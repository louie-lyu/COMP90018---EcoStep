import test from 'node:test';
import assert from 'node:assert/strict';
import { analyseProxyResponse, cases, loadCredentials, parseOptions, requestSample, serializeReport, signIn, summarize } from './run-gemini-live-evaluation.mjs';

const mission = cases[0];
const valid = { recommendedMode: 'WALKING', explanation: 'Walk this short journey.', confidence: 80,
  notificationTitle: 'Try walking', notificationMessage: 'Choose the verified walking option.' };
const envelope = result => ({ task: 'mission', result });
const requestOptions = { token: 'fake-private-token', proxyUrl: 'https://example.workers.dev/', timeoutMs: 1000 };

test('accepts app-compatible fields and excludes arbitrary extra fields from exported replies', () => {
  const result = analyseProxyResponse(envelope({ ...valid, privateExtra: 'never-export' }), mission);
  assert.equal(result.accepted, true);
  assert.ok(!JSON.stringify(result).includes('never-export'));
});

test('rejects mismatched task, missing result and primitive/array proxy envelopes', () => {
  for (const value of [null, [], {}, { task: 'weekly', result: valid }, envelope(null), envelope([])]) {
    const result = analyseProxyResponse(value, mission);
    assert.equal(result.proxyEnvelopeValid, false);
    assert.equal(result.accepted, false);
  }
});

test('distinguishes app field types from business-rule failures', () => {
  for (const value of [{}, { ...valid, confidence: '80' }, { ...valid, recommendedMode: 'FLYING' }]) {
    const result = analyseProxyResponse(envelope(value), mission);
    assert.equal(result.proxyEnvelopeValid, true);
    assert.equal(result.schemaValid, false);
    assert.equal(result.accepted, false);
  }
  for (const value of [{ ...valid, recommendedMode: 'CYCLING' }, { ...valid, recommendedMode: 'UNKNOWN' },
    { ...valid, confidence: 101 }, { ...valid, explanation: ' ' },
    { ...valid, notificationTitle: 'x'.repeat(81) }, { ...valid, notificationMessage: 'x'.repeat(241) }]) {
    const result = analyseProxyResponse(envelope(value), mission);
    assert.equal(result.schemaValid, true);
    assert.equal(result.businessValid, false);
    assert.equal(result.accepted, false);
  }
});

test('weekly text bounds apply after trimming and no model metadata is invented', () => {
  const scenario = cases.find(value => value.task === 'weekly');
  const result = analyseProxyResponse({ task: 'weekly', result: { insight: ' i ', action: ' a ' } }, scenario);
  assert.equal(result.accepted, true);
  assert.equal(result.finishReason, undefined);
  assert.equal(result.modelVersion, undefined);
  assert.equal(analyseProxyResponse({ task: 'weekly', result: { insight: 'x'.repeat(1001), action: 'a' } }, scenario).businessValid, false);
  assert.equal(analyseProxyResponse({ task: 'weekly', result: { insight: 'i', action: '' } }, scenario).businessValid, false);
});

test('proxy JSON rates include errors without inventing a raw Gemini parsing rate', () => {
  const records = [
    { httpStatus: 200, responseBodyReceived: true, proxyJsonParsed: true, proxyEnvelopeValid: true, schemaValid: true, businessValid: true, accepted: true, requestDurationMs: 100 },
    { httpStatus: 502, responseBodyReceived: true, proxyJsonParsed: true, workerErrorCode: 'INVALID_AI_RESPONSE', accepted: false, requestDurationMs: 200 },
    { httpStatus: 504, responseBodyReceived: true, proxyJsonParsed: true, workerErrorCode: 'UPSTREAM_TIMEOUT', accepted: false, requestDurationMs: 20000 },
    { httpStatus: null, errorType: 'TIMEOUT', accepted: false, requestDurationMs: 35000 },
  ];
  const result = summarize(records, 6);
  assert.deepEqual(result.proxyJsonParseRate, { numerator: 3, denominator: 3, value: 1 });
  assert.equal(result.httpSuccessRate.value, 0.25);
  assert.equal(result.appUsableRate.value, 0.25);
  assert.equal(result.workerInvalidAiResponses, 1);
  assert.equal(result.workerTimeoutResponses, 1);
  assert.equal(result.geminiRawJsonParseRate, null);
  assert.equal(result.skippedRequests, 2);
  assert.equal(result.httpSuccessLatencyMs.p50Ms, 100);
  assert.equal(result.allAttemptLatencyMs.p95Ms, 35000);
  assert.equal(result.passed, false);
  assert.equal(summarize([], 1).proxyJsonParseRate.value, null);
});

test('Worker error messages and token material are never exported', async () => {
  const record = await requestSample(mission, { ...requestOptions,
    fetchImpl: async (_url, options) => {
      assert.equal(options.headers.Authorization, 'Bearer ' + requestOptions.token);
      return new Response(JSON.stringify({ error: { code: 'INVALID_AI_RESPONSE', message: requestOptions.token } }), { status: 502 });
    } });
  assert.equal(record.errorType, 'HTTP_502');
  assert.equal(record.workerErrorCode, 'INVALID_AI_RESPONSE');
  assert.equal(record.proxyJsonParsed, true);
  assert.ok(!JSON.stringify(record).includes(requestOptions.token));
});

