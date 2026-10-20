import test from 'node:test';
import assert from 'node:assert/strict';
import { deploy, smoke, target } from './deploy.mjs';

const oldImage = `nntn/notes-app@sha256:${'a'.repeat(64)}`;
const image = `nntn/notes-app@sha256:${'b'.repeat(64)}`;
const config = { apiKey: 'test-only-secret', image, revision: 'c'.repeat(40), releaseId: 'BE-123-1' };
const oldHealth = { Test: ['CMD-SHELL', 'legacy-login-health'], Interval: 15_000_000_000 };

function fixture(options = {}) {
  const calls = [], records = [], probes = [];
  let previous = { ...target, sourceType: 'docker', dockerImage: oldImage, autoDeploy: false,
    serverId: null, replicas: 1, env: 'PASSWORD=must-not-appear-in-reports',
    networkSwarm: [{ Target: 'app' }, { Target: 'private' }], healthCheckSwarm: oldHealth };
  const baseline = [{ deploymentId: 'old', title: 'old release', status: options.busy ? 'running' : 'done' }];
  let desired = structuredClone(previous), runningImage = oldImage, stage = 'old', smokeCalls = 0;
  let publicNode = options.wrongNode ? 'orbstack' : target.hostname;
  const deps = { attempts: 3, observationSamples: 2, pause: async () => {},
    checkpoint: record => records.push(structuredClone(record)),
    smokeCheck: async value => {
      probes.push(value); smokeCalls++;
      if (options.foreignRelease && smokeCalls === 1) {
        desired.dockerImage = `nntn/notes-app@sha256:${'f'.repeat(64)}`;
        throw new Error('public smoke failed');
      }
      if (options.changedSettings && smokeCalls === 1) {
        desired.memoryLimit = 42;
        throw new Error('public smoke failed');
      }
      if (options.interveningDeployment && smokeCalls === 1) {
        baseline.push({ deploymentId: 'manual', title: 'operator release', status: 'done' });
        throw new Error('public smoke failed');
      }
      if (options.retryReady && smokeCalls === 1) throw Object.assign(new Error('warming'), { retryable: true });
      if (options.smokeFailure && (stage === 'candidate' || options.rollbackFailure)) throw new Error('secret-body-must-not-leak');
    },
    fetcher: async (url, request) => {
      const route = url.pathname.split('/').at(-1), body = request.body && JSON.parse(request.body);
      calls.push({ route, body, url: String(url), redirect: request.redirect });
      let data;
      if (route === 'cluster.getNodes') data = [{ Description: { Hostname: publicNode },
        Status: { Addr: target.address, State: 'ready' }, Spec: { Availability: 'active' } }];
      if (route === 'application.one') data = structuredClone(desired);
      if (route === 'deployment.all') data = structuredClone(baseline);
      if (route === 'application.update') {
        desired = { ...desired, ...body };
        if (options.uncertainUpdate) throw new Error(config.apiKey);
      }
      if (route === 'application.deploy') {
        stage = body.title.endsWith('-rollback') ? 'rollback' : 'candidate';
        runningImage = desired.dockerImage;
        baseline.push({ deploymentId: `id-${stage}`, title: body.title,
          status: stage === 'candidate' ? options.deploymentStatus ?? 'done' : 'done' });
        if (options.autoRollback && stage === 'candidate') runningImage = oldImage;
        if (options.uncertainDeploy) throw new Error(config.apiKey);
      }
      if (route === 'docker.getContainers') data = [
        { name: `${target.appName}.1.task`, state: 'running', containerId: 'container' },
      ];
      if (route === 'docker.getConfig') {
        if (url.searchParams.get('containerId') === target.appName) {
          data = { Spec: { Name: target.appName, Mode: { Replicated: { Replicas: 1 } },
            TaskTemplate: { ContainerSpec: { Image: runningImage } } },
          UpdateStatus: { State: options.autoRollback && stage === 'candidate' ? 'rollback_completed' : 'completed' } };
        } else {
          data = { State: { Running: true, Health: { Status: options.unhealthy && stage === 'candidate' ? 'unhealthy' : 'healthy' } },
            Config: { Image: runningImage, Labels: { 'org.opencontainers.image.revision': options.wrongRevision ? 'd'.repeat(40) : config.revision },
              Healthcheck: { Test: stage === 'candidate' || options.previousReadiness
                ? ['CMD-SHELL', '/actuator/health/liveness'] : ['CMD-SHELL', 'legacy-login-health'] } } };
        }
      }
      if (route === 'application.update' && options.rejectUpdate) {
        desired = structuredClone(previous);
        return { ok: false, status: options.rejectUpdate, body: { cancel: async () => {} } };
      }
      return { ok: true, status: 200, body: { cancel: async () => {} }, json: async () => {
        assert.ok(!body, 'Mutations must not parse an empty JSON body'); return data;
      } };
    },
  };
  return { deps, calls, records, probes, last: () => records.at(-1), desired: () => desired,
    moveNode: () => { publicNode = 'wrong-node'; } };
}
const writes = f => f.calls.filter(c => c.body);

