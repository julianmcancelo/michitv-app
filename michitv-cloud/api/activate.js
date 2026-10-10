const { loadDb, saveDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
}

function licenseFor(deviceCode) {
  const clean = String(deviceCode).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  const rand = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `MICHI-VIP-${clean}-${rand}`;
}

module.exports = async (req, res) => {
  cors(res);
  const q = req.query || {};
  let deviceCode = String(q.device || '').toUpperCase().trim();
  if (!deviceCode.startsWith('MICHI-') && deviceCode.length === 4) deviceCode = `MICHI-${deviceCode}`;
  if (!deviceCode) {
    res.status(400).end(JSON.stringify({ error: "Parámetro 'device' requerido" }));
    return;
  }
  const db = await loadDb();
  const days = Number(process.env.DEFAULT_DURATION_DAYS || 30);
  if (db.devices[deviceCode]) {
    const existing = db.devices[deviceCode];
    const isExpired = new Date(existing.expires_at) <= new Date();
    if (!isExpired && existing.activated) {
      res.status(200).end(JSON.stringify({
        success: true, deviceCode, isActivated: true,
        planName: existing.plan, licenseKey: existing.license_key,
        message: 'Device was already active.',
      }));
      return;
    }
  }
  const licenseKey = licenseFor(deviceCode);
  const expires = new Date();
  expires.setDate(expires.getDate() + days);
  db.devices[deviceCode] = {
    device_code: deviceCode,
    license_key: licenseKey,
    activated: true,
    activated_at: new Date().toISOString(),
    expires_at: expires.toISOString(),
    plan: 'VIP MichiTV Pass (Auto-Pairing)',
    tg_user_id: 0,
    tg_username: 'Auto-Pairing',
  };
  await saveDb(db);
  res.status(200).end(JSON.stringify({
    success: true, deviceCode, isActivated: true,
    planName: 'VIP MichiTV Pass (Auto-Pairing)', licenseKey,
  }));
};
