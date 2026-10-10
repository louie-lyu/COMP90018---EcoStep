const path = require('node:path');
const auth = require('firebase-tools/lib/auth');

async function catalogRequest(project) {
  const account = auth.getProjectDefaultAccount(path.resolve(__dirname, '../..'));
  if (!account) throw new Error('Sign in with firebase login first.');
  const tokens = await auth.getAccessToken(account.tokens.refresh_token,
    ['https://www.googleapis.com/auth/cloud-platform']);
  const base = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(project)}/databases/(default)/documents/rewards`;
  return async (suffix, options = {}) => fetch(base + suffix, {
    ...options,
    headers: { Authorization: `Bearer ${tokens.access_token}`, 'Content-Type': 'application/json' },
  });
}

module.exports = { catalogRequest };
