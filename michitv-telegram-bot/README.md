# 🐾 MichiTV Telegram Licensing Bot

Bot interactivo de Telegram y servidor local para la gestión automática de licencias, activación de dispositivos Smart TV/móviles y generación de vouchers para **MichiTV**.

---

## 🚀 Pasos para Ponerlo en Marcha (en 2 minutos)

### 1. Crear tu Bot en Telegram
1. Abre Telegram y busca a **[@BotFather](https://t.me/BotFather)**.
2. Envía el comando `/newbot`.
3. Elige un nombre visible para tu bot (ej: `MichiTV Activador`).
4. Elige un usuario que termine en `bot` (ej: `MichiTVOficial_Bot`).
5. `@BotFather` te responderá con tu **Token de Acceso HTTP API** (se ve como `7123456789:ABCDefGHiJklMnOp...`).

### 2. Configurar el Token
Abre el archivo `config.json` en esta carpeta y reemplaza `"TU_TELEGRAM_BOT_TOKEN_AQUI"` por el token que te dio `@BotFather`:

```json
{
  "bot_token": "7123456789:ABCDefGHiJklMnOp...",
  "admin_ids": [],
  "default_duration_days": 30,
  "server_port": 3000,
  "allow_free_trial": true
}
```

### 3. Iniciar el Bot
Abre una terminal en esta carpeta y ejecuta:
```bash
node bot.js
```
*(No requiere instalar paquetes adicionales con npm, utiliza Node.js nativo)*.

---

## 🎮 Comandos y Funcionalidades del Bot

### Para los Usuarios:
* `/start`: Muestra el menú interactivo con botones táctiles para activar, consultar estado o canjear.
* `/activar <CODIGO>`: Activa un dispositivo (ejemplo: `/activar MICHI-78B2`) y le entrega al usuario una clave de membresía VIP personalizada.
* `/canjear <VOUCHER> <CODIGO>`: Canjea un voucher prepago o de regalo para activar un dispositivo por X días.
* `/estado <CODIGO>`: Muestra los días restantes de membresía y estado de la licencia.
* `/ayuda`: Guía paso a paso sobre cómo usar la app.

### Para el Administrador:
* `/admin`: Abre el panel de control con estadísticas en vivo de dispositivos y vouchers.
* `/voucher <DIAS>`: Crea un código de voucher de un solo uso (ejemplo: `/voucher 30` o `/voucher 365`).

---

## 🌐 API HTTP Embebida
El bot incluye un servidor HTTP ligero (puerto 3000 por defecto) para consultar el estado de cualquier dispositivo mediante GET:
* `http://localhost:3000/api/status?device=MICHI-78B2`

Respuesta JSON:
```json
{
  "found": true,
  "deviceCode": "MICHI-78B2",
  "isActivated": true,
  "planName": "Membresía Premium MichiTV VIP 🐾",
  "expiresAt": "2026-11-08T20:45:00.000Z",
  "licenseKey": "MICHI-VIP-MICHI78B2-A9F2"
}
```
