/**
 * Lógica del bot (portada de michitv-telegram-bot/bot.js a serverless).
 * Sin fs ni polling: la DB entra/sale por callada (loadDb/saveDb) y los
 * estados conversacionales viven en Redis (sobreviven entre instancias).
 */

const { sendMessage, sendChatAction, editMessageText, answerCallbackQuery, sendRandomCatSticker, tgCall } = require('./tg');
const { loadDb, saveDb, getState, setState, clearState } = require('./store');

function cfg() {
  return {
    bot_username: process.env.BOT_USERNAME || 'MichitvBot',
    admin_ids: String(process.env.ADMIN_IDS || '')
      .split(',')
      .map((s) => Number(String(s).trim()))
      .filter((n) => Number.isFinite(n) && n > 0),
    default_duration_days: Number(process.env.DEFAULT_DURATION_DAYS || 30),
    allow_free_trial: String(process.env.ALLOW_FREE_TRIAL || 'true') !== 'false',
  };
}

const APK_RELEASE_URL = 'https://github.com/julianmcancelo/michitv-app/releases/latest';

// ---------- utilidades ----------

function generateLicenseKey(deviceCode) {
  const clean = String(deviceCode).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  const rand = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `MICHI-VIP-${clean}-${rand}`;
}

function generateVoucherCode(days) {
  const rand1 = Math.random().toString(36).substring(2, 6).toUpperCase();
  const rand2 = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `MICHI-V-${days}D-${rand1}-${rand2}`;
}

const michiPhrases = [
  '🐾 *Ronronea mientras calcula en milisegundos con sus bigotes cuánticos*',
  '😺 *Afila sus garras en el servidor y procesa a la velocidad de la luz*',
  '✨ *Mueve la cola elegantemente mientras autentica tu dispositivo*',
  '🐾 *Olfatea los paquetes de red y confirma la conexión con MichiTV*',
  '🐟 *Una sardina por cada película en 4K Ultra HD*',
  '🧶 *Juega con un ovillo de fibra óptica de 10 Gbps*',
  '🍿 *Prepara los pochoclos en el microondas con un golpe de patita*',
  '🐱 *Observa fijamente la pantalla con ojos brillantes de felicidad*',
];

function getRandomMichiPhrase() {
  return michiPhrases[Math.floor(Math.random() * michiPhrases.length)];
}

// Menú principal con botones interactivos
function mainMenu(isAdmin = false) {
  const inline_keyboard = [
    [
      { text: '📥 Descargar MichiTV APK', callback_data: 'action_download' },
      { text: '🔑 Activar Dispositivo', callback_data: 'action_activate' },
    ],
    [{ text: '📺 Mis Pantallas (Multidispositivo)', callback_data: 'action_devices' }],
    [
      { text: '🎁 7 Días VIP Gratis', callback_data: 'action_trial' },
      { text: '🎟 Canjear Voucher', callback_data: 'action_redeem' },
    ],
    [
      { text: '📺 Guía de Instalación TV', callback_data: 'action_install' },
      { text: '🔌 Plugins & Scrapers', callback_data: 'action_plugins' },
    ],
    [
      { text: '🎰 Ruleta de la Suerte', callback_data: 'action_roulette' },
      { text: '📋 Mi Estado VIP', callback_data: 'action_status' },
    ],
    [
      { text: '❓ Preguntas Frecuentes', callback_data: 'action_faq' },
      { text: '🐱 Sticker Gatuno', callback_data: 'action_sticker' },
    ],
    [{ text: '🆘 Soporte Técnico', callback_data: 'action_support' }],
  ];
  if (isAdmin) {
    inline_keyboard.push([{ text: '👑 Panel del Jefe Michi (Admin)', callback_data: 'action_admin' }]);
  }
  return { inline_keyboard };
}

function touchUser(db, userId, username) {
  if (!db.users[userId]) {
    db.users[userId] = {
      id: userId,
      username,
      first_seen: new Date().toISOString(),
      claimed_trial: false,
      last_roulette: null,
      devices: [],
    };
  } else {
    db.users[userId].username = username;
  }
  return db.users[userId];
}

// ---------- handlers (db se pasa y se guarda al final del update) ----------

