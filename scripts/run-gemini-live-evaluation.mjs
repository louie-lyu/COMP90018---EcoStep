// Opt-in live evaluation: local Firebase login -> authenticated AI Worker -> Gemini.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const modes = ['WALKING', 'CYCLING', 'PUBLIC_TRANSPORT', 'CAR', 'UNKNOWN'];
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = value => typeof value === 'string' && value.trim().length > 0;

// Fixed synthetic facts only. Prompt layout mirrors DefaultMissionPromptBuilder and
// DefaultWeeklyCoach; these host fixtures do not execute the Kotlin builders.
function missionCase(name, alternatives, { distance = '5.0', weather = 'Clear, 20 C', transit = 'No verified public transport option' } = {}) {
  return {
    name, task: 'mission', allowedModes: alternatives.map(([mode]) => mode),
    prompt: `You are EcoStep's mobility coach.

Recommend one lower-carbon transport option using only the verified information below.
Do not invent routes, weather, public transport services, carbon values, or user details.

Journey information:
- Current transport mode: CAR
- Route distance: ${distance} km
- Estimated duration: 15 minutes
- Weather: ${weather}
- Public transport: ${transit}
- Recent transport modes: car, walking

Verified lower-carbon alternatives:
${alternatives.map(([mode, saving]) => `- ${mode}: ${saving} g CO2 saving`).join('\n')}

Rules:
1. Choose exactly one mode from the verified alternatives.
2. Do not calculate or change the carbon-saving value.
3. Keep the explanation practical and concise.
4. Keep the notification message short.
5. Return JSON only, without Markdown or additional text.

Required JSON format:
{
  "recommendedMode": "MODE_FROM_VERIFIED_ALTERNATIVES",
  "explanation": "One or two short sentences",
  "confidence": 0,
  "notificationTitle": "Short title",
  "notificationMessage": "Short message"
}`,
  };
}

function weeklyCase(name, accepted, completed, saving, mode) {
  return {
    name, task: 'weekly',
    prompt: `You are the EcoStep Weekly Coach.
Write one short encouraging weekly insight and one realistic action for next week.

Aggregated weekly information:
- Accepted missions: ${accepted}
- Completed missions: ${completed}
- Completion rate: ${(completed / accepted * 100).toFixed(1)}%
- Recorded estimated carbon saved: ${saving.toFixed(1)} grams
- Most used completed transport mode: ${mode}

Use only the information provided.
Do not invent routes, locations, carbon values or personal information.
If there are no completed missions, encourage one achievable first step.
Keep the response under 80 words.`,
  };
}

export const cases = [
  missionCase('mission_walking_only', [['WALKING', 384]], { distance: '2.0' }),
  missionCase('mission_walking_or_cycling', [['WALKING', 960], ['CYCLING', 960]]),
  missionCase('mission_public_transport_only', [['PUBLIC_TRANSPORT', 1236]], { distance: '12.0', transit: 'Verified service (25 minutes)' }),
  missionCase('mission_rain_with_transit', [['WALKING', 960], ['CYCLING', 960], ['PUBLIC_TRANSPORT', 515]], { weather: 'Rain, 12 C', transit: 'Verified service (20 minutes)' }),
  weeklyCase('weekly_completed', 5, 4, 1920, 'WALKING'),
  // accepted > 0: the production coach skips AI when no missions were accepted.
  weeklyCase('weekly_none_completed', 3, 0, 0, 'NONE'),
];

