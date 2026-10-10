const { catalogRequest } = require('./firebase-catalog.cjs');

async function main() {
  const project = process.argv[2] || 'comp90018-cb523';
  const request = await catalogRequest(project);
  const response = await request('?pageSize=100');
  if (!response.ok) throw new Error(`Catalog read failed: HTTP ${response.status}`);
  const result = await response.json();
  console.log(JSON.stringify({ project, rewards: (result.documents || []).map(document => ({
    id: document.name.split('/').pop(), fields: document.fields,
  })) }, null, 2));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