test('pins digest only on Lightsail, verifies revision and observes public smoke before success', async () => {
  const f = fixture();
  const result = await deploy(config, f.deps);
  assert.equal(result.status, 'passed');
  assert.equal(result.previousImage, oldImage);
  assert.equal(result.deploymentId, 'id-candidate');
  assert.equal(result.observedSeconds, 20);
  assert.equal(f.probes.length, 3);
  assert.deepEqual(writes(f)[0].body, { applicationId: target.applicationId,
    sourceType: 'docker', dockerImage: image, autoDeploy: false, healthCheckSwarm: null });
  assert.ok(f.calls.every(c => c.url.startsWith(target.endpoint) && c.redirect === 'error'));
  assert.ok(!JSON.stringify(f.records).includes('must-not-appear'));
});
for (const bad of [
  { image: 'nntn/notes-app:latest' }, { image: `other/app@sha256:${'a'.repeat(64)}` },
  { endpoint: 'https://orbstack.example.test' }, { applicationId: 'wrong' }, { revision: 'bad' },
]) test(`rejects invalid configuration before sending credentials: ${Object.keys(bad)[0]}`, async () => {
  const f = fixture(); await assert.rejects(deploy({ ...config, ...bad }, f.deps));
  assert.equal(f.calls.length, 0);
});
test('wrong node rejects before any mutation', async () => {
  const f = fixture({ wrongNode: true });
  await assert.rejects(deploy(config, f.deps), /expected Lightsail/);
  assert.equal(writes(f).length, 0);
});
test('existing active deployment prevents a second deployment', async () => {
  const f = fixture({ busy: true });
  await assert.rejects(deploy(config, f.deps), /active/);
  assert.equal(writes(f).length, 0);
});
for (const options of [
  { deploymentStatus: 'error' }, { unhealthy: true }, { smokeFailure: true },
  { wrongRevision: true }, { autoRollback: true },
]) test(`failed gate restores previous digest/health without touching data or runtime settings: ${Object.keys(options)[0]}`, async () => {
  const f = fixture(options);
  await assert.rejects(deploy(config, f.deps), /rollback passed/);
  assert.equal(f.last().status, 'failed');
  assert.equal(f.last().rollback, 'passed');
  assert.equal(f.desired().dockerImage, oldImage);
  assert.deepEqual(f.desired().healthCheckSwarm, oldHealth);
  assert.equal(f.desired().env, 'PASSWORD=must-not-appear-in-reports');
  assert.ok(writes(f).every(c => !('env' in c.body) && !('networkSwarm' in c.body)));
  assert.ok(!JSON.stringify(f.records).includes('secret-body'));
  assert.equal(f.probes.at(-1)?.readiness, false); // First rollback supports the pre-Actuator image.
});
test('startup readiness may warm up, but observation remains bounded', async () => {
  const f = fixture({ retryReady: true });
  assert.equal((await deploy(config, f.deps)).status, 'passed');
  assert.equal(f.probes.length, 4);
});
test('subsequent rollback still requires readiness for a prior Actuator image', async () => {
  const f = fixture({ previousReadiness: true, wrongRevision: true });
  await assert.rejects(deploy(config, f.deps), /rollback passed/);
  assert.equal(f.probes.at(-1).readiness, true);
});
for (const options of [
  { foreignRelease: true }, { changedSettings: true }, { interveningDeployment: true },
  { deploymentStatus: 'running' }, { uncertainDeploy: true }, { uncertainUpdate: true },
]) test(`does not overwrite a concurrent/ambiguous deployment: ${Object.keys(options)[0]}`, async () => {
  const f = fixture(options);
  await assert.rejects(deploy(config, f.deps), /manual_required/);
  assert.equal(f.last().rollback, 'manual_required');
  assert.equal(writes(f).filter(c => c.route === 'application.update').length, 1);
  assert.ok(writes(f).filter(c => c.route === 'application.deploy').length <= 1);
  assert.ok(!JSON.stringify(f.records).includes(config.apiKey));
});
test('a rejected image update changes nothing and records the failing route', async () => {
  const f = fixture({ rejectUpdate: 400 });
  await assert.rejects(deploy(config, f.deps), /rollback not_started/);
  assert.equal(f.last().rollback, 'not_started');
  assert.equal(f.last().failure, 'Dokploy application.update HTTP 400');
  assert.equal(writes(f).length, 1);
});
test('failed rollback is explicit and never turns the failed release green', async () => {
  const f = fixture({ smokeFailure: true, rollbackFailure: true });
  await assert.rejects(deploy(config, f.deps), /manual_required/);
  assert.equal(f.last().status, 'failed');
});
test('smoke uses only read-only requests, verifies dependencies and preserves anonymous denial', async () => {
  const calls = [];
  await smoke({ readiness: true }, { fetcher: async (url, options) => {
    calls.push([String(url), options]);
    const health = url.pathname.includes('readiness'), denied = url.pathname.startsWith('/notes');
    return { status: health ? 200 : denied ? 401 : 202, json: async () => ({ status: health ? 'UP' : 'PENDING' }) };
  } });
  assert.equal(calls.length, 4);
  assert.ok(calls.every(([, o]) => !o.method && !o.body && o.redirect === 'error'));
});
test('readiness 503 is retryable and exposes no response body', async () => {
  await assert.rejects(smoke({ readiness: true }, { fetcher: async () => ({
    status: 503, body: { cancel: async () => {} }, json: async () => { throw new Error('private'); },
  }) }), error => error.retryable && !error.message.includes('private'));
});
