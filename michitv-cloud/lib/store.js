/**
 * Persistencia sobre Vercel Blob vía REST crudo (sin SDK).
 * Toda la base como un único JSON + un blob por estado conversacional.
 * Probado: GET/PUT/DELETE contra la API con BLOB_READ_WRITE_TOKEN.
 */

const DB_PATH = 'michitv/db.json';
const STATE_PREFIX = 'michitv/state-';
const STATE_TTL_MS = 10 * 60 * 1000;
const API_VERSION = '12';

function token() {
  const t = process.env.BLOB_READ_WRITE_TOKEN;
  if (!t) throw new Error('Falta BLOB_READ_WRITE_TOKEN en las variables de entorno.');
  return t;
}

function storeHost() {
  const id = String(process.env.BLOB_STORE_ID || '');
  const short = id.replace(/^store_/i, '').toLowerCase();
  if (!short) throw new Error('Falta BLOB_STORE_ID en las variables de entorno.');
  return `https://${short}.private.blob.vercel-storage.com`;
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

async function rawGet(pathname) {
  const res = await fetch(`${storeHost()}/${pathname}`, {
    headers: { Authorization: `Bearer ${token()}` },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`GET ${pathname}: HTTP ${res.status}`);
  return res.json();
}

async function rawPut(pathname, obj) {
  const res = await fetch(`https://vercel.com/api/blob/?pathname=${encodeURIComponent(pathname)}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token()}`,
      'x-content-type': 'application/json',
      'x-api-version': API_VERSION,
      'x-vercel-blob-access': 'private',
      'x-add-random-suffix': '0',
      'x-allow-overwrite': '1',
    },
    body: JSON.stringify(obj),
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(`PUT ${pathname}: HTTP ${res.status} ${txt.slice(0, 120)}`);
  }
}

async function loadDb() {
  try {
    const data = await rawGet(DB_PATH);
    if (!data) return blankDb();
    return normalizeDb(data);
  } catch (e) {
    console.error('loadDb:', e.message);
    return blankDb();
  }
}

async function saveDb(db) {
  await rawPut(DB_PATH, db);
}

// ---- Estados conversacionales (TTL manual de 10 min) ----

async function getState(chatId) {
  try {
    const data = await rawGet(`${STATE_PREFIX}${chatId}.json`);
    if (!data || !data.value) return null;
    if (data.exp && Date.now() > data.exp) return null;
    return data.value;
  } catch (e) {
    return null;
  }
}

async function setState(chatId, state) {
  await rawPut(`${STATE_PREFIX}${chatId}.json`, { value: state, exp: Date.now() + STATE_TTL_MS });
}

async function clearState(chatId) {
  // Sin borrado físico: el TTL manual lo invalida. Best-effort, nunca falla.
  try {
    await rawPut(`${STATE_PREFIX}${chatId}.json`, { value: null, exp: 0 });
  } catch (e) {}
}

module.exports = { loadDb, saveDb, blankDb, getState, setState, clearState };
