const { resolveDriveStreamUrl, browseDriveIndex } = require('../lib/drive');

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Content-Type', 'application/json');
}

module.exports = async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const url = new URL(req.url, 'http://localhost');
  const action = url.searchParams.get('action') || 'browse';

  // 1. Resolver stream directo de un archivo de Drive por ID
  if (action === 'stream') {
    const fileId = url.searchParams.get('id');
    if (!fileId) {
      return res.status(400).end(JSON.stringify({ error: 'Falta parámetro id' }));
    }
    try {
      const streamData = await resolveDriveStreamUrl(fileId);
      return res.status(200).end(JSON.stringify(streamData));
    } catch (e) {
      return res.status(500).end(JSON.stringify({ error: e.message }));
    }
  }

  // 2. Explorar carpetas de un Google Drive Index (Cloudflare Worker)
  const indexUrl = url.searchParams.get('index') || '';
  const folderPath = url.searchParams.get('path') || '/';

  try {
    const listing = await browseDriveIndex(indexUrl, folderPath);
    return res.status(200).end(JSON.stringify(listing));
  } catch (e) {
    return res.status(500).end(JSON.stringify({ error: e.message }));
  }
};
