import test from 'node:test';
import assert from 'node:assert/strict';
import { analyseResponse, cases, parseOptions, requestSample, summarize } from './run-gemini-live-evaluation.mjs';

const mission = cases[0];
const valid = { recommendedMode: 'WALKING', explanation: 'Walk this short journey.', confidence: 80,
  notificationTitle: 'Try walking', notificationMessage: 'Choose the verified walking option.' };
function envelope(value, finishReason = 'STOP') {
  return { candidates: [{ finishReason, content: { parts: [{ text: JSON.stringify(value) }] } }] };
}

test('accepts typed mission JSON and ignores extra keys like the app decoder', () => {
  assert.equal(analyseResponse(envelope({ ...valid, extra: true }), mission).accepted, true);
});

test('does not count malformed/Markdown JSON or thought text as a usable reply', () => {
  const data = { candidates: [{ finishReason: 'STOP', content: { parts: [
    { thought: true, text: JSON.stringify(valid) }, { text: '```json\n{}\n```' },
  ] } }] };
  const result = analyseResponse(data, mission);
  assert.equal(result.contentReturned, true);
  assert.equal(result.jsonParsed, false);
  assert.equal(result.accepted, false);
});

test('distinguishes parseable JSON, correct app types and valid business values', () => {
  for (const value of [null, [], {}, { ...valid, confidence: '80' }, { ...valid, recommendedMode: 'FLYING' }]) {
    const result = analyseResponse(envelope(value), mission);
    assert.equal(result.jsonParsed, true);
    assert.equal(result.schemaValid, false);
    assert.equal(result.accepted, false);
  }
  for (const value of [{ ...valid, recommendedMode: 'CYCLING' }, { ...valid, recommendedMode: 'UNKNOWN' },
    { ...valid, confidence: 101 }, { ...valid, explanation: ' ' },
    { ...valid, notificationTitle: 'x'.repeat(81) }, { ...valid, notificationMessage: 'x'.repeat(241) }]) {
    const result = analyseResponse(envelope(value), mission);
    assert.equal(result.schemaValid, true);
    assert.equal(result.businessValid, false);
    assert.equal(result.accepted, false);
  }
});

test('a parsed reply from an incomplete generation is not counted as app usable', () => {
  const result = analyseResponse(envelope(valid, 'MAX_TOKENS'), mission);
  assert.equal(result.jsonParsed, true);
  assert.equal(result.businessValid, true);
  assert.equal(result.accepted, false);
});

test('weekly length limits match local coaching validation after trimming', () => {
  const scenario = cases.find(value => value.task === 'weekly');
  assert.equal(analyseResponse(envelope({ insight: ' i ', action: ' a ' }), scenario).accepted, true);
  assert.equal(analyseResponse(envelope({ insight: 'x'.repeat(1001), action: 'a' }), scenario).businessValid, false);
  assert.equal(analyseResponse(envelope({ insight: 'i', action: '' }), scenario).businessValid, false);
});

test('blocked and empty candidates are distinct from invalid JSON', () => {
  const result = analyseResponse({ promptFeedback: { blockReason: 'SAFETY' } }, mission);
  assert.equal(result.contentReturned, false);
  assert.equal(result.promptBlockReason, 'SAFETY');
  assert.equal(result.accepted, false);
});

test('rates use explicit denominators, retain failures/skips and nearest-rank latency', () => {
  const records = [
    { httpStatus: 200, contentReturned: true, jsonParsed: true, schemaValid: true, businessValid: true, accepted: true, requestDurationMs: 100 },
    { httpStatus: 200, contentReturned: true, jsonParsed: false, accepted: false, requestDurationMs: 200 },
    { httpStatus: 429, accepted: false, requestDurationMs: 10 },
    { httpStatus: null, errorType: 'TIMEOUT', accepted: false, requestDurationMs: 35000 },
  ];
  const result = summarize(records, 6);
  assert.deepEqual(result.jsonParseRateAmongContent, { numerator: 1, denominator: 2, value: 0.5 });
  assert.equal(result.httpSuccessRate.value, 0.5);
  assert.equal(result.appUsableRate.value, 0.25);
  assert.equal(result.skippedRequests, 2);
  assert.equal(result.timeoutRequests, 1);
  assert.equal(result.httpSuccessLatencyMs.p50Ms, 100);
  assert.equal(result.httpSuccessLatencyMs.p95Ms, 200);
  assert.equal(result.allAttemptLatencyMs.p95Ms, 35000);
  assert.equal(result.passed, false);
  assert.equal(summarize([], 1).jsonParseRateAmongContent.value, null);
  assert.equal(summarize([], 1).appUsableLatencyMs.p95Ms, null);
});

test('provider HTTP errors retain status but discard diagnostic bodies and credentials', async () => {
  const key = 'test-key-never-log';
  const record = await requestSample(mission, { key, model: 'model', timeoutMs: 1000,
    fetchImpl: async (_url, options) => {
      assert.equal(options.headers['x-goog-api-key'], key);
      return new Response(`invalid key ${key}`, { status: 403 });
    } });
  assert.equal(record.httpStatus, 403);
  assert.equal(record.errorType, 'HTTP_403');
  assert.equal(record.jsonParsed, false);
  assert.ok(!JSON.stringify(record).includes(key));
});

test('network exceptions are sanitized and timeouts are classified separately', async () => {
  for (const [name, expected] of [['TypeError', 'NETWORK_ERROR'], ['TimeoutError', 'TIMEOUT']]) {
    const record = await requestSample(mission, { key: 'key', model: 'model', timeoutMs: 1000,
      fetchImpl: async () => { const error = new Error('secret diagnostics'); error.name = name; throw error; } });
    assert.equal(record.errorType, expected);
    assert.ok(!JSON.stringify(record).includes('secret diagnostics'));
    assert.ok(record.requestDurationMs >= 0);
  }
});

test('HTTP success with invalid provider envelope is not a model parsing failure', async () => {
  const record = await requestSample(mission, { key: 'key', model: 'model', timeoutMs: 1000,
    fetchImpl: async () => new Response('not JSON', { status: 200 }) });
  assert.equal(record.httpStatus, 200);
  assert.equal(record.errorType, 'INVALID_PROVIDER_ENVELOPE');
  assert.equal(record.contentReturned, false);
  assert.equal(record.accepted, false);
});

test('request keeps plain JSON generation without schema enforcement, retries or secret URL params', async () => {
  let calls = 0;
  const result = await requestSample(mission, { key: 'key', model: 'model', timeoutMs: 1000,
    fetchImpl: async (url, options) => {
      calls++;
      assert.ok(!url.includes('key='));
      const body = JSON.parse(options.body);
      assert.equal(body.generationConfig.responseMimeType, 'application/json');
      assert.equal(body.generationConfig.responseSchema, undefined);
      assert.equal(body.contents[0].parts[0].text, mission.prompt);
      return new Response(JSON.stringify(envelope(valid)), { status: 200 });
    } });
  assert.equal(result.accepted, true);
  assert.equal(calls, 1);
});

test('smoke is bounded and invalid/big request configurations are rejected before calling', () => {
  assert.equal(parseOptions(['--smoke']).samplesPerCase, 1);
  assert.equal(parseOptions(['--smoke']).caseName, mission.name);
  assert.throws(() => parseOptions(['--samples-per-case', '21']));
  assert.throws(() => parseOptions(['--case', 'missing']));
  assert.throws(() => parseOptions(['--timeout-ms', 'NaN']));
  assert.throws(() => parseOptions(['--unknown']));
});
