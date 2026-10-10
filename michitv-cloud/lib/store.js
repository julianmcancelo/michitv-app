/**
 * Capa de persistencia sobre Upstash Redis (HTTP REST, sin conexiones).
 * Guarda TODA la base como un único JSON (misma forma que database.json local).
 */

const DB_KEY = 'michitv:db';

function redisEnv() {
  const url = process.env.UPSTASH_REDIS_REST_URL || process.env.KV_REST_API_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN || process.env.KV_REST_API_TOKEN;
  if (!url || !token) {
    throw new Error('Faltan UPSTASH_REDIS_REST_URL / UPSTASH_REDIS_REST_TOKEN en las variables de entorno.');
  }
  return { url: url.replace(/\/$/, ''), token };
}

async function redis(cmd, ...args) {
  const { url, token } = redisEnv();
  const res = await fetch(`${url}/${cmd}/${args.map((a) => encodeURIComponent(a)).join('/')}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Redis ${cmd} falló: ${res.status}`);
  const data = await res.json();
  return data.result;
}

function blankDb() {
  return {
    devices: {},
    vouchers: {},
    users: {},
    config: {
      maintenance: false,
      maintenance_message: 'Estamos mejorando MichiTV. Volvemos en minutos.',
      maintenance_until: null,
    },
    support_tickets: {},
  };
}

function normalizeDb(db) {
  const base = blankDb();
  if (!db || typeof db !== 'object') return base;
  return {
    devices: db.devices && typeof db.devices === 'object' ? db.devices : {},
    vouchers: db.vouchers && typeof db.vouchers === 'object' ? db.vouchers : {},
    users: db.users && typeof db.users === 'object' ? db.users : {},
    config: { ...base.config, ...(db.config || {}) },
    support_tickets: db.support_tickets && typeof db.support_tickets === 'object' ? db.support_tickets : {},
  };
}

async function loadDb() {
  try {
    const raw = await redis('get', DB_KEY);
    if (!raw) return blankDb();
    return normalizeDb(typeof raw === 'string' ? JSON.parse(raw) : raw);
  } catch (e) {
    console.error('loadDb:', e.message);
    return blankDb();
  }
}

async function saveDb(db) {
  const payload = JSON.stringify(db);
  // Upstash REST: SET key value (el valor va en el path, encodeado)
  const { url, token } = redisEnv();
  const res = await fetch(`${url}/set/${DB_KEY}/${encodeURIComponent(payload)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Redis set falló: ${res.status}`);
}

// ---- Estados conversacionales (sobreviven entre instancias, 10 min TTL) ----

const STATE_TTL_S = 600;

async function getState(chatId) {
  try {
    const raw = await redis('get', `michitv:state:${chatId}`);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

async function setState(chatId, state) {
  const { url, token } = redisEnv();
  const payload = encodeURIComponent(JSON.stringify(state));
  await fetch(`${url}/set/${`michitv:state:${chatId}`}/${payload}/EX/${STATE_TTL_S}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

async function clearState(chatId) {
  try {
    await redis('del', `michitv:state:${chatId}`);
  } catch (e) {}
}

module.exports = { loadDb, saveDb, blankDb, getState, setState, clearState };
