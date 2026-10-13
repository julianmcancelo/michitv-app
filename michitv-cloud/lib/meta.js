const https = require('https');

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'MichiTV-Bot/2.2' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve(null);
        }
      });
    }).on('error', reject);
  });
}

/**
 * Busca detalles de una película o serie en Cinemeta (Stremio)
 * para obtener título oficial, año, descripción, carátula y backdrop en alta calidad.
 */
async function searchCinemetaMeta(query, type = 'movie') {
  try {
    const q = encodeURIComponent(query.trim());
    const targetType = type === 'series' || type === 'tv' ? 'series' : 'movie';
    const searchUrl = `https://v3-cinemeta.strem.io/catalog/${targetType}/top/search=${q}.json`;
    const res = await fetchJson(searchUrl);
    if (!res || !res.metas || res.metas.length === 0) return null;
    const top = res.metas[0];
    return {
      id: top.id,
      name: top.name,
      type: targetType,
      year: top.year ? String(top.year) : null,
      poster: top.poster || null,
      background: top.background || top.poster || null,
      description: top.description || null,
      genres: top.genres || [],
      rating: top.imdbRating ? parseFloat(top.imdbRating) : null
    };
  } catch (e) {
    console.error('searchCinemetaMeta error:', e.message);
    return null;
  }
}

module.exports = {
  searchCinemetaMeta
};
