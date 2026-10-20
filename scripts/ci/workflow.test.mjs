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
  assert.match(deployment, /RELEASE_IMAGE: nntn\/notes-app@\$\{\{ needs.publish.outputs.digest \}\}/);
  assert.doesNotMatch(workflow.slice(0, workflow.indexOf('\n  deploy:')), /DOKPLOY_API_KEY/);
  assert.doesNotMatch(workflow, /DOKPLOY_WEBHOOK|secrets\.dokploy_URL/);
  assert.ok(deployment.indexOf('node scripts/ci/deploy.mjs') < deployment.indexOf('Promote only the verified digest'));
  assert.doesNotMatch(deployment, /continue-on-error/);
});
test('publication requires application and CD regression tests plus merged main policy', () => {
  assert.match(workflow, /needs: \[build-and-test, deployment-tests, release-policy\]/);
  assert.match(workflow, /needs.deployment-tests.result == 'success'/);
  assert.match(workflow, /needs.release-policy.outputs.merged == 'true'/);
  assert.match(workflow, /node --test scripts\/ci\/\*.test.mjs/);
  assert.match(workflow, /localhost:8081\/actuator\/health\/readiness/);
});