export function analyseProxyResponse(data, scenario) {
  const result = {
    proxyEnvelopeValid: false, schemaValid: false, businessValid: false,
    accepted: false, validationErrors: [], reply: null,
  };
  if (!isObject(data) || data.task !== scenario.task || !isObject(data.result)) {
    result.validationErrors.push('INVALID_PROXY_ENVELOPE'); return result;
  }
  result.proxyEnvelopeValid = true;
  const parsed = data.result;
  // Matches Kotlin deserialization: ignore unknown fields, require correct types and enum.
  result.schemaValid = isObject(parsed) && (scenario.task === 'weekly'
    ? typeof parsed.insight === 'string' && typeof parsed.action === 'string'
    : modes.includes(parsed.recommendedMode) && typeof parsed.explanation === 'string' &&
      typeof parsed.confidence === 'number' && Number.isFinite(parsed.confidence) &&
      typeof parsed.notificationTitle === 'string' && typeof parsed.notificationMessage === 'string');
  if (!result.schemaValid) { result.validationErrors.push('INVALID_APP_SCHEMA'); return result; }
  if (scenario.task === 'weekly') {
    result.businessValid = text(parsed.insight) && parsed.insight.trim().length <= 1000 &&
      text(parsed.action) && parsed.action.trim().length <= 1000;
  } else {
    result.businessValid = scenario.allowedModes.includes(parsed.recommendedMode) &&
      parsed.confidence >= 0 && parsed.confidence <= 100 &&
      text(parsed.explanation) && parsed.explanation.trim().length <= 1000 &&
      text(parsed.notificationTitle) && parsed.notificationTitle.trim().length <= 80 &&
      text(parsed.notificationMessage) && parsed.notificationMessage.trim().length <= 240;
  }
  if (!result.businessValid) result.validationErrors.push('INVALID_BUSINESS_RULES');
  result.accepted = result.businessValid;
  // Persist only typed app fields, never arbitrary extra server fields or response bodies.
  const fields = scenario.task === 'weekly' ? ['insight', 'action']
    : ['recommendedMode', 'explanation', 'confidence', 'notificationTitle', 'notificationMessage'];
  result.reply = Object.fromEntries(fields.map(field => [field, parsed[field]]));
  return result;
}

function readLocalJson(path) {
  try { return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')); }
  catch { throw new Error('Could not read local credential/Firebase configuration. Check its JSON format locally.'); }
}

export function loadCredentials({ env = process.env, localPath = join(projectRoot, 'ai-evaluation.local.json'),
  firebasePath = join(projectRoot, 'app/google-services.json'), readJson = readLocalJson, fileExists = existsSync } = {}) {
  const local = fileExists(localPath) ? readJson(localPath) : {};
  const firebase = env.ECOSTEP_FIREBASE_API_KEY ? {} : fileExists(firebasePath) ? readJson(firebasePath) : {};
  const credentials = {
    email: env.ECOSTEP_TEST_EMAIL || local.email,
    password: env.ECOSTEP_TEST_PASSWORD || local.password,
    apiKey: env.ECOSTEP_FIREBASE_API_KEY || firebase.client?.[0]?.api_key?.[0]?.current_key,
  };
  if (!text(credentials.email) || !text(credentials.password) || !text(credentials.apiKey)) {
    throw new Error('Set local test credentials in ai-evaluation.local.json or ECOSTEP_TEST_EMAIL/ECOSTEP_TEST_PASSWORD, and supply app/google-services.json or ECOSTEP_FIREBASE_API_KEY.');
  }
  return credentials;
}

const workerErrorCodes = new Set(['NOT_FOUND', 'METHOD_NOT_ALLOWED', 'NOT_CONFIGURED', 'UNAUTHENTICATED',
  'TOO_LARGE', 'INVALID_REQUEST', 'RATE_LIMITED', 'UPSTREAM_AUTH', 'MODEL_UNAVAILABLE', 'UPSTREAM_ERROR',
  'INVALID_AI_RESPONSE', 'UPSTREAM_TIMEOUT', 'UPSTREAM_UNAVAILABLE']);

export async function signIn(credentials, { timeoutMs, fetchImpl = fetch }) {
  const started = performance.now();
  const summary = { method: 'firebase-email-password', success: false, httpStatus: null, errorType: null };
  try {
    const response = await fetchImpl('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + encodeURIComponent(credentials.apiKey), {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Android-Package': 'com.ecostep.app' },
      signal: AbortSignal.timeout(timeoutMs),
      body: JSON.stringify({ email: credentials.email, password: credentials.password, returnSecureToken: true }),
    });
    summary.httpStatus = response.status;
    const data = await response.json();
    if (!response.ok || !text(data?.idToken)) { summary.errorType = 'AUTH_REJECTED'; return { summary, token: null }; }
    summary.success = true;
    // Token is returned privately to the runner, not included in the exportable summary.
    return { summary, token: data.idToken };
  } catch (error) {
    summary.errorType = error.name === 'TimeoutError' || error.name === 'AbortError' ? 'TIMEOUT' : 'AUTH_UNAVAILABLE';
    return { summary, token: null };
  } finally { summary.durationMs = performance.now() - started; }
}

