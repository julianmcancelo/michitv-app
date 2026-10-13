const { loadDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  const q = req.query || {};
  const action = q.action || '';

  // 1. Multiplexor de Cine Privado (compatible con Vercel actual)
  if (action === 'cinema_catalog' || action === 'cinema') {
    const db = await loadDb();
    const rawList = Object.values(db.custom_media || {});
    const list = rawList.sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0));
    return res.status(200).end(JSON.stringify({ items: list }));
  }

  // 2. Multiplexor de Google Drive
  if (action === 'drive_stream') {
    const fileId = q.id;
    if (!fileId) return res.status(400).end(JSON.stringify({ error: "Falta id" }));
    try {
      const { resolveDriveStreamUrl } = require('../lib/drive');
      const data = await resolveDriveStreamUrl(fileId);
      return res.status(200).end(JSON.stringify(data));
    } catch (e) {
      return res.status(500).end(JSON.stringify({ error: e.message }));
    }
  }

  if (action === 'drive_browse') {
    const indexUrl = q.index || '';
    const folderPath = q.path || '/';
    try {
      const { browseDriveIndex } = require('../lib/drive');
      const data = await browseDriveIndex(indexUrl, folderPath);
      return res.status(200).end(JSON.stringify(data));
    } catch (e) {
      return res.status(500).end(JSON.stringify({ error: e.message }));
    }
  }

  // 3. Comportamiento por defecto: Status de dispositivo
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
