import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

// The only production target. A leaked workflow cannot point the app token elsewhere.
export const target = Object.freeze({
  captain: 'https://captain.nhannotes.id.vn', app: 'server',
  publicUrl: 'https://server.nhannotes.id.vn', frontendUrl: 'https://nhannotes.id.vn',
});
export const imagePattern = /^ghcr\.io\/nntn32\/notes-app@sha256:[a-f0-9]{64}$/;

const retryable = message => Object.assign(new Error(message), { retryable: true });

// Public contracts. `revision` must be served by /actuator/info, which proves the new container answers.
export async function smoke({ revision }, { fetcher = fetch } = {}) {
  const get = async (origin, path, expected) => {
    let response;
    try {
      response = await fetcher(new URL(path, origin), {
        redirect: 'error', signal: AbortSignal.timeout(15_000),
        headers: { 'User-Agent': 'NotesWeb-Actions-Smoke/1.0' },
      });
    } catch { throw retryable('Public smoke transport failed'); }
    if ([502, 503, 504, 520, 521, 522, 523, 524].includes(response.status)) {
      await response.body?.cancel();
      throw retryable('Public endpoint is not ready');
    }
    assert.ok(response.status === expected, `Public smoke ${path}: unexpected HTTP status`);
    return response;
  };
  const info = await (await get(target.publicUrl, '/actuator/info', 200)).json();
  if (revision && info.revision !== revision) throw retryable('Old revision still serving');
  const health = await get(target.publicUrl, '/actuator/health/readiness', 200);
  assert.ok((await health.json()).status === 'UP', 'Readiness is not UP');
  for (const origin of [target.publicUrl, target.frontendUrl]) {
    const result = await get(origin, '/api/auth/login/result/__be_cd_health__', 202);
    assert.ok((await result.json()).status === 'PENDING', 'Auth polling contract changed');
  }
  await (await get(target.publicUrl, '/notes/listNotes', 401)).body?.cancel();
  return info.revision;
}

export async function deploy(config, {
  fetcher = fetch, pause = delay, smokeCheck = options => smoke(options, { fetcher }),
  checkpoint = () => {}, attempts = 60, observationSamples = 12,
} = {}) {
  const { appToken, image, revision, previousImage } = config;
  assert.ok(appToken, 'Missing CapRover app token');
  assert.match(image, imagePattern, 'Deploy requires an immutable backend digest');
  assert.match(revision, /^[a-f0-9]{40}$/, 'Invalid revision');
  assert.ok(!previousImage || imagePattern.test(previousImage), 'Previous image must be a pinned digest');

  // App tokens can only start a deploy of this one app; detached so Cloudflare's 100 s limit never cuts it.
  const startDeploy = async imageName => {
    let response;
    try {
      response = await fetcher(new URL(`/api/v2/user/apps/appData/${target.app}?detached=1`, target.captain), {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30_000),
        // Cloudflare 403s some default client user agents (e.g. Python-urllib); send a fixed one.
        headers: { 'x-captain-app-token': appToken, 'x-namespace': 'captain', 'Content-Type': 'application/json',
          'User-Agent': 'NotesWeb-Actions-Deploy/1.0' },
        body: JSON.stringify({ captainDefinitionContent: JSON.stringify({ schemaVersion: 2, imageName }) }),
      });
    } catch { throw new Error('CapRover deploy request failed'); }
    assert.ok(response.ok, `CapRover deploy HTTP ${response.status}`);
    let status;
    try { status = (await response.json()).status; } catch { throw new Error('Invalid CapRover response'); }
    // 100 OK / 101 deploy started; anything else (bad token, unknown app ...) is a rejection.
    assert.ok([100, 101].includes(status), `CapRover deploy status ${status}`);
  };
  const waitFor = async expectedRevision => {
    for (let i = 0; i < attempts; i++) {
      try {
        const served = await smokeCheck({ revision: expectedRevision });
        for (let sample = 0; sample < observationSamples; sample++) {
          await pause(10_000);
          await smokeCheck({ revision: expectedRevision ?? served });
        }
        return served;
      } catch (error) {
        if (!error.retryable) throw error;
        await pause(10_000);
      }
    }
    throw new Error('Deployment health gate timed out');
  };

  const record = { revision, image, previousImage: previousImage ?? null, app: target.app, status: 'starting' };
  checkpoint(record);
  try {
    await startDeploy(image);
    record.status = 'deploying'; checkpoint(record);
    await waitFor(revision);
    record.status = 'passed'; record.observedSeconds = observationSamples * 10;
    checkpoint(record);
    return record;
  } catch (error) {
    record.status = 'failed';
    if (/^CapRover deploy (HTTP \d{3}|status -?\d+|request failed)$/.test(error?.message)) record.failure = error.message;
    record.rollback = previousImage ? 'running' : 'no_previous_release';
    checkpoint(record);
    if (previousImage) {
      try {
        await startDeploy(previousImage);
        // Any revision but the failed one, passing every public contract, counts as recovered.
        const served = await waitFor(undefined);
        record.rollback = served === revision ? 'manual_required' : 'passed';
      } catch { record.rollback = 'manual_required'; }
    }
    checkpoint(record);
    throw new Error(`Deployment failed; rollback ${record.rollback}. See deployment.json.`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const config = { appToken: process.env.CAPROVER_APP_TOKEN, image: process.env.RELEASE_IMAGE,
    revision: process.env.GITHUB_SHA, previousImage: process.env.PREVIOUS_IMAGE || undefined };
  try {
    await deploy(config, { checkpoint: value => writeFileSync('deployment.json', JSON.stringify(value, null, 2)) });
    console.log(`Deployed ${config.image}: revision, readiness and public contracts held for 120 s`);
  } catch {
    // Never print server bodies, tokens, environment values or low-level transport errors.
    console.error('Backend deploy failed. Inspect the sanitized deployment.json report before retrying.');
    process.exitCode = 1;
  }
}