export function serializeReport(report, secrets) {
  const serialized = JSON.stringify(report, null, 2) + '\n';
  if (secrets.some(secret => typeof secret === 'string' && secret.length > 0 &&
      (serialized.includes(secret) || serialized.includes(JSON.stringify(secret).slice(1, -1))))) {
    throw new Error('Report export blocked: private credential material was detected.');
  }
  return serialized;
}

function latency(values) {
  if (!values.length) return { count: 0, meanMs: null, p50Ms: null, p95Ms: null, minMs: null, maxMs: null };
  const sorted = [...values].sort((a, b) => a - b);
  return {
    count: sorted.length, meanMs: sorted.reduce((a, b) => a + b, 0) / sorted.length,
    p50Ms: sorted[Math.ceil(sorted.length * 0.50) - 1], p95Ms: sorted[Math.ceil(sorted.length * 0.95) - 1],
    minMs: sorted[0], maxMs: sorted.at(-1),
  };
}

export function summarize(records, planned) {
  const count = flag => records.filter(record => record[flag]).length;
  const attempted = records.length;
  const httpSuccess = records.filter(record => record.httpStatus >= 200 && record.httpStatus < 300).length;
  const bodies = count('responseBodyReceived'), parsed = count('proxyJsonParsed'), envelope = count('proxyEnvelopeValid');
  const schema = count('schemaValid'), valid = count('businessValid'), accepted = count('accepted');
  const rate = (numerator, denominator) => ({ numerator, denominator, value: denominator ? numerator / denominator : null });
  return {
    plannedRequests: planned, attemptedRequests: attempted, skippedRequests: planned - attempted,
    httpSuccessfulRequests: httpSuccess, responseBodies: bodies, proxyJsonParsedResponses: parsed, proxyEnvelopeValidResponses: envelope,
    schemaValidResponses: schema, businessValidResponses: valid, acceptedResponses: accepted,
    timeoutRequests: records.filter(record => record.errorType === 'TIMEOUT').length,
    workerTimeoutResponses: records.filter(record => record.workerErrorCode === 'UPSTREAM_TIMEOUT').length,
    workerInvalidAiResponses: records.filter(record => record.workerErrorCode === 'INVALID_AI_RESPONSE').length,
    completionRate: rate(attempted, planned), httpSuccessRate: rate(httpSuccess, attempted),
    // Error envelopes can be valid JSON. This rate is not Gemini's original text parse rate.
    proxyJsonParseRate: rate(parsed, bodies), schemaValidRateAmongEnvelopes: rate(schema, envelope),
    geminiRawJsonParseRate: null,
    businessValidRateAmongSchemaValid: rate(valid, schema), appUsableRate: rate(accepted, attempted),
    allAttemptLatencyMs: latency(records.map(record => record.requestDurationMs)),
    httpSuccessLatencyMs: latency(records.filter(record => record.httpStatus >= 200 && record.httpStatus < 300).map(record => record.requestDurationMs)),
    appUsableLatencyMs: latency(records.filter(record => record.accepted).map(record => record.requestDurationMs)),
    passed: attempted === planned && accepted === planned && planned > 0,
  };
}

