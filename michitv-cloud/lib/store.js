/**
 * Persistencia sobre Vercel Blob (SDK oficial, auth OIDC automática).
 * Toda la base como un único JSON + un blob por estado conversacional.
 */

const { put, list, del, get } = require('@vercel/blob');

const DB_PATH = 'michitv/db.json';
const STATE_PREFIX = 'michitv/state-';
const STATE_TTL_MS = 10 * 60 * 1000;

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

async function findBlob(pathname) {
  const { blobs } = await list({ prefix: pathname, limit: 5 });
  return blobs.find((b) => b.pathname === pathname) || blobs[0] || null;
}

async function downloadJson(pathname) {
  try {
    const res = await get(pathname, { access: 'private', cacheControlMaxAge: 0 });
    if (!res || res.ok === false) return null;
    if (typeof res.json === 'function') return await res.json();
    const text = typeof res.text === 'function' ? await res.text() : String(res);
    return JSON.parse(text);
  } catch (e) {
    return null; // incluye BlobNotFound: arrancar en blanco
  }
}

async function loadDb() {
  try {
    const data = await downloadJson(DB_PATH);
    if (!data) return blankDb();
    return normalizeDb(data);
  } catch (e) {
    console.error('loadDb:', e.message);
    return blankDb();
  }
}

async function saveDb(db) {
  await put(DB_PATH, JSON.stringify(db), {
    access: 'private',
    addRandomSuffix: false,
    allowOverwrite: true,
    contentType: 'application/json',
  });
}

// ---- Estados conversacionales (TTL manual de 10 min) ----

async function getState(chatId) {
  try {
    const data = await downloadJson(`${STATE_PREFIX}${chatId}.json`);
    if (!data || !data.value) return null;
    if (data.exp && Date.now() > data.exp) {
      try {
        const found = await findBlob(`${STATE_PREFIX}${chatId}.json`);
        if (found) await del(found.url);
      } catch (e) {}
      return null;
    }
    return data.value;
  } catch (e) {
    return null;
  }
}

async function setState(chatId, state) {
  await put(`${STATE_PREFIX}${chatId}.json`, JSON.stringify({ value: state, exp: Date.now() + STATE_TTL_MS }), {
    access: 'private',
    addRandomSuffix: false,
    allowOverwrite: true,
    contentType: 'application/json',
  });
}

async function clearState(chatId) {
  try {
    const found = await findBlob(`${STATE_PREFIX}${chatId}.json`);
    if (found) await del(found.url);
  } catch (e) {}
}

module.exports = { loadDb, saveDb, blankDb, getState, setState, clearState };
