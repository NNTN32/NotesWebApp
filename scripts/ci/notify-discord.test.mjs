import test from 'node:test';
import assert from 'node:assert/strict';
import { message, notify, overall } from './notify-discord.mjs';

const hook = 'https://discord.com/api/webhooks/1/secret-token';
const env = {
  DISCORD_WEBHOOK: hook, GITHUB_REPOSITORY: 'NNTN32/NotesWebApp', GITHUB_WORKFLOW: 'CI', GITHUB_EVENT_NAME: 'push',
  REF: 'main', HEAD_SHA: 'a'.repeat(40), GITHUB_ACTOR: 'nntn', TITLE: 'Fix @everyone bug\nbody',
  GITHUB_SERVER_URL: 'https://github.com', GITHUB_RUN_ID: '42',
  RESULTS: JSON.stringify({ build: { result: 'success' }, deploy: { result: 'failure' }, publish: { result: 'skipped' } }),
};

test('any failed job makes the run red; skipped jobs do not', () => {
  assert.equal(overall({ a: 'success', b: 'skipped' }), 'success');
  assert.equal(overall({ a: 'success', b: 'cancelled' }), 'cancelled');
  assert.equal(overall({ a: 'cancelled', b: 'failure' }), 'failure');
});

test('posts one sanitized message and never echoes the webhook', async () => {
  const calls = [];
  const out = await notify(env, async (url, request) => { calls.push({ url: String(url), request }); return { ok: true, status: 204 }; });
  assert.equal(out, 'sent: failure');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, hook);
  assert.equal(calls[0].request.redirect, 'error');
  const body = JSON.parse(calls[0].request.body);
  assert.deepEqual(body.allowed_mentions, { parse: [] });
  assert.match(body.content, /^🔴 \*\*FAILURE · NNTN32\/NotesWebApp\*\*/);
  assert.match(body.content, /`aaaaaaa`/);
  assert.match(body.content, /❌ deploy/);
  assert.doesNotMatch(body.content, /body|secret-token/);
});

test('missing secret skips quietly; foreign endpoints and HTTP errors fail without leaking', async () => {
  assert.match(await notify({ ...env, DISCORD_WEBHOOK: '' }), /^skipped/);
  const never = async () => { throw new Error('must not be called'); };
  await assert.rejects(notify({ ...env, DISCORD_WEBHOOK: 'https://evil.example/api/webhooks/1/x' }, never), /^Error: Unexpected webhook endpoint$/);
  await assert.rejects(notify(env, async () => ({ ok: false, status: 404 })), e => e.message === 'Discord webhook HTTP 404');
  await assert.rejects(notify(env, async () => { throw new Error(hook); }), e => !e.message.includes('secret-token'));
});

test('messages stay under the Discord limit', () => {
  const body = message({ repo: 'r', workflow: 'w', event: 'e', ref: 'x', sha: 'b'.repeat(40), actor: 'a', title: 'x'.repeat(5000), runUrl: 'u', results: { a: 'success' } });
  assert.ok(body.content.length <= 1900);
});