export async function requestSample(scenario, { token, proxyUrl, timeoutMs, fetchImpl = fetch }) {
  const started = performance.now();
  const record = { scenario: scenario.name, task: scenario.task, httpStatus: null, errorType: null,
    workerErrorCode: null, responseBodyReceived: false, proxyJsonParsed: false, proxyEnvelopeValid: false,
    schemaValid: false, businessValid: false, accepted: false };
  try {
    const response = await fetchImpl(new URL('v1/ai', proxyUrl), {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`,
        'User-Agent': 'EcoStep/1.0', Accept: 'application/json' },
      signal: AbortSignal.timeout(timeoutMs),
      body: JSON.stringify({ task: scenario.task, prompt: scenario.prompt }),
    });
    record.httpStatus = response.status;
    const body = await response.text();
    record.responseBodyReceived = body.length > 0;
    record.requestDurationMs = performance.now() - started;
    const validationStarted = performance.now();
    let data;
    try { data = JSON.parse(body); record.proxyJsonParsed = true; }
    catch { record.errorType = response.ok ? 'INVALID_PROXY_JSON' : `HTTP_${response.status}`; return record; }
    if (!response.ok) {
      record.errorType = `HTTP_${response.status}`;
      record.workerErrorCode = workerErrorCodes.has(data?.error?.code) ? data.error.code : 'UNKNOWN_ERROR';
      return record;
    }
    Object.assign(record, analyseProxyResponse(data, scenario));
    record.validationDurationMs = performance.now() - validationStarted;
  } catch (error) {
    // Never store exception messages, Authorization headers or raw error bodies.
    record.errorType = error.name === 'TimeoutError' || error.name === 'AbortError' ? 'TIMEOUT' : 'NETWORK_ERROR';
  } finally {
    record.requestDurationMs ??= performance.now() - started;
  }
  return record;
}

export function parseOptions(args) {
  const client = readFileSync(join(projectRoot, 'app/src/main/java/com/ecostep/app/network/ai/AiWorkerClient.kt'), 'utf8');
  const options = { samplesPerCase: 3, intervalMs: 1000, timeoutMs: 35000,
    proxyUrl: client.match(/const val BASE_URL\s*=\s*"([^"]+)"/)?.[1],
    caseName: null, smoke: false, networkConditions: 'unspecified' };
  const fields = { '--samples-per-case': 'samplesPerCase', '--interval-ms': 'intervalMs', '--timeout-ms': 'timeoutMs',
    '--proxy-url': 'proxyUrl', '--case': 'caseName', '--network-conditions': 'networkConditions' };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--smoke') { options.smoke = true; continue; }
    const field = fields[args[i]];
    if (!field || i + 1 >= args.length) throw new Error('Unknown option or missing value. See docs/AI_LIVE_EVALUATION.md.');
    options[field] = args[++i];
  }
  for (const [field, min, max] of [['samplesPerCase', 1, 20], ['intervalMs', 0, 60000], ['timeoutMs', 1000, 120000]]) {
    options[field] = Number(options[field]);
    if (!Number.isInteger(options[field]) || options[field] < min || options[field] > max) throw new Error(`Invalid ${field}; expected ${min}..${max}.`);
  }
  let proxy;
  try { proxy = new URL(options.proxyUrl); } catch { throw new Error('Invalid AI proxy URL.'); }
  if (proxy.protocol !== 'https:' || !proxy.hostname.endsWith('.workers.dev') || proxy.username || proxy.password ||
      proxy.search || proxy.hash || proxy.pathname !== '/') throw new Error('Use an HTTPS Cloudflare Worker base URL without credentials, query parameters or an endpoint path.');
  options.proxyUrl = proxy.href;
  if (options.caseName && !cases.some(scenario => scenario.name === options.caseName)) throw new Error('Unknown evaluation case.');
  if (options.smoke) { options.samplesPerCase = 1; options.caseName ??= 'mission_walking_only'; }
  return options;
}

function git(...args) {
  try { return execFileSync('git', ['-c', `safe.directory=${projectRoot.replaceAll('\\', '/')}`, ...args], { cwd: projectRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); }
  catch { return null; }
}

async function main() {
  const options = parseOptions(process.argv.slice(2));
  const credentials = loadCredentials();
  const selected = options.caseName ? cases.filter(scenario => scenario.name === options.caseName) : cases;
  const startedAt = Date.now();
  const filename = join(projectRoot, 'evaluation_results', `ai-proxy-live-evaluation-${startedAt}.json`);
  const gitStatus = git('status', '--porcelain');
  const report = {
    schemaVersion: 2, datasetVersion: 1, startedAtMillis: startedAt,
    gitCommit: git('rev-parse', 'HEAD'), workingTreeDirty: gitStatus === null ? null : Boolean(gitStatus),
    scriptSha256: createHash('sha256').update(readFileSync(fileURLToPath(import.meta.url))).digest('hex'),
    transport: 'firebase-ai-proxy', proxyUrl: options.proxyUrl, modelVersion: null,
    runtime: process.version, platform: process.platform, architecture: process.arch,
    networkConditions: options.networkConditions,
    scope: 'Host Firebase sign-in -> authenticated Cloudflare AI Worker -> Gemini, using synthetic facts. Measures proxy JSON, app-compatible schema and selected local business rules. Original Gemini text, finish reason, model version and token usage are not exposed by this endpoint. No Android UI, real journeys, automatic retry/fallback or semantic quality scoring.',
    benchmarkMethod: 'Sequential non-streaming requests. Monotonic elapsed time includes request preparation, host network, Worker authentication/parsing and Gemini generation through response body receipt. Firebase login is measured separately and excluded from request P50/P95. Validation, pacing and file writes excluded; connection reuse possible. No warm-up or retries; nearest-rank individual request percentiles.',
    configuration: { samplesPerCase: options.samplesPerCase, intervalMs: options.intervalMs, timeoutMs: options.timeoutMs,
      warmups: 0, retries: 0 },
    fixtures: selected, authentication: null, records: [], stopReason: null,
  };
  const planned = selected.length * options.samplesPerCase;
  const secrets = [credentials.email, credentials.password, credentials.apiKey];
  function save() {
    report.finishedAtMillis = Date.now();
    report.summary = summarize(report.records, planned);
    report.byTask = [...new Set(selected.map(scenario => scenario.task))].map(task => ({ task,
      ...summarize(report.records.filter(record => record.task === task), selected.filter(scenario => scenario.task === task).length * options.samplesPerCase) }));
    mkdirSync(dirname(filename), { recursive: true });
    writeFileSync(filename, serializeReport(report, secrets));
  }
  // Persist after every request, including failures, so interrupted runs retain their results.
  save();
  const authentication = await signIn(credentials, options);
  report.authentication = authentication.summary;
  if (authentication.token) secrets.push(authentication.token);
  console.log(`Firebase sign-in: HTTP ${authentication.summary.httpStatus ?? 'none'}, success=${authentication.summary.success}`);
  if (!authentication.summary.success) {
    report.stopReason = 'AUTH_FAILED'; save();
    console.log(`Report: ${filename}`); process.exitCode = 1; return;
  }
  outer: for (let sample = 1; sample <= options.samplesPerCase; sample++) {
    for (const scenario of selected) {
      if (report.records.length && options.intervalMs) await new Promise(done => setTimeout(done, options.intervalMs));
      const record = await requestSample(scenario, { token: authentication.token, ...options });
      report.records.push({ sample, ...record });
      if ([400, 401, 403, 404, 429].includes(record.httpStatus)) report.stopReason = `HTTP_${record.httpStatus}`;
      save();
      console.log(`${scenario.name} #${sample}: HTTP ${record.httpStatus ?? 'none'}, ${record.requestDurationMs.toFixed(1)} ms, proxyJSON=${record.proxyJsonParsed}, schema=${record.schemaValid}, usable=${record.accepted}, error=${record.workerErrorCode ?? record.errorType ?? 'none'}`);
      if (report.stopReason) break outer;
    }
  }
  console.log(`Report: ${filename}`);
  console.log(JSON.stringify(report.summary, null, 2));
  if (!report.summary.passed) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { console.error(`Live evaluation: ${error.message}`); process.exitCode = 1; });
}
