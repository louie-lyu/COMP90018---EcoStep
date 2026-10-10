const assert = require('node:assert/strict');
const { test } = require('node:test');
const { seedCatalog, documentFields } = require('./seed-demo-rewards.cjs');
const rewards = require('../config/demo-rewards.json');

test('demo catalog uses compatible fields and affordable prices', () => {
  assert.deepEqual(rewards.map(reward => reward.pointsRequired), [10, 20, 50]);
  for (const reward of rewards) {
    const fields = documentFields(reward);
    assert.equal(fields.active.booleanValue, true);
    assert.match(fields.description.stringValue, /No monetary value/);
  }
});

test('re-running seed preserves existing catalog entries', async () => {
  const stored = new Map();
  const request = async (url, options) => {
    assert.equal(options.method, 'POST');
    const id = new URLSearchParams(url.slice(1)).get('documentId');
    if (stored.has(id)) return { ok: false, status: 409 };
    stored.set(id, JSON.parse(options.body));
    return { ok: true, status: 200 };
  };
  await seedCatalog(request);
  stored.get(rewards[0].rewardId).fields.active = { booleanValue: false };
  const results = await seedCatalog(request);
  assert.equal(stored.size, 3);
  assert.equal(stored.get(rewards[0].rewardId).fields.active.booleanValue, false);
  assert.ok(results.every(result => result.status === 'existing, unchanged'));
});

test('seed reports permission failures instead of pretending success', async () => {
  await assert.rejects(seedCatalog(async () => ({ ok: false, status: 403 })), /HTTP 403/);
});