async function handleDownloadApk(chatId) {
  await sendChatAction(chatId, 'typing');
  await sendRandomCatSticker(chatId);
  await sendMessage(
    chatId,
    `🐾 <b>MichiTV Cinema OS • v2.0.0 (Edición Oficial)</b> 🐾✨\n\n` +
      `📱 <b>Compatibilidad:</b> Android TV, Google TV, Fire TV Stick, Xiaomi Box, Teléfonos y Tablets.\n\n` +
      `<b>🌟 Novedades:</b>\n` +
      `• 🛠️ Modo Mantenimiento con temporizador.\n` +
      `• 🆘 Soporte técnico integrado.\n` +
      `• 📷 QR de activación también en celulares.\n` +
      `• 🎬 Reproducción más estable.\n\n` +
      `📥 <b>Descarga directa:</b>\n${APK_RELEASE_URL}`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '📥 Descargar APK Ahora', url: APK_RELEASE_URL }],
          [{ text: '📺 ¿Cómo instalar en mi TV?', callback_data: 'action_install' }],
          [{ text: '🔑 Activar mi Dispositivo', callback_data: 'action_activate' }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function showInstallationGuide(chatId) {
  const guide =
    `📺 <b>Guía Oficial de Instalación de MichiTV</b> 🐾\n\n` +
    `¡Puedes instalar MichiTV en cualquier pantalla inteligente con estos 3 métodos fáciles!\n\n` +
    `<b>MÉTODO 1: Desde tu Celular a tu TV (Recomendado) 📲➡️📺</b>\n` +
    `1. Descarga el APK desde este enlace:\n${APK_RELEASE_URL}\n` +
    `2. En tu TV y en tu celular instala la app gratuita <b>Send Files to TV</b> desde Google Play Store.\n` +
    `3. Envía el archivo APK de tu celular a la TV y dale a <i>Instalar</i>.\n\n` +
    `<b>MÉTODO 2: Con la App Downloader en TV (Firestick / Android TV) 🌐</b>\n` +
    `1. En tu TV abre la app <b>Downloader</b> (ícono naranja de AFTVnews).\n` +
    `2. Ingresa la URL de descarga de arriba.\n` +
    `3. La descarga comenzará automáticamente y pulsa <i>Instalar</i>.\n\n` +
    `<b>MÉTODO 3: Con Memoria USB / Pendrive 💾</b>\n` +
    `1. Copia el archivo APK a un Pendrive.\n` +
    `2. Conéctalo al puerto USB de tu Smart TV o TV Box.\n` +
    `3. Ábrelo con cualquier explorador de archivos (ej. <i>File Commander</i>) e instala.`;
  await sendMessage(chatId, guide, {
    reply_markup: {
      inline_keyboard: [
        [{ text: '📥 Descargar APK Ahora', url: APK_RELEASE_URL }],
        [{ text: '🔑 Ya la instalé, quiero Activar', callback_data: 'action_activate' }],
        [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
      ],
    },
  });
}

async function handleFreeTrial(db, chatId, userId, username) {
  const config = cfg();
  const user = touchUser(db, userId, username);
  if (!config.allow_free_trial || user.claimed_trial) {
    await sendMessage(
      chatId,
      `🐱 <b>¡Miau! Ya has disfrutado de tu prueba VIP de bienvenida de 7 días.</b>\n\nPuedes probar tu suerte diaria en la <b>🎰 Ruleta de la Fortuna</b> para ganar más días VIP, o canjear un voucher oficial.`,
      {
        reply_markup: {
          inline_keyboard: [
            [{ text: '🎰 Girar Ruleta de la Suerte', callback_data: 'action_roulette' }],
            [{ text: '🎟 Canjear Voucher', callback_data: 'action_redeem' }],
            [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
          ],
        },
      }
    );
    return;
  }
  const trialVoucher = generateVoucherCode(7);
  db.vouchers[trialVoucher] = {
    days: 7,
    plan: 'Prueba Gratuita VIP 7 Días 🎁',
    created_at: new Date().toISOString(),
    created_by: `Regalo a ${username}`,
    used: false,
  };
  user.claimed_trial = true;
  await sendRandomCatSticker(chatId);
  await sendMessage(
    chatId,
    `🎉 <b>¡RONRONEO DE BIENVENIDA! Aquí tienes tus 7 Días VIP Gratis</b> 🐾✨\n\n` +
      `🎁 <b>Tu Voucher de Regalo:</b>\n<code>${trialVoucher}</code>\n\n` +
      `<b>🐾 Cómo canjearlo en tu TV o Teléfono:</b>\n` +
      `1. Abre MichiTV y entra a ⚙️ <b>Ajustes → Licencia</b>.\n` +
      `2. Escribe o pega este voucher y toca <b>Activar</b>. ¡Listo! 🍿🐱`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '🔑 Canjear este Voucher en mi Dispositivo', callback_data: `redeem_direct_${trialVoucher}` }],
          [{ text: '📥 Descargar MichiTV APK', url: APK_RELEASE_URL }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function handleDailyRoulette(db, chatId, userId, username) {
  const user = touchUser(db, userId, username);
  const now = Date.now();
  const COOLDOWN_MS = 24 * 60 * 60 * 1000;
  if (user.last_roulette && now - user.last_roulette < COOLDOWN_MS) {
    const remainingHours = Math.ceil((COOLDOWN_MS - (now - user.last_roulette)) / (1000 * 60 * 60));
    await sendMessage(
      chatId,
      `⏳ <b>¡Los michis necesitan descansar sus patitas!</b>\n\nYa giraste la ruleta hoy. Vuelve en aproximadamente <b>${remainingHours} horas</b>. 🐱💤`,
      {
        reply_markup: {
          inline_keyboard: [
            [{ text: '📋 Ver Mi Estado VIP', callback_data: 'action_status' }],
            [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
          ],
        },
      }
    );
    return;
  }
  user.last_roulette = now;
  await sendMessage(chatId, `🎰 <b>¡Girando la Ruleta Felina de la Fortuna!...</b> 🐾\n<i>*Trrrr-clic-clic-clic*</i> 🌀✨`);
  await new Promise((r) => setTimeout(r, 1500));
  const roll = Math.random() * 100;
  let prizeTitle = '';
  let prizeDesc = '';
  let voucherCode = null;
  if (roll < 5) {
    voucherCode = generateVoucherCode(7);
    db.vouchers[voucherCode] = { days: 7, plan: 'Jackpot Ruleta 7 Días VIP 👑', created_at: new Date().toISOString(), used: false };
    prizeTitle = '👑 ¡¡JACKPOT CÓSMICO FELINO!! 7 DÍAS VIP GRATIS 🐾';
    prizeDesc = `¡Tus bigotes tienen magnetismo de oro!\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
  } else if (roll < 20) {
    voucherCode = generateVoucherCode(3);
    db.vouchers[voucherCode] = { days: 3, plan: 'Ruleta 3 Días VIP ⭐️', created_at: new Date().toISOString(), used: false };
    prizeTitle = '⭐️ ¡PREMIO DE PLATA! 3 DÍAS VIP GRATIS 🐱';
    prizeDesc = `Ganaste <b>3 días de membresía VIP</b>.\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
  } else if (roll < 50) {
    voucherCode = generateVoucherCode(1);
    db.vouchers[voucherCode] = { days: 1, plan: 'Ruleta 1 Día VIP 🎟', created_at: new Date().toISOString(), used: false };
    prizeTitle = '🎟 ¡PREMIO DE BRONCE! 1 DÍA VIP EXTRA 🐾';
    prizeDesc = `Te llevas <b>24 horas de acceso VIP</b>.\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
  } else if (roll < 80) {
    prizeTitle = '🐟 ¡SARDINA DORADA VIRTUAL Y BENDICIÓN GATUNA! 🐱';
    prizeDesc = 'Tus series cargarán a velocidad de la luz y sin cortes.';
  } else {
    prizeTitle = '🧶 ¡OVILLO DE LANA CÓSMICO PARA DORMIR SIESTA! 💤';
    prizeDesc = 'Vuelve mañana por más premios.';
  }
  await sendRandomCatSticker(chatId);
  await sendMessage(chatId, `🎉 <b>RESULTADO DE LA RULETA:</b>\n\n${prizeTitle}\n\n${prizeDesc}`, {
    reply_markup: {
      inline_keyboard: [
        ...(voucherCode ? [[{ text: '🔑 Activar este Voucher en mi Dispositivo', callback_data: `redeem_direct_${voucherCode}` }]] : []),
        [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
      ],
    },
  });
}

async function showPluginsInfo(chatId) {
  await sendMessage(
    chatId,
    `🔌 <b>Catálogo de Plugins & Scrapers de MichiTV</b> 🐾\n\n` +
      `🎬 <b>AnimeAV1 [ACTIVO]:</b> Anime en Audio Latino y Subtitulado.\n\n` +
      `🍿 <b>AnimeFLV One [ACTIVO]:</b> Miles de animes actualizados.\n\n` +
      `📺 <b>Caracol TV [ACTIVO]:</b> Canales en directo y novelas.\n\n` +
      `🎥 <b>FuegoCine & PelisPlus [ACTIVO]:</b> Estrenos en HD y 4K.`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '📥 Descargar MichiTV APK', url: APK_RELEASE_URL }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function showFaq(chatId) {
  await sendMessage(
    chatId,
    `❓ <b>Preguntas Frecuentes y Soporte MichiTV</b> 🐾\n\n` +
      `<b>1. No me activa la TV:</b> verifica que el celular/TV y la PC estén en el mismo WiFi, o configura la IP del servidor en Ajustes → Licencia → Ajustes avanzados.\n\n` +
      `<b>2. ¿Cómo selecciono audio Latino?</b> En ⚙️ <i>Ajustes &gt; Preferencias de Reproducción</i>.\n\n` +
      `<b>3. ¿Una película tarda o se traba?</b> Cambia el servidor de video en la ficha (recomendado: FC directo).\n\n` +
      `<b>4. ¿La app va lenta?</b> En ⚙️ <i>Ajustes &gt; Almacenamiento</i> pulsa <b>Limpiar Caché</b>.`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '🆘 Hablar con Soporte', callback_data: 'action_support' }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function showRemoteGuide(chatId) {
  await sendMessage(
    chatId,
    `🎮 <b>Guía de Control Remoto para Android TV</b> 🐾\n\n` +
      `• <b>Flechas:</b> navegar entre películas y menús.\n` +
      `• <b>OK:</b> abrir o reproducir.\n` +
      `• <b>Play/Pause:</b> pausar o reanudar.\n` +
      `• <b>Atrás:</b> volver o cerrar el reproductor.`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '📥 Descargar APK', url: APK_RELEASE_URL }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function processCheckStatus(db, chatId, rawCode) {
  const code = String(rawCode || '').trim().toUpperCase();
  if (!code.startsWith('MICHI-')) {
    await sendMessage(chatId, `😿 <b>Formato no reconocido.</b>\nEl código debe comenzar con <code>MICHI-</code> (ej: <code>MICHI-78B2</code>).`);
    return;
  }
  const dev = db.devices[code];
  if (!dev) {
    await sendMessage(
      chatId,
      `🔍 <b>Dispositivo <code>${code}</code> no encontrado.</b>\n¿Deseas activarlo?`,
      {
        reply_markup: {
          inline_keyboard: [
            [{ text: '🎁 Activar 7 Días VIP Gratis', callback_data: 'action_trial' }],
            [{ text: '🔑 Activar Dispositivo', callback_data: 'action_activate' }],
            [{ text: '⬅️ Menú', callback_data: 'action_menu' }],
          ],
        },
      }
    );
    return;
  }
  const expDate = new Date(dev.expires_at);
  const now = new Date();
  const isExpired = expDate <= now;
  const daysLeft = Math.max(0, Math.ceil((expDate - now) / (1000 * 60 * 60 * 24)));
  await sendMessage(
    chatId,
    `📋 <b>Ficha de Licencia MichiTV</b> 🐾\n\n` +
      `📱 <b>Dispositivo:</b> <code>${dev.device_code}</code>\n` +
      `⭐️ <b>Estado:</b> ${isExpired ? '🔴 EXPIRADO' : '🟢 ACTIVO • VIP'}\n` +
      `🏷 <b>Plan:</b> ${dev.plan}\n` +
      `📅 <b>Vence:</b> ${expDate.toLocaleDateString('es-ES')} (${daysLeft} días)\n` +
      `🔑 <b>Clave:</b> <code>${dev.license_key}</code>\n` +
      `👤 <b>Vinculado a:</b> ${dev.tg_username || 'anónimo'}`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '🎟 Extender con Voucher', callback_data: 'action_redeem' }],
          [{ text: '⬅️ Menú', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function processActivation(db, chatId, userId, username, rawDeviceCode) {
  const config = cfg();
  let code = String(rawDeviceCode || '').trim().toUpperCase();
  if (!code.startsWith('MICHI-')) {
    if (code.length === 4) code = `MICHI-${code}`;
    else {
      await sendMessage(chatId, `❌ <b>Formato de código inválido.</b>\nIngresa tu código (Ej: <code>MICHI-78B2</code>) o PIN de 4 dígitos. Búscalo en ⚙️ <b>Ajustes</b> de la aplicación.`);
      return;
    }
  }
  if (db.devices[code]) {
    const existingDev = db.devices[code];
    const isExpired = new Date(existingDev.expires_at) <= new Date();
    if (!isExpired && existingDev.activated) {
      if (existingDev.tg_user_id !== userId) {
        await sendMessage(chatId, `⚠️ <b>Este dispositivo ya está vinculado a otra cuenta (${existingDev.tg_username}).</b>\nSi crees que es un error, contacta a soporte.`);
      } else {
        await sendMessage(chatId, `🐱 <b>¡Miau! Tu dispositivo <code>${code}</code> ya está activado y vigente.</b>\nVence el: ${new Date(existingDev.expires_at).toLocaleDateString('es-ES')}`, {
          reply_markup: { inline_keyboard: [[{ text: '📋 Ver Estado', callback_data: `status_${code}` }], [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }]] },
        });
      }
      return;
    }
  }
  const licenseKey = generateLicenseKey(code);
  const expires = new Date();
  expires.setDate(expires.getDate() + config.default_duration_days);
  db.devices[code] = {
    device_code: code,
    license_key: licenseKey,
    activated: true,
    activated_at: new Date().toISOString(),
    expires_at: expires.toISOString(),
    plan: 'VIP MichiTV Pass',
    tg_user_id: userId,
    tg_username: username,
  };
  touchUser(db, userId, username);
  if (!db.users[userId].devices.includes(code)) db.users[userId].devices.push(code);
  await sendRandomCatSticker(chatId);
  const formattedExp = expires.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
  await sendMessage(
    chatId,
    `🎉 <b>¡Dispositivo Activado!</b> 🐾✨\n\n` +
      `📱 <b>Dispositivo:</b> <code>${code}</code>\n` +
      `⭐️ <b>Membresía:</b> Premium MichiTV VIP\n` +
      `📅 <b>Válido hasta:</b> ${formattedExp} (${config.default_duration_days} días)\n\n` +
      `⚡️ <b>Tu pantalla se actualizará sola</b> en unos segundos. 🍿🐱\n\n` +
      `🔑 <i>Clave de respaldo:</i> <code>${licenseKey}</code>`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '📋 Consultar Estado', callback_data: `status_${code}` }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function processRedeemStep1(db, chatId, voucherCode) {
  const v = String(voucherCode || '').trim().toUpperCase();
  if (!db.vouchers[v]) {
    await sendMessage(chatId, `😿 <b>Miau... Voucher no encontrado o inválido.</b>`);
    return null;
  }
  if (db.vouchers[v].used) {
    await sendMessage(chatId, `⚠️ <b>Este voucher ya fue utilizado.</b>`);
    return null;
  }
  await setState(chatId, { type: 'WAITING_VOUCHER_DEVICE', voucher: v });
  await sendMessage(chatId, `😻 <b>¡Voucher verificado!</b> (${db.vouchers[v].days} días VIP)\n\nAhora envía el <b>Código de Dispositivo</b> (Ej: <code>MICHI-78B2</code>):`);
  return v;
}

async function processRedeemStep2(db, chatId, userId, username, voucherCode, rawDevice) {
  const v = String(voucherCode || '').trim().toUpperCase();
  const code = String(rawDevice || '').trim().toUpperCase();
  if (!db.vouchers[v] || db.vouchers[v].used) {
    await sendMessage(chatId, `❌ Voucher inválido o ya utilizado.`);
    return;
  }
  if (!code.startsWith('MICHI-')) {
    await sendMessage(chatId, `❌ El formato del dispositivo debe ser <code>MICHI-XXXX</code>.`);
    return;
  }
  const voucher = db.vouchers[v];
  const licenseKey = generateLicenseKey(code);
  const expires = new Date();
  expires.setDate(expires.getDate() + voucher.days);
  db.devices[code] = {
    device_code: code,
    license_key: licenseKey,
    activated: true,
    activated_at: new Date().toISOString(),
    expires_at: expires.toISOString(),
    plan: voucher.plan || `VIP ${voucher.days} Días`,
    tg_user_id: userId,
    tg_username: username,
    redeemed_voucher: v,
  };
  voucher.used = true;
  voucher.used_at = new Date().toISOString();
  voucher.used_by = username;
  touchUser(db, userId, username);
  if (!db.users[userId].devices.includes(code)) db.users[userId].devices.push(code);
  await sendRandomCatSticker(chatId);
  await sendMessage(
    chatId,
    `🎉 <b>¡VOUCHER CANJEADO!</b> 🎟🐾\n\n` +
      `📱 <b>Dispositivo:</b> <code>${code}</code>\n` +
      `⏱ <b>Días agregados:</b> +${voucher.days}\n` +
      `📅 <b>Vence:</b> ${expires.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' })}\n` +
      `🔑 <b>Clave VIP:</b> <code>${licenseKey}</code>`,
    {
      reply_markup: {
        inline_keyboard: [
          [{ text: '📋 Ver Estado', callback_data: `status_${code}` }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function showUserDevices(db, chatId, userId, username) {
  const user = touchUser(db, userId, username);
  if (!user.devices || user.devices.length === 0) {
    await sendMessage(chatId, `😿 <b>Aún no tienes televisores ni celulares vinculados.</b>`, {
      reply_markup: {
        inline_keyboard: [
          [{ text: '🔑 Activar Nuevo Dispositivo', callback_data: 'action_activate' }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    });
    return;
  }
  let msg = `📺 <b>Mis Pantallas (Multidispositivo)</b> 🐾\n\n`;
  const keyboard = [];
  user.devices.forEach((code, index) => {
    const dev = db.devices[code];
    if (dev) {
      const isExpired = new Date(dev.expires_at) <= new Date();
      msg += `<b>${index + 1}. Dispositivo:</b> <code>${code}</code> ${isExpired ? '🔴' : '🟢'}\n   Vence: ${new Date(dev.expires_at).toLocaleDateString('es-ES')}\n`;
      keyboard.push([{ text: `Ver ${code}`, callback_data: `status_${code}` }]);
    }
  });
  keyboard.push([{ text: '🔑 Añadir Pantalla', callback_data: 'action_activate' }]);
  keyboard.push([{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }]);
  await sendMessage(chatId, msg, { reply_markup: { inline_keyboard: keyboard } });
}

async function showAdminPanel(db, chatId) {
  const totalDevices = Object.keys(db.devices).length;
  const totalVouchers = Object.keys(db.vouchers).length;
  const usedVouchers = Object.values(db.vouchers).filter((v) => v.used).length;
  const totalUsers = Object.keys(db.users).length;
  await sendMessage(
    chatId,
    `👑 <b>Panel del Jefe Michi (Admin)</b> 🐾\n\n` +
      `👥 <b>Usuarios:</b> ${totalUsers}\n` +
      `📱 <b>Dispositivos:</b> ${totalDevices}\n` +
      `🎟 <b>Vouchers:</b> ${totalVouchers} (${usedVouchers} usados)`,
    {
      reply_markup: {
        inline_keyboard: [
          [
            { text: '🎟 Voucher 30D', callback_data: 'admin_v30' },
            { text: '🎟 Voucher 90D', callback_data: 'admin_v90' },
          ],
          [{ text: '🎟 Voucher 1 Año', callback_data: 'admin_v365' }],
          [{ text: '⬅️ Menú Principal', callback_data: 'action_menu' }],
        ],
      },
    }
  );
}

async function handleMaintenanceCommand(db, chatId, userId, text, isAdmin) {
  if (!isAdmin) {
    await sendMessage(chatId, `🔒 <b>Solo el Jefe Michi puede usar este comando.</b> 🐾`);
    return;
  }
  const args = String(text || '').split(/\s+/).slice(1);
  const sub = (args[0] || '').toLowerCase();
  if (sub === 'off') {
    db.config.maintenance = false;
    db.config.maintenance_until = null;
    await sendMessage(chatId, `✅ <b>Modo mantenimiento APAGADO.</b>\nLas apps volverán solas en segundos. 🐾`);
    return;
  }
  if (sub === 'estado' || sub === 'status') {
    const m = db.config.maintenance;
    const until = db.config.maintenance_until;
    const left = m && until ? Math.max(0, Math.ceil((until - Date.now()) / 60000)) : null;
    await sendMessage(
      chatId,
      `🛠️ <b>Mantenimiento:</b> ${m ? '🟡 ACTIVO' : '🟢 APAGADO'}\n` +
        (m ? `💬 ${db.config.maintenance_message}\n` : '') +
        (left !== null ? `⏱ Quedan aprox. ${left} minutos.\n` : '') +
        `\n<code>/mantenimiento on 30 Estamos actualizando</code>\n<code>/mantenimiento off</code>`
    );
    return;
  }
  if (sub === 'on') {
    let minutes = null;
    let msgStart = 1;
    if (args[1] && /^\d+$/.test(args[1])) {
      minutes = parseInt(args[1], 10);
      msgStart = 2;
    }
    const message = args.slice(msgStart).join(' ').trim() || 'Estamos realizando mejoras. Volvemos enseguida.';
    db.config.maintenance = true;
    db.config.maintenance_message = message;
    db.config.maintenance_until = minutes ? Date.now() + minutes * 60000 : null;
    await sendMessage(
      chatId,
      `🛠️ <b>Mantenimiento ACTIVADO.</b> 🐾\n\n💬 ${message}\n` +
        (minutes ? `⏱ ${minutes} minutos (se apaga solo).\n` : `⏱ Indefinido (<code>/mantenimiento off</code> para apagar).\n`)
    );
    return;
  }
  await sendMessage(
    chatId,
    `🛠️ <b>Uso:</b>\n<code>/mantenimiento on 30 Estamos actualizando</code>\n<code>/mantenimiento on Mensaje</code>\n<code>/mantenimiento off</code>\n<code>/mantenimiento estado</code>`
  );
}

async function handleSupportMessage(db, chatId, userId, username, text) {
  const ticketId = Date.now().toString(36).toUpperCase().slice(-6);
  db.support_tickets[ticketId] = {
    id: ticketId,
    user_chat_id: chatId,
    user_id: userId,
    username,
    message: String(text || ''),
    created_at: new Date().toISOString(),
    status: 'open',
  };
  await sendMessage(chatId, `✅ <b>¡Ticket recibido!</b> 🐾\nTe responderemos por este chat.\n🎫 Ticket: <code>${ticketId}</code>`);
  const config = cfg();
  const admins = config.admin_ids.length > 0 ? config.admin_ids : [chatId];
  const adminMsg =
    `🆘 <b>Nuevo Ticket de Soporte</b>\n\n👤 Usuario: ${username}\n🆔 (ID: ${ticketId})\n\n` +
    `💬 Mensaje: "${text}"\n\n<i>Responde con Reply a ESTE mensaje y le llegará al cliente.</i>`;
  for (const adminId of admins) {
    try {
      await sendMessage(adminId, adminMsg);
    } catch (e) {}
  }
}

async function handleAdminReply(db, msg) {
  const reply = msg.reply_to_message;
  if (!reply || !reply.text) return false;
  if (reply.text.indexOf('Nuevo Ticket de Soporte') === -1) return false;
  const m = /ID:\s*([A-Z0-9]+)/.exec(reply.text);
  if (!m) return false;
  const ticket = db.support_tickets[m[1]];
  if (!ticket) return false;
  const answer = (msg.text || '').trim();
  if (!answer) return true;
  ticket.status = 'answered';
  try {
    await sendMessage(ticket.user_chat_id, `🐾 <b>Respuesta del equipo MichiTV</b> (🎫 ${ticket.id}):\n\n${answer}`);
    await sendMessage(msg.chat.id, `✅ <b>Respuesta enviada al cliente.</b> 🐾`);
  } catch (e) {
    await sendMessage(msg.chat.id, `⚠️ No pude entregar la respuesta: ${e.message}`);
  }
  return true;
}

// ---------- entrada principal ----------

async function handleMessage(db, msg) {
  if (!msg.text) return;
  const config = cfg();
  const chatId = msg.chat.id;
  const userId = msg.from.id;
  const username = msg.from.username ? `@${msg.from.username}` : msg.from.first_name || 'Humano Amigo';
  const text = msg.text.trim();
  const isAdmin = config.admin_ids.includes(userId) || config.admin_ids.length === 0;

  touchUser(db, userId, username);

  if (msg.reply_to_message && isAdmin) {
    if (await handleAdminReply(db, msg)) return;
  }

  if (text.startsWith('/start ') && text.length > 7) {
    const payload = text.substring(7).trim();
    let targetCode = null;
    if (payload.startsWith('activar_')) targetCode = payload.replace('activar_', '').trim();
    else if (payload.startsWith('MICHI-')) targetCode = payload.trim();
    if (targetCode && targetCode.startsWith('MICHI-')) {
      await sendRandomCatSticker(chatId);
      await sendMessage(chatId, `😻 <b>¡Detecté tu código escaneado:</b> <code>${targetCode}</code>\n\n<i>${getRandomMichiPhrase()}</i>`);
      await processActivation(db, chatId, userId, username, targetCode);
      return;
    }
  }

  const state = await getState(chatId);
  if (state) {
    await clearState(chatId);
    if (state.type === 'WAITING_DEVICE_CODE') {
      await processActivation(db, chatId, userId, username, text);
      return;
    } else if (state.type === 'WAITING_VOUCHER_CODE') {
      await processRedeemStep1(db, chatId, text);
      return;
    } else if (state.type === 'WAITING_VOUCHER_DEVICE') {
      await processRedeemStep2(db, chatId, userId, username, state.voucher, text);
      return;
    } else if (state.type === 'WAITING_STATUS_CODE') {
      await processCheckStatus(db, chatId, text);
      return;
    } else if (state.type === 'WAITING_SUPPORT_MSG') {
      await handleSupportMessage(db, chatId, userId, username, text);
      return;
    }
  }

  if (text === '/start') {
    await sendRandomCatSticker(chatId);
    await sendMessage(
      chatId,
      `🐾 <b>¡Hola, ${username}! Bienvenido a MichiBot</b> 🐱✨\n\n` +
        `Soy el guardián de <b>MichiTV Cinema OS</b>.\n\n<i>${getRandomMichiPhrase()}</i>\n\n🚀 <b>¿Qué deseas hacer?</b> 👇`,
      { reply_markup: mainMenu(isAdmin) }
    );
    return;
  }
  if (text === '/descargar' || text === '/apk' || text === '/download') {
    await handleDownloadApk(chatId);
    return;
  }
  if (text === '/instalar' || text === '/guia') {
    await showInstallationGuide(chatId);
    return;
  }
  if (text === '/prueba' || text === '/demo' || text === '/trial') {
    await handleFreeTrial(db, chatId, userId, username);
    return;
  }
  if (text === '/suerte' || text === '/ruleta') {
    await handleDailyRoulette(db, chatId, userId, username);
    return;
  }
  if (text === '/plugins' || text === '/canales') {
    await showPluginsInfo(chatId);
    return;
  }
  if (text === '/faq' || text === '/soporte') {
    await showFaq(chatId);
    return;
  }
  if (text === '/soporte_tecnico') {
    await setState(chatId, { type: 'WAITING_SUPPORT_MSG' });
    await sendMessage(chatId, `🆘 <b>Soporte Técnico MichiTV</b> 🐾\n\nContame tu problema en un solo mensaje:`);
    return;
  }
  if (text === '/mando' || text === '/control' || text === '/atajos') {
    await showRemoteGuide(chatId);
    return;
  }
  if (text === '/michi' || text === '/sticker') {
    await sendRandomCatSticker(chatId);
    await sendMessage(chatId, `🐱 <i>¡Miau! Bendición gatuna para tu día.</i> 🐾`, { reply_markup: mainMenu(isAdmin) });
    return;
  }
  if (text.startsWith('/activar')) {
    const parts = text.split(/\s+/);
    if (parts.length < 2) {
      await setState(chatId, { type: 'WAITING_DEVICE_CODE' });
      await sendMessage(chatId, `🐱 <b>Envíame tu Código de Dispositivo</b> (Ej: <code>MICHI-78B2</code>, está en ⚙️ <b>Ajustes</b> de MichiTV):`);
      return;
    }
    await processActivation(db, chatId, userId, username, parts[1]);
    return;
  }
  if (text === '/dispositivos' || text === '/pantallas') {
    await showUserDevices(db, chatId, userId, username);
    return;
  }
  if (text.startsWith('/estado')) {
    const parts = text.split(/\s+/);
    if (parts.length < 2) {
      await setState(chatId, { type: 'WAITING_STATUS_CODE' });
      await sendMessage(chatId, `🔍 <b>Envíame tu código</b> (Ej: <code>MICHI-78B2</code>):`);
      return;
    }
    await processCheckStatus(db, chatId, parts[1]);
    return;
  }
  if (text.startsWith('/canjear')) {
    const parts = text.split(/\s+/);
    if (parts.length < 3) {
      await sendMessage(chatId, `💡 <b>Formato:</b>\n<code>/canjear &lt;VOUCHER&gt; &lt;DISPOSITIVO&gt;</code>`);
      return;
    }
    await processRedeemStep2(db, chatId, userId, username, parts[1], parts[2]);
    return;
  }
  if (text.startsWith('/mantenimiento') || text.startsWith('/maintenance')) {
    await handleMaintenanceCommand(db, chatId, userId, text, isAdmin);
    return;
  }
  if (text === '/admin' && isAdmin) {
    await showAdminPanel(db, chatId);
    return;
  }
  if (text === '/ayuda' || text === '/help') {
    await showInstallationGuide(chatId);
    return;
  }
  await sendMessage(chatId, `🐱 <i>*Miau curioso*</i>... Elige una opción del menú:`, { reply_markup: mainMenu(isAdmin) });
}

async function handleCallback(db, query) {
  const chatId = query.message.chat.id;
  const messageId = query.message.message_id;
  const data = query.data;
  const userId = query.from.id;
  const username = query.from.username ? `@${query.from.username}` : query.from.first_name || 'Humano';
  const config = cfg();
  const isAdmin = config.admin_ids.includes(userId);
  await answerCallbackQuery(query.id);

  if (data === 'action_menu') {
    await editMessageText(chatId, messageId, `🐾 <b>Menú Principal de MichiTV</b>`, { reply_markup: mainMenu(isAdmin) });
  } else if (data === 'action_download') {
    await handleDownloadApk(chatId);
  } else if (data === 'action_install') {
    await showInstallationGuide(chatId);
  } else if (data === 'action_trial') {
    await handleFreeTrial(db, chatId, userId, username);
  } else if (data === 'action_roulette') {
    await handleDailyRoulette(db, chatId, userId, username);
  } else if (data === 'action_plugins') {
    await showPluginsInfo(chatId);
  } else if (data === 'action_faq') {
    await showFaq(chatId);
  } else if (data === 'action_mando') {
    await showRemoteGuide(chatId);
  } else if (data === 'action_sticker') {
    await sendRandomCatSticker(chatId);
  } else if (data === 'action_activate') {
    await setState(chatId, { type: 'WAITING_DEVICE_CODE' });
    await sendMessage(chatId, `🐱 <b>Envía tu Código de Dispositivo</b> (Ej: <code>MICHI-78B2</code>):`);
  } else if (data === 'action_devices') {
    await showUserDevices(db, chatId, userId, username);
  } else if (data === 'action_redeem') {
    await setState(chatId, { type: 'WAITING_VOUCHER_CODE' });
    await sendMessage(chatId, `🎟 <b>Envía tu Código Voucher</b> (Ej: <code>MICHI-V-30D-XXXX-YYYY</code>):`);
  } else if (data === 'action_status') {
    await setState(chatId, { type: 'WAITING_STATUS_CODE' });
    await sendMessage(chatId, `🔍 <b>Envía tu código de dispositivo</b> (Ej: <code>MICHI-78B2</code>):`);
  } else if (data === 'action_support') {
    await setState(chatId, { type: 'WAITING_SUPPORT_MSG' });
    await sendMessage(chatId, `🆘 <b>Soporte Técnico MichiTV</b> 🐾\n\nContame tu problema en un solo mensaje (qué falla, en qué pantalla y en qué dispositivo):`);
  } else if (data === 'action_admin' && isAdmin) {
    await showAdminPanel(db, chatId);
  } else if (data === 'admin_v30' && isAdmin) {
    const v = generateVoucherCode(30);
    db.vouchers[v] = { days: 30, plan: 'VIP 30 Días', created_at: new Date().toISOString(), used: false };
    await sendRandomCatSticker(chatId);
    await sendMessage(chatId, `🎟 <b>Voucher 30 Días:</b>\n<code>${v}</code>`);
  } else if (data === 'admin_v90' && isAdmin) {
    const v = generateVoucherCode(90);
    db.vouchers[v] = { days: 90, plan: 'VIP 90 Días', created_at: new Date().toISOString(), used: false };
    await sendRandomCatSticker(chatId);
    await sendMessage(chatId, `🎟 <b>Voucher 90 Días:</b>\n<code>${v}</code>`);
  } else if (data === 'admin_v365' && isAdmin) {
    const v = generateVoucherCode(365);
    db.vouchers[v] = { days: 365, plan: 'VIP Anual', created_at: new Date().toISOString(), used: false };
    await sendRandomCatSticker(chatId);
    await sendMessage(chatId, `🎟 <b>Voucher Anual:</b>\n<code>${v}</code>`);
  } else if (data.startsWith('redeem_direct_')) {
    const vCode = data.replace('redeem_direct_', '');
    await setState(chatId, { type: 'WAITING_VOUCHER_DEVICE', voucher: vCode });
    await sendMessage(chatId, `🐱 <b>Ahora envía el código de tu pantalla</b> (Ej: <code>MICHI-78B2</code>):`);
  } else if (data.startsWith('status_')) {
    await processCheckStatus(db, chatId, data.replace('status_', ''));
  }
}

async function handleUpdate(update) {
  const db = await loadDb();
  try {
    if (update.message) await handleMessage(db, update.message);
    else if (update.callback_query) await handleCallback(db, update.callback_query);
  } finally {
    await saveDb(db);
  }
}

async function setupBotProfile() {
  const config = cfg();
  await tgCall('setMyCommands', {
    commands: [
      { command: 'start', description: '🐾 Menú principal gatuno' },
      { command: 'descargar', description: '📥 Descargar APK de MichiTV' },
      { command: 'activar', description: '🔑 Activar Smart TV o Celular' },
      { command: 'prueba', description: '🎁 7 Días VIP gratis' },
      { command: 'instalar', description: '📺 Guía de instalación en TV' },
      { command: 'plugins', description: '🔌 Plugins y scrapers' },
      { command: 'suerte', description: '🎰 Ruleta diaria' },
      { command: 'estado', description: '📋 Vigencia de licencia' },
      { command: 'soporte', description: '🆘 Soporte técnico' },
      { command: 'mantenimiento', description: '🛠️ Mantenimiento (admin)' },
      { command: 'michi', description: '🐱 Sticker gatuno' },
      { command: 'faq', description: '❓ Ayuda' },
      { command: 'mando', description: '🎮 Control remoto' },
      { command: 'canjear', description: '🎟 Canjear voucher VIP' },
      { command: 'admin', description: '👑 Panel del Jefe Michi' },
    ],
  });
  await tgCall('setMyDescription', {
    description: '🐾 ¡Miau! Soy MichiBot, guardián de MichiTV Cinema OS.\n\nDescarga la app, activa tu Smart TV con QR, reclama 7 días VIP y recibe stickers de gatitos.',
  });
  console.log('Perfil de @' + config.bot_username + ' configurado.');
}

async function checkExpirations() {
  const db = await loadDb();
  const now = new Date();
  const IN_2_DAYS = new Date(now.getTime() + 48 * 60 * 60 * 1000);
  let changed = false;
  for (const code in db.devices) {
    const dev = db.devices[code];
    if (!dev.tg_user_id || dev.tg_user_id === 0) continue;
    const expiresAt = new Date(dev.expires_at);
    if (expiresAt > now && expiresAt <= IN_2_DAYS && !dev.notified_expiration) {
      try {
        await sendMessage(dev.tg_user_id, `⚠️ <b>¡Alerta Gatuna!</b> 🐾\n\nTu dispositivo <code>${dev.device_code}</code> vence el ${expiresAt.toLocaleDateString('es-ES')}.\n\n¡Canjea un voucher para seguir disfrutando!`, {
          reply_markup: { inline_keyboard: [[{ text: '🎟 Canjear Voucher', callback_data: 'action_redeem' }]] },
        });
        dev.notified_expiration = true;
        changed = true;
      } catch (e) {
        console.error('expiración:', e.message);
      }
    }
  }
  if (changed) await saveDb(db);
  return { checked: Object.keys(db.devices).length };
}

module.exports = { handleUpdate, setupBotProfile, checkExpirations, APK_RELEASE_URL };