test('unknown server error codes cannot leak secrets through export', async () => {
  const record = await requestSample(mission, { ...requestOptions,
    fetchImpl: async () => new Response(JSON.stringify({ error: { code: requestOptions.token } }), { status: 401 }) });
  assert.equal(record.workerErrorCode, 'UNKNOWN_ERROR');
  assert.ok(!JSON.stringify(record).includes(requestOptions.token));
});

test('network exceptions are sanitized and timeouts are classified separately', async () => {
  for (const [name, expected] of [['TypeError', 'NETWORK_ERROR'], ['TimeoutError', 'TIMEOUT']]) {
    const record = await requestSample(mission, { ...requestOptions,
      fetchImpl: async () => { const error = new Error('private diagnostics'); error.name = name; throw error; } });
    assert.equal(record.errorType, expected);
    assert.ok(!JSON.stringify(record).includes('private diagnostics'));
    assert.ok(record.requestDurationMs >= 0);
  }
});

test('successful HTTP with malformed JSON is rejected', async () => {
  const record = await requestSample(mission, { ...requestOptions,
    fetchImpl: async () => new Response('not JSON', { status: 200 }) });
  assert.equal(record.errorType, 'INVALID_PROXY_JSON');
  assert.equal(record.proxyJsonParsed, false);
  assert.equal(record.accepted, false);
});

test('AI requests match the Worker contract without passwords or a Gemini key', async () => {
  let calls = 0;
  const result = await requestSample(mission, { ...requestOptions,
    fetchImpl: async (url, options) => {
      calls++;
      assert.equal(url.href, 'https://example.workers.dev/v1/ai');
      assert.equal(options.headers['x-goog-api-key'], undefined);
      assert.deepEqual(JSON.parse(options.body), { task: mission.task, prompt: mission.prompt });
      assert.ok(!options.body.includes(requestOptions.token));
      return new Response(JSON.stringify(envelope(valid)), { status: 200 });
    } });
  assert.equal(result.accepted, true);
  assert.equal(calls, 1);
});

test('Firebase login returns its token privately and exports only authentication metrics', async () => {
  const credentials = { email: 'test@example.invalid', password: 'private-test-password', apiKey: 'private-test-client-key' };
  const login = await signIn(credentials, { timeoutMs: 1000, fetchImpl: async (url, options) => {
    assert.ok(url.startsWith('https://identitytoolkit.googleapis.com/'));
    assert.equal(JSON.parse(options.body).password, credentials.password);
    return new Response(JSON.stringify({ idToken: requestOptions.token, refreshToken: 'private-refresh', localId: 'private-uid' }));
  } });
  assert.equal(login.token, requestOptions.token);
  assert.equal(login.summary.success, true);
  for (const secret of [...Object.values(credentials), requestOptions.token, 'private-refresh', 'private-uid'])
    assert.ok(!JSON.stringify(login.summary).includes(secret));
});

test('login rejection and invalid JSON do not export account/server diagnostics', async () => {
  for (const response of [new Response(JSON.stringify({ error: { message: 'private account info' } }), { status: 400 }), new Response('invalid JSON')]) {
    const login = await signIn({ email: 'e', password: 'p', apiKey: 'k' }, { timeoutMs: 1000, fetchImpl: async () => response });
    assert.equal(login.summary.success, false);
    assert.equal(login.token, null);
    assert.ok(!JSON.stringify(login.summary).includes('private account info'));
  }
});

test('credentials load from local files/environment with environment overrides', () => {
  const options = { localPath: 'local', firebasePath: 'firebase', fileExists: () => true,
    readJson: path => path === 'local' ? { email: 'local-email', password: 'local-password' }
      : { client: [{ api_key: [{ current_key: 'local-client-key' }] }] } };
  assert.deepEqual(loadCredentials({ ...options, env: {} }), { email: 'local-email', password: 'local-password', apiKey: 'local-client-key' });
  assert.deepEqual(loadCredentials({ ...options, env: { ECOSTEP_TEST_EMAIL: 'env-email', ECOSTEP_TEST_PASSWORD: 'env-password', ECOSTEP_FIREBASE_API_KEY: 'env-key' } }),
    { email: 'env-email', password: 'env-password', apiKey: 'env-key' });
  assert.throws(() => loadCredentials({ env: {}, fileExists: () => false }));
});

test('report serialization blocks both literal and JSON-escaped credentials', () => {
  assert.throws(() => serializeReport({ accidentalEmail: 'test@example.invalid' }, ['test@example.invalid']));
  assert.throws(() => serializeReport({ accidentalPassword: 'password"\\private' }, ['password"\\private']));
  assert.equal(JSON.parse(serializeReport({ count: 18 }, ['private-password'])).count, 18);
});

test('smoke is bounded and insecure proxy URLs are rejected before login', () => {
  assert.equal(parseOptions(['--smoke']).samplesPerCase, 1);
  assert.equal(parseOptions(['--smoke']).caseName, mission.name);
  assert.throws(() => parseOptions(['--samples-per-case', '21']));
  assert.throws(() => parseOptions(['--case', 'missing']));
  assert.throws(() => parseOptions(['--model', 'old-direct-model']));
  for (const url of ['http://example.workers.dev/', 'https://user:password@example.workers.dev/',
    'https://example.workers.dev/?token=private', 'https://example.workers.dev/v1/ai', 'https://example.invalid/']) {
    assert.throws(() => parseOptions(['--proxy-url', url]));
  }
});
