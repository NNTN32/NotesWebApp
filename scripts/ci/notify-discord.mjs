// Post one CI result (success, failure or cancelled) to a Discord webhook.
// Never prints the webhook URL or the response body.
import { pathToFileURL } from 'node:url';

const JOB_ICON = { success: '✅', failure: '❌', cancelled: '⏹️', skipped: '⏭️' };
const RUN_ICON = { success: '🟢', failure: '🔴', cancelled: '⚪' };

export function overall(results) {
  const values = Object.values(results);
  if (values.includes('failure')) return 'failure';
  if (values.includes('cancelled')) return 'cancelled';
  return 'success';
}

export function message({ repo, workflow, event, ref, sha, actor, title, runUrl, results }) {
  const status = overall(results);
  const jobs = Object.entries(results).map(([job, result]) => `${JOB_ICON[result] ?? '•'} ${job}`).join('  ');
  const firstLine = String(title ?? '').split('\n')[0].slice(0, 200);
  const content = [
    `${RUN_ICON[status]} **${status.toUpperCase()} · ${repo}** · ${workflow}`,
    `${event} \`${ref}\` · \`${String(sha).slice(0, 7)}\` · ${actor}`,
    firstLine,
    jobs,
    `<${runUrl}>`,
  ].filter(Boolean).join('\n');
  // Titles are untrusted (PR/commit text): never let them ping anyone.
  return { content: content.slice(0, 1900), allowed_mentions: { parse: [] } };
}

export function webhookUrl(raw) {
  const url = new URL(raw);
  if (url.protocol !== 'https:' || url.hostname !== 'discord.com' || !url.pathname.startsWith('/api/webhooks/') || url.search || url.hash) {
    throw new Error('Unexpected webhook endpoint');
  }
  return url;
}

export async function notify(env, fetcher = fetch) {
  if (!env.DISCORD_WEBHOOK) return 'skipped: no webhook secret for this run (fork or Dependabot)';
  const needs = JSON.parse(env.RESULTS || '{}');
  const results = Object.fromEntries(Object.entries(needs).map(([job, value]) => [job, value.result]));
  const body = message({
    repo: env.GITHUB_REPOSITORY, workflow: env.GITHUB_WORKFLOW, event: env.GITHUB_EVENT_NAME,
    ref: env.REF, sha: env.HEAD_SHA, actor: env.GITHUB_ACTOR, title: env.TITLE, results,
    runUrl: `${env.GITHUB_SERVER_URL}/${env.GITHUB_REPOSITORY}/actions/runs/${env.GITHUB_RUN_ID}`,
  });
  let response;
  try {
    response = await fetcher(webhookUrl(env.DISCORD_WEBHOOK), {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15_000),
      headers: { 'Content-Type': 'application/json', 'User-Agent': 'NotesWeb-CI-Notify/1.0' },
      body: JSON.stringify(body),
    });
  } catch (error) {
    throw new Error(error?.message === 'Unexpected webhook endpoint' ? error.message : 'Discord request failed');
  }
  if (!response.ok) throw new Error(`Discord webhook HTTP ${response.status}`);
  return `sent: ${overall(results)}`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(await notify(process.env)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
