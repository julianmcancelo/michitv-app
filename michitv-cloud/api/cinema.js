const { loadDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') return res.status(200).end();

  const db = await loadDb();
  const rawList = Object.values(db.custom_media || {});

  // Devolver ordenados por fecha de creación descendente
  const list = rawList.sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0));

  res.status(200).end(JSON.stringify({
    items: list
  }));
};
