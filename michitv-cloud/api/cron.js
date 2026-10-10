const { checkExpirations } = require('../lib/handlers');

module.exports = async (req, res) => {
  const q = req.query || {};
  if (!process.env.CRON_SECRET || q.secret !== process.env.CRON_SECRET) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }
  try {
    const out = await checkExpirations();
    res.status(200).json({ ok: true, ...out });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
};
