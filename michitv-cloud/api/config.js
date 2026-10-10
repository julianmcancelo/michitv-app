const { loadDb, saveDb } = require('../lib/store');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  const db = await loadDb();
  if (db.config && db.config.maintenance && db.config.maintenance_until) {
    if (Date.now() >= db.config.maintenance_until) {
      db.config.maintenance = false;
      db.config.maintenance_until = null;
      await saveDb(db);
    }
  }
  const maintenance = !!(db.config && db.config.maintenance);
  const until = (db.config && db.config.maintenance_until) || null;
  res.status(200).end(JSON.stringify({
    maintenance,
    message: (db.config && db.config.maintenance_message) || 'Estamos mejorando MichiTV. Volvemos en minutos.',
    maintenance_until: until,
    timeLeft: maintenance && until ? Math.max(0, until - Date.now()) : null,
  }));
};
