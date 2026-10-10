const { loadDb, saveDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  const q = req.query || {};
  let deviceCode = String(q.device || '').toUpperCase().trim();
  if (!deviceCode.startsWith('MICHI-') && deviceCode.length === 4) deviceCode = `MICHI-${deviceCode}`;
  const db = await loadDb();
  if (deviceCode && db.devices[deviceCode]) {
    db.devices[deviceCode].activated = false;
    db.devices[deviceCode].deactivated_at = new Date().toISOString();
    await saveDb(db);
    res.status(200).end(JSON.stringify({ success: true, deviceCode, isActivated: false }));
  } else {
    res.status(404).end(JSON.stringify({ success: false, error: 'Dispositivo no encontrado' }));
  }
};
