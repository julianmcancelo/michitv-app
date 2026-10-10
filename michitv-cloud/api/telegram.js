const { handleUpdate } = require('../lib/handlers');

module.exports = async (req, res) => {
  if (req.method === 'GET') {
    res.status(200).send('MichiTV bot webhook OK');
    return;
  }
  if (req.method !== 'POST') {
    res.status(405).send('Method not allowed');
    return;
  }
  try {
    const update = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : req.body || {};
    await handleUpdate(update);
  } catch (e) {
    console.error('webhook:', e.message);
  }
  // Siempre 200 para que Telegram no reintente en bucle
  res.status(200).json({ ok: true });
};
