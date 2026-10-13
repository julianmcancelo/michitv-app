const https = require('https');
const http = require('http');
const { URL } = require('url');

/**
 * Resuelve una URL de streaming directo para un ID o URL de Google Drive,
 * gestionando las redirecciones y cookies de confirmación de descarga para archivos > 100MB.
 */
function resolveDriveStreamUrl(fileId) {
  return new Promise((resolve, reject) => {
    const initialUrl = `https://drive.google.com/uc?export=download&id=${fileId}`;

    function fetchStep(targetUrl, cookieHeader) {
      const parsed = new URL(targetUrl);
      const isHttps = parsed.protocol === 'https:';
      const client = isHttps ? https : http;

      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': '*/*'
      };
      if (cookieHeader) {
        headers['Cookie'] = cookieHeader;
      }

      const req = client.request(parsed, { method: 'GET', headers }, (res) => {
        const cookies = res.headers['set-cookie'] || [];
        const nextCookie = cookies.map(c => c.split(';')[0]).join('; ');
        const effectiveCookie = [cookieHeader, nextCookie].filter(Boolean).join('; ');

        // Redirección HTTP 301, 302, 303, 307
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          const redirectUrl = new URL(res.headers.location, targetUrl).toString();
          res.resume(); // descartar body
          return fetchStep(redirectUrl, effectiveCookie);
        }

        // Si es 200, verificar si es la página de confirmación de virus de Google
        let body = '';
        res.setEncoding('utf8');
        res.on('data', chunk => {
          if (body.length < 50000) body += chunk;
        });

        res.on('end', () => {
          // Si nos redirigió al CDN de descarga directa de Google (ej: usercontent)
          if (targetUrl.includes('googleusercontent.com') || targetUrl.includes('&confirm=')) {
            return resolve({
              url: targetUrl,
              headers: effectiveCookie ? { 'Cookie': effectiveCookie } : {}
            });
          }

          // Buscar el token de confirmación en el HTML
          const confirmMatch = body.match(/confirm=([a-zA-Z0-9_\-]+)/) || body.match(/name="confirm" value="([^"]+)"/);
          if (confirmMatch) {
            const token = confirmMatch[1];
            const confirmUrl = `https://drive.google.com/uc?export=download&confirm=${token}&id=${fileId}`;
            return fetchStep(confirmUrl, effectiveCookie);
          }

          // Si no hay confirmación pero es un archivo directo
          resolve({
            url: targetUrl,
            headers: effectiveCookie ? { 'Cookie': effectiveCookie } : {}
          });
        });
      });

      req.on('error', reject);
      req.end();
    }

    fetchStep(initialUrl, '');
  });
}

/**
 * Consulta un Google Drive Index (Cloudflare Worker tipo GoIndex / GDIndex)
 * o devuelve una lista simulada si no hay índice configurado.
 */
function browseDriveIndex(indexBaseUrl, path = '/') {
  return new Promise((resolve, reject) => {
    if (!indexBaseUrl) {
      return resolve({
        path,
        folders: [],
        files: []
      });
    }

    const cleanBase = indexBaseUrl.replace(/\/+$/, '');
    const cleanPath = path.startsWith('/') ? path : `/${path}`;
    const targetUrl = new URL(cleanBase + cleanPath);

    const client = targetUrl.protocol === 'https:' ? https : http;
    const req = client.request(targetUrl, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'User-Agent': 'MichiTV-Drive/2.1'
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          // Estandarizar respuesta para MichiTV
          const files = (parsed.data?.files || parsed.files || []).map(f => {
            const isDir = f.mimeType === 'application/vnd.google-apps.folder' || f.is_dir || f.type === 'folder';
            return {
              id: f.id || f.name,
              name: f.name,
              size: f.size || 0,
              isFolder: isDir,
              mimeType: f.mimeType || (isDir ? 'folder' : 'video/mp4'),
              link: f.link || (isDir ? null : `${cleanBase}${cleanPath === '/' ? '' : cleanPath}/${encodeURIComponent(f.name)}`)
            };
          });

          resolve({
            path: cleanPath,
            items: files
          });
        } catch (e) {
          resolve({ path: cleanPath, items: [], error: 'Respuesta no válida del Index' });
        }
      });
    });

    req.on('error', (err) => {
      resolve({ path: cleanPath, items: [], error: err.message });
    });

    req.write(JSON.stringify({ password: '', page_index: 0 }));
    req.end();
  });
}

module.exports = {
  resolveDriveStreamUrl,
  browseDriveIndex
};
