import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

export const target = Object.freeze({
  endpoint: 'https://dokploy.nhannotes.id.vn',
  applicationId: '_mkgSpRBPfNpAjBLu_zDB',
  appName: 'nnnoteswebapp-notesserver-oubfhi',
  hostname: 'ip-172-26-24-177', address: '172.26.24.177',
  publicUrl: 'https://server.nhannotes.id.vn', frontendUrl: 'https://nhannotes.id.vn',
});
const imagePattern = /^nntn\/notes-app@sha256:[a-f0-9]{64}$/;
const normalizeImage = image => image?.replace(/^(docker\.io|index\.docker\.io)\//, '');
const preservedFields = ['env', 'args', 'command', 'networkSwarm', 'networkIds', 'detachDokployNetwork',
  'memoryLimit', 'cpuLimit', 'memoryReservation', 'cpuReservation', 'replicas', 'modeSwarm',
  'updateConfigSwarm', 'rollbackConfigSwarm', 'serverId', 'mounts'];

export async function smoke({ readiness }, { fetcher = fetch } = {}) {
  const get = async (origin, path, expected) => {
    let response;
    try {
      response = await fetcher(new URL(path, origin), {
        redirect: 'error', signal: AbortSignal.timeout(15_000),
        headers: { 'User-Agent': 'NotesWeb-Actions-Smoke/1.0' },
      });
    } catch {
      throw Object.assign(new Error('Public smoke transport failed'), { retryable: true });
    }
    if ([502, 503, 504].includes(response.status)) {
      await response.body?.cancel();
      throw Object.assign(new Error('Public endpoint is not ready'), { retryable: true });
    }
    assert.ok(response.status === expected, `Public smoke ${path}: unexpected HTTP status`);
    return response;
  };
  if (readiness) {
    const health = await get(target.publicUrl, '/actuator/health/readiness', 200);
    assert.ok((await health.json()).status === 'UP', 'Readiness is not UP');
  }
  for (const origin of [target.publicUrl, target.frontendUrl]) {
    const result = await get(origin, '/api/auth/login/result/__be_cd_health__', 202);
    assert.ok((await result.json()).status === 'PENDING', 'Auth polling contract changed');
  }
  await (await get(target.publicUrl, '/notes/listNotes', 401)).body?.cancel();
}

export async function deploy(config, {
  fetcher = fetch, pause = delay, smokeCheck = options => smoke(options, { fetcher }),
  checkpoint = () => {}, attempts = 120, observationSamples = 12,
} = {}) {
  const { apiKey, image, revision, releaseId } = config;
  assert.ok(apiKey, 'Missing Dokploy API key');
  assert.ok(!config.endpoint || config.endpoint === target.endpoint, 'Unexpected Dokploy endpoint');
  assert.ok(!config.applicationId || config.applicationId === target.applicationId, 'Unexpected application');
  assert.match(image, imagePattern, 'Deploy requires an immutable backend digest');
  assert.match(revision, /^[a-f0-9]{40}$/, 'Invalid revision');
  assert.match(releaseId, /^BE-[A-Za-z0-9_-]+$/, 'Invalid release ID');
  const query = `applicationId=${target.applicationId}`;
  let uncertainMutation = false;
  const api = async (route, body) => {
    let response;
    try {
      response = await fetcher(new URL(`/api/${route}`, target.endpoint), {
        method: body ? 'POST' : 'GET', redirect: 'error', signal: AbortSignal.timeout(30_000),
        headers: { 'x-api-key': apiKey, 'Content-Type': 'application/json' },
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
    } catch {
      if (body) uncertainMutation = true;
      throw new Error(`Dokploy ${route.split('?')[0]} request failed`);
    }
    if (body && response.status >= 500) uncertainMutation = true;
    assert.ok(response.ok, `Dokploy ${route.split('?')[0]} HTTP ${response.status}`);
    if (body) { await response.body?.cancel(); return; }
    try { return await response.json(); }
    catch { throw new Error('Invalid Dokploy JSON response'); }
  };
  const assertTarget = async () => {
    const nodes = await api('cluster.getNodes');
    assert.ok(Array.isArray(nodes) && nodes.length === 1
      && nodes[0].Description?.Hostname === target.hostname
      && nodes[0].Status?.Addr === target.address && nodes[0].Status?.State === 'ready'
      && nodes[0].Spec?.Availability === 'active', 'Dokploy is not the expected Lightsail node');
  };
  const app = () => api(`application.one?${query}`);
  const deployments = async () => {
    const rows = await api(`deployment.all?${query}`);
    assert.ok(Array.isArray(rows), 'Invalid deployment list');
    return rows;
  };
  const service = () => api(`docker.getConfig?containerId=${target.appName}`);
  const runtime = async expectedImage => {
    const s = await service();
    assert.ok(s.Spec?.Name === target.appName, 'Unexpected Swarm service');
    if (s.UpdateStatus?.State === 'paused' || s.UpdateStatus?.State === 'rollback_paused') {
      throw new Error('Swarm rollout paused');
    }
    if (normalizeImage(s.Spec.TaskTemplate?.ContainerSpec?.Image) !== expectedImage) {
      if (s.UpdateStatus?.State === 'rollback_completed') throw new Error('Swarm reverted the candidate');
      return null;
    }
    if (s.Spec.Mode?.Replicated?.Replicas !== 1
      || !['completed', 'rollback_completed', undefined].includes(s.UpdateStatus?.State)) return null;
    const rows = await api('docker.getContainers');
    assert.ok(Array.isArray(rows), 'Invalid container list');
    const running = rows.filter(c => c.name?.startsWith(`${target.appName}.`) && c.state === 'running');
    if (running.length !== 1) return null;
    const c = await api(`docker.getConfig?containerId=${encodeURIComponent(running[0].containerId)}`);
    if (!c.State?.Running || c.State.Health?.Status !== 'healthy'
      || normalizeImage(c.Config?.Image) !== expectedImage) return null;
    return c;
  };
  await assertTarget();
  const previous = await app();
  assert.ok(previous.applicationId === target.applicationId && previous.appName === target.appName
    && previous.sourceType === 'docker' && !previous.serverId && previous.autoDeploy === false,
  'Unexpected application or automatic deploy enabled');
  assert.match(previous.dockerImage, imagePattern, 'Previous image must also be pinned');
  const previousRuntime = await runtime(previous.dockerImage);
  assert.ok(previousRuntime, 'Previous release is not healthy; stop before deployment');
  const existing = await deployments();
  assert.ok(!existing.some(d => ['queued', 'running'].includes(d.status)), 'Another deployment is active');
  const baseline = new Set(existing.map(d => d.deploymentId));
  const stableSettings = value => JSON.stringify(preservedFields.map(k => value[k] ?? null));
  const assertSettings = current => assert.ok(stableSettings(current) === stableSettings(previous),
    'Runtime settings changed concurrently; manual reconciliation required');
  const record = { revision, image, previousImage: previous.dockerImage,
    applicationId: target.applicationId, releaseId, target: target.hostname, status: 'starting' };
  checkpoint(record);
  let changed = false;
  const assertOwnership = async () => {
    await assertTarget();
    const current = await app();
    assertSettings(current);
    assert.ok(current.dockerImage === image, 'Image changed concurrently; do not overwrite another release');
    const rows = await deployments();
    assert.ok(!rows.some(d => !baseline.has(d.deploymentId) && d.title !== releaseId),
      'Another deployment intervened; manual rollback required');
    assert.ok(!rows.some(d => ['queued', 'running'].includes(d.status)), 'Deployment still active; do not overlap rollback');
    const s = await service();
    assert.ok(!['updating', 'rollback_started'].includes(s.UpdateStatus?.State), 'Swarm still updating; manual rollback required');
  };
  const waitForRelease = async (title, expectedImage, readiness, expectedRevision) => {
    for (let i = 0; i < attempts; i++) {
      const rows = await deployments();
      assert.ok(!rows.some(d => !baseline.has(d.deploymentId) && ![releaseId, `${releaseId}-rollback`].includes(d.title)),
        'Another deployment intervened');
      const current = rows.find(d => d.title === title && !baseline.has(d.deploymentId));
      if (current) {
        if (title === releaseId) record.deploymentId = current.deploymentId;
        else record.rollbackDeploymentId = current.deploymentId;
        checkpoint(record);
        assert.ok(!['error', 'failed', 'cancelled'].includes(current.status), 'Dokploy deployment failed');
        if (current.status === 'done') {
          const c = await runtime(expectedImage);
          if (c) {
            if (expectedRevision) assert.ok(c.Config.Labels?.['org.opencontainers.image.revision'] === expectedRevision,
              'Running image revision does not match this release');
            try { await smokeCheck({ readiness }); }
            catch (error) {
              if (!error.retryable) throw error;
              await pause(5_000);
              continue;
            }
            for (let sample = 0; sample < observationSamples; sample++) {
              await pause(10_000);
              assert.ok(await runtime(expectedImage), 'Runtime health/image changed during observation');
              await smokeCheck({ readiness });
            }
            const finalRows = await deployments();
            assert.ok(!finalRows.some(d => !baseline.has(d.deploymentId)
              && ![releaseId, `${releaseId}-rollback`].includes(d.title)), 'Another deployment intervened');
            const desired = await app();
            assertSettings(desired);
            assert.ok(desired.dockerImage === expectedImage && desired.autoDeploy === false,
              'Desired image changed concurrently');
            return;
          }
        }
      }
      await pause(5_000);
    }
    throw new Error('Deployment health gate timed out');
  };
  try {
    changed = true;
    // Only the first Actuator release changes the old custom health override; rollback restores it.
    await api('application.update', { applicationId: target.applicationId, sourceType: 'docker',
      dockerImage: image, autoDeploy: false, healthCheckSwarm: null });
    await api('application.deploy', { applicationId: target.applicationId, title: releaseId,
      description: `BE ${revision}` });
    await waitForRelease(releaseId, image, true, revision);
    record.status = 'passed'; record.observedSeconds = observationSamples * 10;
    checkpoint(record);
    return record;
  } catch {
    record.status = 'failed';
    record.rollback = 'not_started';
    if (changed) {
      try {
        assert.ok(!uncertainMutation, 'Ambiguous mutation; reconcile before another write');
        await assertOwnership();
        record.rollback = 'running'; checkpoint(record);
        await api('application.update', { applicationId: target.applicationId, sourceType: 'docker',
          dockerImage: previous.dockerImage, autoDeploy: false,
          healthCheckSwarm: previous.healthCheckSwarm ?? null });
        await api('application.deploy', { applicationId: target.applicationId, title: `${releaseId}-rollback`,
          description: 'Restore previous digest and health override; retain current data and runtime settings' });
        const readiness = JSON.stringify(previousRuntime.Config.Healthcheck?.Test ?? []).includes('/actuator/health/liveness');
        await waitForRelease(`${releaseId}-rollback`, previous.dockerImage, readiness);
        record.rollback = 'passed';
      } catch { record.rollback = 'manual_required'; }
    }
    checkpoint(record);
    throw new Error(`Deployment failed; rollback ${record.rollback}. See deployment.json; no mutation is retried.`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const config = { apiKey: process.env.DOKPLOY_API_KEY, endpoint: process.env.DOKPLOY_URL,
    applicationId: process.env.DOKPLOY_APPLICATION_ID, image: process.env.RELEASE_IMAGE,
    revision: process.env.GITHUB_SHA, releaseId: `BE-${process.env.GITHUB_RUN_ID}-${process.env.GITHUB_RUN_ATTEMPT}` };
  try {
    const result = await deploy(config, { checkpoint: value => writeFileSync('deployment.json', JSON.stringify(value, null, 2)) });
    console.log(`Deployment ${result.deploymentId}: digest/runtime/public health gates passed`);
  } catch {
    // Never print server bodies, API credentials, environment values or low-level transport errors.
    console.error('Backend deploy failed. Inspect the sanitized deployment.json report before retrying.');
    process.exitCode = 1;
  }
}
