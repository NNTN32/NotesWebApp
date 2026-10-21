import test from 'node:test';
import assert from 'node:assert/strict';
import { deploy, smoke, target } from './deploy.mjs';

const image = `ghcr.io/nntn32/notes-app@sha256:${'b'.repeat(64)}`;
const previousImage = `ghcr.io/nntn32/notes-app@sha256:${'a'.repeat(64)}`;
const revision = 'c'.repeat(40);
const config = { appToken: 'test-only-token', image, revision, previousImage };
const retry = () => Object.assign(new Error('not yet'), { retryable: true });

function fixture({ status = 101, httpStatus = 200, smokes = [] } = {}) {
  const posts = [], records = [];
  let probe = 0;
  const deps = {
    attempts: 3, observationSamples: 2, pause: async () => {},
    checkpoint: r => records.push(structuredClone(r)),
    fetcher: async (url, request) => {
      posts.push({ url: String(url), headers: request.headers, body: JSON.parse(request.body), redirect: request.redirect });
      return { ok: httpStatus < 400, status: httpStatus, json: async () => ({ status }) };
    },
    smokeCheck: async ({ revision: expected }) => {
      const step = smokes[probe++] ?? 'ok';
      if (step === 'retry') throw retry();
      if (step === 'fail') throw new Error('secret-body-must-not-leak');
      return expected ?? 'd'.repeat(40);
    },
  };
  return { deps, posts, records };
}
const deployedImage = post => JSON.parse(post.body.captainDefinitionContent).imageName;

test('deploys the exact digest with the app token to the fixed app, then observes', async () => {
  const f = fixture({ smokes: ['retry', 'ok', 'ok', 'ok'] });
  const result = await deploy(config, f.deps);
  assert.equal(result.status, 'passed');
  assert.equal(f.posts.length, 1);
  assert.equal(f.posts[0].url, `${target.captain}/api/v2/user/apps/appData/server?detached=1`);
  assert.equal(f.posts[0].headers['x-captain-app-token'], 'test-only-token');
  assert.equal(f.posts[0].redirect, 'error');
  assert.equal(deployedImage(f.posts[0]), image);
  assert.ok(!JSON.stringify(f.records).includes('test-only-token'));
});

test('rejects mutable or foreign images before any request', async () => {
  for (const bad of ['ghcr.io/nntn32/notes-app:latest', `nntn/notes-app@sha256:${'b'.repeat(64)}`,
    `ghcr.io/evil/notes-app@sha256:${'b'.repeat(64)}`]) {
    const f = fixture();
    await assert.rejects(deploy({ ...config, image: bad }, f.deps));
    assert.equal(f.posts.length, 0);
  }
  const f = fixture();
  await assert.rejects(deploy({ ...config, appToken: '' }, f.deps));
  assert.equal(f.posts.length, 0);
});

test('a failed smoke rolls back to the previous digest and stays failed', async () => {
  const f = fixture({ smokes: ['ok', 'fail', 'ok', 'ok', 'ok'] });
  await assert.rejects(deploy(config, f.deps), /rollback passed/);
  assert.deepEqual(f.posts.map(deployedImage), [image, previousImage]);
  const last = f.records.at(-1);
  assert.equal(last.status, 'failed');
  assert.equal(last.rollback, 'passed');
  assert.ok(!JSON.stringify(f.records).includes('secret-body'));
});

test('a revision that never appears times out and rolls back', async () => {
  const f = fixture({ smokes: ['retry', 'retry', 'retry', 'ok', 'ok', 'ok'] });
  await assert.rejects(deploy(config, f.deps), /rollback passed/);
  assert.equal(f.posts.length, 2);
});

test('rejected deploy records only the status and still restores the previous digest', async () => {
  const f = fixture({ status: 1106 });
  await assert.rejects(deploy(config, f.deps), /rollback manual_required/);
  assert.equal(f.records.at(-1).failure, 'CapRover deploy status 1106');
});

test('first release has nothing to roll back to', async () => {
  const f = fixture({ httpStatus: 524 });
  await assert.rejects(deploy({ ...config, previousImage: undefined }, f.deps), /no_previous_release/);
  assert.equal(f.posts.length, 1);
  assert.equal(f.records.at(-1).failure, 'CapRover deploy HTTP 524');
});

test('public smoke requires the new revision before checking contracts', async () => {
  const responses = {
    '/actuator/info': [200, { revision }], '/actuator/health/readiness': [200, { status: 'UP' }],
    '/api/auth/login/result/__be_cd_health__': [202, { status: 'PENDING' }], '/notes/listNotes': [401, {}],
  };
  const seen = [];
  const fetcher = async url => {
    seen.push(String(url));
    const [status, body] = responses[url.pathname];
    return { status, json: async () => body, body: { cancel: async () => {} } };
  };
  assert.equal(await smoke({ revision }, { fetcher }), revision);
  assert.ok(seen.includes(`${target.frontendUrl}/api/auth/login/result/__be_cd_health__`));
  await assert.rejects(smoke({ revision: 'e'.repeat(40) }, { fetcher }), e => e.retryable);
  responses['/notes/listNotes'] = [200, {}];
  await assert.rejects(smoke({ revision }, { fetcher }), /listNotes/);
});
