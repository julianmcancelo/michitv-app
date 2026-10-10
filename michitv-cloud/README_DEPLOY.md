# ☁️ MichiTV Cloud Bot —Deploy en Vercel (sin servidor propio)

Esta carpeta es un gemelo del bot de `michitv-telegram-bot/` preparado para
correr 100% en la nube: **sin PC encendida, sin IP local, sin firewall**.

## Cómo funciona

| Antes (PC) | Ahora (Vercel) |
|---|---|
| `node bot.js` con polling 24/7 | Webhook: Telegram llama a `/api/telegram` por mensaje |
| `database.json` en disco | JSON completo en **Upstash Redis** (key `michitv:db`) |
| `http://192.168.x.x:3000/api/*` | `https://tu-proyecto.vercel.app/api/*` (HTTPS público) |
| APK por archivo local (25 MB) | Link al último release de GitHub (serverless no admite 25 MB) |
| `setInterval` expiraciones | Cron `/api/cron?secret=...` (opcional) |

La app Android consulta los mismos endpoints (`status`, `activate`, `deactivate`,
`config`), así que con cambiar la URL base funciona desde cualquier red.

## Paso 1 — Subir a Vercel (5 min)

1. Commitea esta carpeta al repo (ya está incluida).
2. Entra a [vercel.com](https://vercel.com) → **Add New → Project** →
   importa `julianmcancelo/michitv-app`.
3. En **Root Directory**: poné `michitv-cloud` (Edit) → **Deploy**.
4. Anotá tu URL, ej: `https://michitv-cloud.vercel.app`.

## Paso 2 — Base de datos Redis gratis (3 min)

1. En el proyecto de Vercel: pestaña **Storage** → **Create Database** →
   **Upstash Redis** → Create (plan gratuito).
2. En **Settings → Environment Variables** verifica que existan:
   `UPSTASH_REDIS_REST_URL` y `UPSTASH_REDIS_REST_TOKEN`
   (la integración las crea solas).

## Paso 3 — Variables del bot

En **Settings → Environment Variables** agregá:

| Variable | Valor |
|---|---|
| `BOT_TOKEN` | El token de @BotFather (el mismo de `config.json`) |
| `BOT_USERNAME` | `MichitvBot` |
| `ADMIN_IDS` | `7995536641` (tu ID; sumá más con comas si querés) |
| `DEFAULT_DURATION_DAYS` | `30` |
| `CRON_SECRET` | Cualquier texto largo (para `/api/cron`) |

Luego **Redeploy** (Deployments → ⋯ → Redeploy).

## Paso 4 — Activar el webhook (1 min, desde tu PC)

```bash
cd michitv-cloud
# (opcional, solo para este script) crear .env con BOT_TOKEN=...
node set-webhook.js https://tu-proyecto.vercel.app/api/telegram
```

Verás `setWebhook: {"ok":true,...}`. Desde ese momento Telegram deja de
hablarle a tu PC y le habla a Vercel. **Apagá el `node bot.js` local.**

## Paso 5 — Probar

- En Telegram: `/start` al bot → debe responder el menú.
- En el navegador: `https://tu-proyecto.vercel.app/api/status?device=MICHI-528D`
  → JSON con la licencia.
- `/api/config` → `{"maintenance":false,...}`.

## Paso 6 — Apuntar la app a la nube

Pasame la URL de tu deploy y actualizo `TelegramActivationManager` para que
`https://tu-proyecto.vercel.app` sea el servidor principal (con los locales
como respaldo). Después se compila la APK y listo: adiós IP local,
adiós firewall, adiós PC encendida.

## Migrar tus datos actuales (opcional, 2 min)

Si querés conservar dispositivos/vouchers actuales en la nube:

```bash
# 1. Con el bot local CORRIENDO por última vez, pedí un backup:
curl http://127.0.0.1:3000/api/status?device=MICHI-528D
# 2. Subí tu database.json local a Redis (reemplaza URL/TOKEN):
curl -X POST "https://TU-UPSTASH-URL/set/michitv:db/$(python3 -c "import json,urllib.parse;print(urllib.parse.quote(open('../michitv-telegram-bot/database.json').read()))")" \
  -H "Authorization: Bearer TU-UPSTASH-TOKEN"
```

O más simple: activá de nuevo cada equipo con `/activar` (tarda 10 segundos
por TV y la nube se llena sola).

## Notas

- Los estados de conversación (esperando código, voucher, etc.) viven en Redis
  con TTL de 10 minutos: funcionan aunque Vercel use instancias distintas.
- Expiraciones: el cron de Vercel es de pago en algunos planes. Si no lo
  activás, igual todo funciona; solo no llegan avisos de "vence en 48 h".
  Para activarlo: `vercel.json` ya expone `/api/cron?secret=TU_CRON_SECRET`
  (configuralo en Vercel → Settings → Cron Jobs, 1 vez por día).
- El bot local (`michitv-telegram-bot/`) sigue intacto como respaldo: si algún
  día querés volver, corré `node bot.js` y borrá el webhook con
  `.../setWebhook?url=` (vacío).
