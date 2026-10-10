/**
 * Registra el webhook de Telegram apuntando a tu deploy de Vercel.
 * Uso: node set-webhook.js https://tu-proyecto.vercel.app/api/telegram
 * (BOT_TOKEN se lee de las variables de entorno o de .env local)
 */
const fs = require('fs');
const path = require('path');

function loadLocalEnv() {
  const p = path.join(__dirname, '.env');
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf-8').split('\n')) {
    const m = /^([A-Z_]+)=(.*)$/.exec(line.trim());
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2];
  }
}

async function main() {
  loadLocalEnv();
  const base = (process.argv[2] || '').replace(/\/$/, '');
  const token = process.env.BOT_TOKEN;
  if (!base || !token) {
    console.log('Uso: node set-webhook.js https://tu-proyecto.vercel.app/api/telegram');
    process.exit(1);
  }
  const { setupBotProfile } = require('./lib/handlers');
  // 1. Registrar webhook (corta el long-polling del bot local automáticamente)
  const res = await fetch(`https://api.telegram.org/bot${token}/setWebhook`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url: base, drop_pending_updates: false }),
  });
  console.log('setWebhook:', await res.text());
  // 2. Configurar comandos y descripción
  await setupBotProfile();
  console.log('Listo. El bot de tu PC quedará inactivo (Telegram entrega al webhook).');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
