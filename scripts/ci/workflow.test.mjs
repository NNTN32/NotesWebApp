import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const workflow = readFileSync(new URL('../../.github/workflows/CI.yml', import.meta.url), 'utf8');
test('production credentials and immutable deployment stay behind the main environment gate', () => {
  const deployment = workflow.slice(workflow.indexOf('\n  deploy:'));
  assert.match(deployment, /needs: publish/);
  assert.match(deployment, /github.event_name == 'push' && github.ref == 'refs\/heads\/main'/);
  assert.match(deployment, /environment: ProductBE/);
  assert.match(deployment, /cancel-in-progress: false/);
  assert.match(deployment, /RELEASE_IMAGE: ghcr.io\/nntn32\/notes-app@\$\{\{ needs.publish.outputs.digest \}\}/);
  assert.doesNotMatch(workflow.slice(0, workflow.indexOf('\n  deploy:')), /CAPROVER_APP_TOKEN/);
  assert.doesNotMatch(workflow, /DOKPLOY|DOCKERHUB_TOKEN|nntn\/notes-app/);
  assert.ok(deployment.indexOf('node scripts/ci/deploy.mjs') < deployment.indexOf('Promote only the verified digest'));
  assert.doesNotMatch(deployment, /continue-on-error/);
});
test('publication requires application and CD regression tests plus merged main policy', () => {
  assert.match(workflow, /needs: \[build-and-test, deployment-tests, release-policy\]/);
  assert.match(workflow, /needs.deployment-tests.result == 'success'/);
  assert.match(workflow, /needs.release-policy.outputs.merged == 'true'/);
  assert.match(workflow, /attempt < 6 && !merged/); // lagging PR association must not turn a merge into a direct push
  assert.match(workflow, /node --test scripts\/ci\/\*.test.mjs/);
  assert.match(workflow, /localhost:8081\/actuator\/health\/readiness/);
});
test('every action is pinned to a full commit SHA and checkouts drop the token', () => {
  const uses = [...workflow.matchAll(/^\s*- uses: (\S+)/gm), ...workflow.matchAll(/^\s+uses: (\S+)/gm)].map(m => m[1]);
  assert.ok(uses.length > 0);
  for (const action of uses) assert.match(action, /@[a-f0-9]{40}$/, action);
  const checkouts = workflow.split('actions/checkout@').length - 1;
  assert.equal(workflow.split('persist-credentials: false').length - 1, checkouts);
});
test('the published digest is smoked on both architectures before it is recorded', () => {
  const publish = workflow.slice(workflow.indexOf('\n  publish:'), workflow.indexOf('\n  deploy:'));
  assert.match(publish, /linux\/amd64 linux\/arm64/);
  assert.ok(publish.indexOf('both architectures') < publish.indexOf('Record immutable release'));
});
test('every run reports its result to Discord without touching production credentials', () => {
  const notify = workflow.slice(workflow.indexOf('\n  notify:'));
  assert.match(notify, /needs: \[deployment-tests, build-and-test, release-policy, publish, deploy\]/);
  assert.match(notify, /if: always\(\)/);
  assert.match(notify, /DISCORD_WEBHOOK: \$\{\{ secrets.DISCORD_CI_WEBHOOK \}\}/);
  assert.doesNotMatch(notify, /CAPROVER_APP_TOKEN|environment:|packages: write/);
  assert.equal(workflow.split('DISCORD_CI_WEBHOOK').length - 1, 1);
});
test('every merged main commit is published and deployed (no skip-deploy marker)', () => {
  assert.doesNotMatch(workflow, /skip deploy/i);
});
