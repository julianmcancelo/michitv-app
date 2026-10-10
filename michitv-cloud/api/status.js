const { loadDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  const q = req.query || {};
  const deviceCode = String(q.device || '').toUpperCase();
  const db = await loadDb();
  if (db.devices[deviceCode]) {
    const dev = db.devices[deviceCode];
    const isExpired = new Date(dev.expires_at) <= new Date();
    res.status(200).end(JSON.stringify({
      found: true,
      deviceCode: dev.device_code,
      isActivated: dev.activated && !isExpired,
      planName: dev.plan,
      expiresAt: dev.expires_at,
      licenseKey: dev.license_key,
      tg_username: dev.tg_username || null,
    }));
  } else {
    res.status(200).end(JSON.stringify({ found: false, isActivated: false, planName: 'Plan Gratuito MichiTV' }));
  }
};
