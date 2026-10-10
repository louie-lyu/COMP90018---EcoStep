const rewards = require('../config/demo-rewards.json');
const { catalogRequest } = require('./firebase-catalog.cjs');

function documentFields(reward) {
  const { rewardId, ...data } = reward;
  if (!/^ecostep-demo-[a-z]+$/.test(rewardId) ||
      !Number.isInteger(data.pointsRequired) || data.pointsRequired <= 0 ||
      !data.title.startsWith('Demo ') || !data.description.startsWith('Demo only:')) {
    throw new Error('Invalid demo catalog entry.');
  }
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key,
    typeof value === 'boolean' ? { booleanValue: value } :
      typeof value === 'number' ? { integerValue: String(value) } : { stringValue: value },
  ]));
}

/** Create-only REST writes preserve manually edited or disabled existing offers. */
async function seedCatalog(request) {
  const results = [];
  for (const reward of rewards) {
    const response = await request(`?documentId=${encodeURIComponent(reward.rewardId)}`, {
      method: 'POST', body: JSON.stringify({ fields: documentFields(reward) }),
    });
    if (!response.ok && response.status !== 409) {
      throw new Error(`Create ${reward.rewardId} failed: HTTP ${response.status}`);
    }
    results.push({ rewardId: reward.rewardId, status: response.status === 409 ? 'existing, unchanged' : 'created' });
  }
  return results;
}

async function main() {
  const args = process.argv.slice(2);
  const project = args[args.indexOf('--project') + 1];
  if (!args.includes('--project') || !project || project.startsWith('--')) {
    throw new Error('Usage: node scripts/seed-demo-rewards.cjs --project PROJECT [--apply]');
  }
  rewards.forEach(documentFields);
  if (!args.includes('--apply')) {
    console.log(JSON.stringify({ project, mode: 'dry-run', rewards }, null, 2));
    return;
  }
  const results = await seedCatalog(await catalogRequest(project));
  console.log(JSON.stringify({ project, results }, null, 2));
}

module.exports = { seedCatalog, documentFields };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
