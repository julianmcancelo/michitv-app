/**
 * 🐾 MichiTV Official Telegram Licensing & Distribution Bot - "MichiBot IA" 🐾
 * 
 * Bot felino inteligente, puntual y sagaz con superpoderes:
 * - Descarga directa del APK de MichiTV Cinema v2.0.0 vía Telegram y HTTP.
 * - Activación instantánea de Smart TV mediante Código QR y Celulares mediante deep-link directo.
 * - Prueba VIP gratuita de bienvenida de 7 días.
 * - Ruleta diaria de la suerte gatuna con premios y vouchers VIP.
 * - Catálogo de plugins y scrapers de video.
 * - Guía completa de instalación para Android TV / Fire TV / TV Box.
 * - Envío de stickers animados oficiales de gatitos.
 * - Panel del Administrador con estadísticas y creación de vouchers.
 * 
 * 100% Cero dependencias externas (Fetch nativo de Node.js + multipart FormData nativo).
 */

const fs = require('fs');
const path = require('path');
const http = require('http');

// Rutas de archivos
const CONFIG_PATH = path.join(__dirname, 'config.json');
const DB_PATH = path.join(__dirname, 'database.json');
const DOWNLOADS_DIR = path.join(__dirname, 'downloads');
const PRIMARY_APK_PATH = path.join(DOWNLOADS_DIR, 'MichiTV-Cinema-v2.0.0.apk');
const FALLBACK_APK_PATH = path.resolve(__dirname, '../kino-tv-app/app/build/outputs/apk/debug/app-debug.apk');

// Asegurar carpeta downloads
if (!fs.existsSync(DOWNLOADS_DIR)) {
    fs.mkdirSync(DOWNLOADS_DIR, { recursive: true });
}

// Sincronizar APK si existe en el build de Android
function getApkPath() {
    if (fs.existsSync(PRIMARY_APK_PATH)) {
        return PRIMARY_APK_PATH;
    }
    if (fs.existsSync(FALLBACK_APK_PATH)) {
        try {
            fs.copyFileSync(FALLBACK_APK_PATH, PRIMARY_APK_PATH);
            return PRIMARY_APK_PATH;
        } catch (e) {
            return FALLBACK_APK_PATH;
        }
    }
    return null;
}

// Cargar configuración
let config = {
    bot_token: "8955872733:AAFbRGfbBRgjTrIk7Yor8xpu_NxxpIygdk8",
    bot_username: "MichitvBot",
    admin_ids: [],
    default_duration_days: 30,
    server_port: 3000,
    allow_free_trial: true
};

if (fs.existsSync(CONFIG_PATH)) {
    try {
        config = { ...config, ...JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf-8')) };
    } catch (e) {
        console.error("Error leyendo config.json:", e.message);
    }
}

// Cargar base de datos local
let db = {
    devices: {},
    vouchers: {},
    users: {},
    config: {
        maintenance: false,
        maintenance_message: "Estamos mejorando MichiTV. Volvemos en minutos.",
        maintenance_until: null
    },
    support_tickets: {}
};

function loadDB() {
    if (fs.existsSync(DB_PATH)) {
        try {
            db = JSON.parse(fs.readFileSync(DB_PATH, 'utf-8'));
            if (!db.devices) db.devices = {};
            if (!db.vouchers) db.vouchers = {};
            if (!db.users) db.users = {};
            if (!db.config) db.config = {
                maintenance: false,
                maintenance_message: "Estamos mejorando MichiTV. Volvemos en minutos.",
                maintenance_until: null
            };
            if (!db.support_tickets) db.support_tickets = {};
        } catch (e) {
            console.error("Error cargando database.json:", e.message);
        }
    } else {
        saveDB();
    }
}

function saveDB() {
    try {
        fs.writeFileSync(DB_PATH, JSON.stringify(db, null, 2), 'utf-8');
    } catch (e) {
        console.error("Error guardando database.json:", e.message);
    }
}

loadDB();

// Estados en memoria para interacción conversacional
const userStates = {};

// Helpers de Telegram API
const API_BASE = () => `https://api.telegram.org/bot${config.bot_token}`;

async function tgCall(method, body = {}) {
    const res = await fetch(`${API_BASE()}/${method}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const data = await res.json();
    if (!data.ok) {
        throw new Error(data.description || "Error en Telegram API");
    }
    return data.result;
}

async function sendMessage(chatId, text, extra = {}) {
    return tgCall('sendMessage', {
        chat_id: chatId,
        text: text,
        parse_mode: 'HTML',
        ...extra
    });
}

async function sendChatAction(chatId, action = "typing") {
    try {
        await tgCall('sendChatAction', {
            chat_id: chatId,
            action: action
        });
    } catch (e) {}
}

async function editMessageText(chatId, messageId, text, extra = {}) {
    return tgCall('editMessageText', {
        chat_id: chatId,
        message_id: messageId,
        text: text,
        parse_mode: 'HTML',
        ...extra
    });
}

async function answerCallbackQuery(callbackQueryId, text = "") {
    return tgCall('answerCallbackQuery', {
        callback_query_id: callbackQueryId,
        text: text
    });
}

// Envío nativo de documentos / APK mediante FormData multipart
async function sendDocument(chatId, filePath, caption = "", extra = {}) {
    if (!fs.existsSync(filePath)) {
        throw new Error("El archivo no existe en el disco.");
    }
    const fileBuffer = fs.readFileSync(filePath);
    const fileName = path.basename(filePath);
    const formData = new FormData();
    formData.append('chat_id', String(chatId));

    const blob = new Blob([fileBuffer], { type: 'application/vnd.android.package-archive' });
    formData.append('document', blob, fileName);

    if (caption) {
        formData.append('caption', caption);
        formData.append('parse_mode', 'HTML');
    }

    for (const [key, val] of Object.entries(extra)) {
        if (typeof val === 'object') {
            formData.append(key, JSON.stringify(val));
        } else {
            formData.append(key, String(val));
        }
    }

    const res = await fetch(`${API_BASE()}/sendDocument`, {
        method: 'POST',
        body: formData
    });

    const data = await res.json();
    if (!data.ok) {
        throw new Error(data.description || "Error enviando documento por Telegram API");
    }
    return data.result;
}

// STICKERS DE GATITOS OFICIALES Y VERIFICADOS
const CAT_STICKERS = [
    "CAACAgQAAxUAAWrIBi8sDiuAo2YgOTeZmq2TR399AAIeDQACZiCxUeZJ5XdhkD7EPQQ", // Gatito cariñoso
    "CAACAgQAAxUAAWrIBi_F4bz0scr5H0pw7qLv51gYAALmCwAC5tO4Ud7yc8umEFMyPQQ", // Gatito alegre
    "CAACAgQAAxUAAWrIBi8XKiedPSh2hY52-nDM5aDRAAIwDQACifqxUYIhZ4fg-7qiPQQ", // Gatito saltando de felicidad
    "CAACAgQAAxUAAWrIBi-6VhXZ3cl3jm0lRb7ws4O6AAIQCwAC9rewUZ8mOZT4oJTdPQQ", // Gatito inteligente con lentes
    "CAACAgQAAxUAAWrIBi_hBunnm0hU8XPYPnqVB8SIAAKXAANaW7oChB0yK-tGTdA9BA", // Gatito pochoclero sabroso
    "CAACAgQAAxUAAWrIBi9SQpI5-VuyTo8QAlWppPeKAAKZAANaW7oCOTmgh0oC6Hg9BA"  // Gatito travieso
];

async function sendRandomCatSticker(chatId) {
    try {
        const randomSticker = CAT_STICKERS[Math.floor(Math.random() * CAT_STICKERS.length)];
        await tgCall('sendSticker', {
            chat_id: chatId,
            sticker: randomSticker
        });
    } catch (e) {
        console.warn("Aviso enviando sticker de gatito:", e.message);
    }
}

// Configurar perfil y menú oficial en Telegram
async function setupBotProfile() {
    try {
        await tgCall('setMyCommands', {
            commands: [
                { command: "start", description: "🐾 Menú principal gatuno" },
                { command: "descargar", description: "📥 Descargar APK de MichiTV" },
                { command: "activar", description: "🔑 Activar Smart TV o Celular" },
                { command: "prueba", description: "🎁 7 Días VIP gratis de regalo" },
                { command: "instalar", description: "📺 Guía de instalación en TV" },
                { command: "plugins", description: "🔌 Plugins y scrapers de video" },
                { command: "suerte", description: "🎰 Ruleta diaria de la fortuna" },
                { command: "estado", description: "📋 Consultar vigencia de licencia" },
                { command: "michi", description: "🐱 Recibir un sticker gatuno" },
                { command: "faq", description: "❓ Preguntas frecuentes y ayuda" },
                { command: "soporte", description: "🆘 Soporte técnico" },
                { command: "mantenimiento", description: "🛠️ Modo mantenimiento (admin)" },
                { command: "mando", description: "🎮 Atajos para control remoto" },
                { command: "canjear", description: "🎟 Canjear un voucher VIP" },
                { command: "admin", description: "👑 Panel del Jefe Michi" }
            ]
        });

        await tgCall('setMyDescription', {
            description: "🐾 ¡Miau! Soy MichiBot, el asistente inteligente y guardián cuántico de MichiTV Cinema OS.\n\n" +
                "Descarga la aplicación APK directa, activa tu Smart TV escaneando el código QR de tu pantalla, activa celulares con 1 toque, juega a la ruleta diaria de premios, reclama tu prueba VIP gratis y recibe stickers exclusivos de gatitos."
        });

        await tgCall('setMyShortDescription', {
            short_description: "🐾 Descarga la APK, activa tu TV con QR, prueba 7 días VIP y stickers de gatos."
        });

        console.log("✨ Perfil, stickers, comandos y descripciones de @MichitvBot configurados en Telegram.");
    } catch (e) {
        console.warn("Aviso configurando perfil en Telegram:", e.message);
    }
}

// Generadores de claves y vouchers
function generateLicenseKey(deviceCode) {
    const clean = deviceCode.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
    const rand = Math.random().toString(36).substring(2, 6).toUpperCase();
    return `MICHI-VIP-${clean}-${rand}`;
}

function generateVoucherCode(days) {
    const rand1 = Math.random().toString(36).substring(2, 6).toUpperCase();
    const rand2 = Math.random().toString(36).substring(2, 6).toUpperCase();
    return `MICHI-V-${days}D-${rand1}-${rand2}`;
}

// Frases felinas astutas
const michiPhrases = [
    "🐾 *Ronronea mientras calcula en milisegundos con sus bigotes cuánticos*",
    "😺 *Afila sus garras en el servidor y procesa a la velocidad de la luz*",
    "✨ *Mueve la cola elegantemente mientras autentica tu dispositivo*",
    "🐾 *Olfatea los paquetes de red y confirma la conexión con MichiTV*",
    "🐟 *Una sardina por cada película en 4K Ultra HD*",
    "🧶 *Juega con un ovillo de fibra óptica de 10 Gbps*",
    "🍿 *Prepara los pochoclos en el microondas con un golpe de patita*",
    "🐱 *Observa fijamente la pantalla con ojos brillantes de felicidad*"
];

function getRandomMichiPhrase() {
    return michiPhrases[Math.floor(Math.random() * michiPhrases.length)];
}

// Menú principal con botones interactivos
function getMainMenuKeyboard(isAdmin = false) {
    const inline_keyboard = [
        [
            { text: "📥 Descargar MichiTV APK", callback_data: "action_download" },
            { text: "🔑 Activar Dispositivo", callback_data: "action_activate" }
        ],
        [
            { text: "📺 Mis Pantallas (Multidispositivo)", callback_data: "action_devices" }
        ],
        [
            { text: "🎁 7 Días VIP Gratis", callback_data: "action_trial" },
            { text: "🎟 Canjear Voucher", callback_data: "action_redeem" }
        ],
        [
            { text: "📺 Guía de Instalación TV", callback_data: "action_install" },
            { text: "🔌 Plugins & Scrapers", callback_data: "action_plugins" }
        ],
        [
            { text: "🎰 Ruleta de la Suerte", callback_data: "action_roulette" },
            { text: "📋 Mi Estado VIP", callback_data: "action_status" }
        ],
        [
            { text: "❓ Preguntas Frecuentes", callback_data: "action_faq" },
            { text: "🐱 Sticker Gatuno", callback_data: "action_sticker" }
        ],
        [
            { text: "🆘 Soporte Técnico", callback_data: "action_support" }
        ]
    ];

    if (isAdmin) {
        inline_keyboard.push([
            { text: "👑 Panel del Jefe Michi (Admin)", callback_data: "action_admin" }
        ]);
    }

    return { inline_keyboard };
}

// Registrar o actualizar usuario en la base de datos
function touchUser(userId, username) {
    if (!db.users[userId]) {
        db.users[userId] = {
            id: userId,
            username: username,
            first_seen: new Date().toISOString(),
            claimed_trial: false,
            last_roulette: null,
            devices: []
        };
    } else {
        db.users[userId].username = username;
    }
    saveDB();
    return db.users[userId];
}

// ================= HANDLERS DE COMANDOS =================

// 1. Descarga del archivo APK
async function handleDownloadApk(chatId) {
    await sendChatAction(chatId, "upload_document");
    await sendRandomCatSticker(chatId);

    const apkPath = getApkPath();
    if (!apkPath || !fs.existsSync(apkPath)) {
        await sendMessage(chatId, `😿 <b>Miau... No encontré el archivo APK en el servidor.</b>\nPídele al administrador que ejecute la compilación.`);
        return;
    }

    const stat = fs.statSync(apkPath);
    const sizeMb = (stat.size / (1024 * 1024)).toFixed(1);

    await sendMessage(chatId, `🐾 <b>¡Preparando el paquete oficial de MichiTV Cinema OS!</b>\n` +
        `📦 <b>Archivo:</b> <code>MichiTV-Cinema-v2.0.0.apk</code>\n` +
        `⚖️ <b>Tamaño:</b> ${sizeMb} MB\n\n` +
        `<i>Subiendo directamente a este chat de Telegram, un momento...</i> 🐱⏳`);

    try {
        const caption = `🍿 <b>MichiTV Cinema OS • v2.0.0 (Edición Oficial)</b> 🐾✨\n\n` +
            `📱 <b>Compatibilidad:</b> Android TV, Google TV, Fire TV Stick, Xiaomi Box, Teléfonos y Tablets.\n` +
            `⚡️ <b>Peso:</b> ${sizeMb} MB\n\n` +
            `<b>🌟 Novedades de esta versión:</b>\n` +
            `• 🛠️ <b>Modo Mantenimiento con temporizador</b> desde Telegram.\n` +
            `• 🆘 <b>Soporte técnico integrado</b> en el bot.\n` +
            `• 📷 <b>QR de activación también en celulares</b> + código copiable.\n` +
            `• 🎬 <b>Reproducción más estable</b> (servidor FC prioritario).\n` +
            `• 🌙 <b>Modo Cine Oscuro OLED</b> profundo con acentos neón.\n` +
            `• 🔌 Scrapers de Anime, Películas y Series en Español Latino y HD.\n` +
            `• 💾 Reanudación automática <i>Continuar Viendo</i> con base local Room.\n\n` +
            `<i>¡Instala el APK y abre los Ajustes de la app para activarla al instante!</i> 🍿🐱`;

        await sendDocument(chatId, apkPath, caption, {
            reply_markup: {
                inline_keyboard: [
                    [{ text: "📺 ¿Cómo instalar en mi TV?", callback_data: "action_install" }],
                    [{ text: "🔑 Activar mi Dispositivo", callback_data: "action_activate" }],
                    [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
                ]
            }
        });
    } catch (e) {
        console.error("Error enviando APK por Telegram:", e.message);
        await sendMessage(chatId, `⚠️ <b>Aviso al enviar archivo por Telegram:</b> ${e.message}\n\nPuedes descargarlo directamente desde tu navegador o app Downloader con este enlace:\n<code>http://localhost:3000/download/michitv.apk</code>`);
    }
}

// 2. Guía completa de instalación
async function showInstallationGuide(chatId) {
    const guide = `📺 <b>Guía Oficial de Instalación de MichiTV</b> 🐾\n\n` +
        `¡Puedes instalar MichiTV en cualquier pantalla inteligente con estos 3 métodos fáciles!\n\n` +
        `<b>MÉTODO 1: Desde tu Celular a tu TV (Recomendado) 📲➡️📺</b>\n` +
        `1. Descarga el APK tocando el botón <b>📥 Descargar APK</b> en este chat.\n` +
        `2. En tu TV y en tu celular instala la app gratuita <b>Send Files to TV</b> desde Google Play Store.\n` +
        `3. Envía el archivo APK de tu celular a la TV y dale a <i>Instalar</i>.\n\n` +
        `<b>MÉTODO 2: Con la App Downloader en TV (Firestick / Android TV) 🌐</b>\n` +
        `1. En tu TV abre la app <b>Downloader</b> (ícono naranja de AFTVnews).\n` +
        `2. En la barra de dirección ingresa la URL de descarga o abre el navegador web integrado.\n` +
        `3. La descarga comenzará automáticamente y pulsa <i>Instalar</i>.\n\n` +
        `<b>MÉTODO 3: Con Memoria USB / Pendrive 💾</b>\n` +
        `1. Copia el archivo APK que descargaste aquí a un Pendrive.\n` +
        `2. Conéctalo al puerto USB de tu Smart TV o TV Box.\n` +
        `3. Ábrelo con cualquier explorador de archivos (ej. <i>File Commander</i>) e instala.`;

    await sendMessage(chatId, guide, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📥 Descargar APK Ahora", callback_data: "action_download" }],
                [{ text: "🔑 Ya la instalé, quiero Activar", callback_data: "action_activate" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 3. Prueba VIP Gratuita de Bienvenida (7 Días)
async function handleFreeTrial(chatId, userId, username) {
    const user = touchUser(userId, username);

    if (user.claimed_trial) {
        await sendMessage(chatId, `🐱 <b>¡Miau! Ya has disfrutado de tu prueba VIP de bienvenida de 7 días.</b>\n\n` +
            `Puedes probar tu suerte diaria en la <b>🎰 Ruleta de la Fortuna</b> para ganar más días VIP, o canjear un voucher oficial.`, {
            reply_markup: {
                inline_keyboard: [
                    [{ text: "🎰 Girar Ruleta de la Suerte", callback_data: "action_roulette" }],
                    [{ text: "🎟 Canjear Voucher", callback_data: "action_redeem" }],
                    [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
                ]
            }
        });
        return;
    }

    // Crear voucher de 7 días
    const trialVoucher = generateVoucherCode(7);
    db.vouchers[trialVoucher] = {
        days: 7,
        plan: "Prueba Gratuita VIP 7 Días 🎁",
        created_at: new Date().toISOString(),
        created_by: `Regalo a ${username}`,
        used: false
    };

    user.claimed_trial = true;
    saveDB();

    await sendRandomCatSticker(chatId);
    const msg = `🎉 <b>¡RONRONEO DE BIENVENIDA! Aquí tienes tus 7 Días VIP Gratis</b> 🐾✨\n\n` +
        `🎁 <b>Tu Voucher de Regalo:</b>\n<code>${trialVoucher}</code>\n\n` +
        `⭐️ <b>Beneficios VIP:</b>\n` +
        `• Sin límites de reproducción en 1080p y 4K.\n` +
        `• Desbloqueo de todos los scrapers y servidores rápidos.\n` +
        `• Soporte prioritario con MichiBot.\n\n` +
        `<b>🐾 Cómo canjearlo en tu TV o Teléfono:</b>\n` +
        `1. Abre MichiTV y entra a ⚙️ <b>Ajustes</b>.\n` +
        `2. En la sección <i>Licencia y Activación</i>, escribe o pega este voucher en el campo correspondiente:\n` +
        `   <code>${trialVoucher}</code>\n` +
        `3. Toca el botón <b>Activar</b> ¡y listo! Disfruta del cine 🍿🐱`;

    await sendMessage(chatId, msg, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "🔑 ¿Tienes código de dispositivo? Actívalo aquí", callback_data: `redeem_direct_${trialVoucher}` }],
                [{ text: "📥 Descargar MichiTV APK", callback_data: "action_download" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 4. Ruleta diaria de la suerte felina
async function handleDailyRoulette(chatId, userId, username) {
    const user = touchUser(userId, username);
    const now = Date.now();
    const COOLDOWN_MS = 24 * 60 * 60 * 1000; // 24 horas

    if (user.last_roulette && (now - user.last_roulette < COOLDOWN_MS)) {
        const remainingHours = Math.ceil((COOLDOWN_MS - (now - user.last_roulette)) / (1000 * 60 * 60));
        await sendMessage(chatId, `⏳ <b>¡Los michis necesitan descansar sus patitas!</b>\n\n` +
            `Ya giraste la ruleta hoy. Vuelve en aproximadamente <b>${remainingHours} horas</b> para tu próxima tirada diaria de la fortuna. 🐱💤`, {
            reply_markup: {
                inline_keyboard: [
                    [{ text: "📋 Ver Mi Estado VIP", callback_data: "action_status" }],
                    [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
                ]
            }
        });
        return;
    }

    user.last_roulette = now;
    saveDB();

    await sendMessage(chatId, `🎰 <b>¡Girando la Ruleta Felina de la Fortuna con las patitas cuánticas!...</b> 🐾\n<i>*Trrrr-clic-clic-clic-clic*</i> 🌀✨`);
    await sendChatAction(chatId, "choose_sticker");

    await new Promise(r => setTimeout(r, 1500));

    // Posibles premios con probabilidades ponderadas
    const roll = Math.random() * 100;
    let prizeTitle = "";
    let prizeDesc = "";
    let voucherCode = null;

    if (roll < 5) {
        // 5% Jackpot 7 días VIP
        voucherCode = generateVoucherCode(7);
        db.vouchers[voucherCode] = { days: 7, plan: "Jackpot Ruleta 7 Días VIP 👑", created_at: new Date().toISOString(), used: false };
        saveDB();
        prizeTitle = "👑 ¡¡JACKPOT CÓSMICO FELINO!! 7 DÍAS VIP GRATIS 🐾";
        prizeDesc = `¡Tus bigotes tienen magnetismo de oro! Has ganado <b>7 días de suscripción VIP</b> completa.\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
    } else if (roll < 20) {
        // 15% 3 días VIP
        voucherCode = generateVoucherCode(3);
        db.vouchers[voucherCode] = { days: 3, plan: "Ruleta 3 Días VIP ⭐️", created_at: new Date().toISOString(), used: false };
        saveDB();
        prizeTitle = "⭐️ ¡PREMIO DE PLATA! 3 DÍAS VIP GRATIS 🐱";
        prizeDesc = `¡Un salto acrobático perfecto! Ganaste <b>3 días de membresía VIP</b>.\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
    } else if (roll < 50) {
        // 30% 1 día VIP
        voucherCode = generateVoucherCode(1);
        db.vouchers[voucherCode] = { days: 1, plan: "Ruleta 1 Día VIP 🎟", created_at: new Date().toISOString(), used: false };
        saveDB();
        prizeTitle = "🎟 ¡PREMIO DE BRONCE! 1 DÍA VIP EXTRA 🐾";
        prizeDesc = `¡Excelente tiro con garra! Te llevas <b>24 horas de acceso VIP</b> libre.\n\n🎟 <b>Tu Voucher Ganador:</b>\n<code>${voucherCode}</code>`;
    } else if (roll < 80) {
        // 30% Sardina Dorada (Sticker divertido)
        prizeTitle = "🐟 ¡SARDINA DORADA VIRTUAL Y BENDICIÓN GATUNA! 🐱";
        prizeDesc = `Has sido bendecido con el ronroneo de la abundancia. ¡Tus series cargarán a velocidad de la luz y sin cortes! Aquí tienes un sticker conmemorativo:`;
    } else {
        // 20% Ovillo de lana cósmico
        prizeTitle = "🧶 ¡OVILLO DE LANA CÓSMICO PARA DORMIR SIESTA! 💤";
        prizeDesc = `Hoy es un gran día para tirarse en el sillón a ver una buena película con pochoclos calentitos. ¡Vuelve mañana por más premios!`;
    }

    await sendRandomCatSticker(chatId);
    await sendMessage(chatId, `🎉 <b>RESULTADO DE LA RULETA:</b>\n\n${prizeTitle}\n\n${prizeDesc}`, {
        reply_markup: {
            inline_keyboard: [
                ...(voucherCode ? [[{ text: "🔑 Activar este Voucher en mi Dispositivo", callback_data: `redeem_direct_${voucherCode}` }]] : []),
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 5. Catálogo de Plugins y Scrapers
async function showPluginsInfo(chatId) {
    const text = `🔌 <b>Catálogo de Plugins & Scrapers de MichiTV</b> 🐾\n\n` +
        `MichiTV cuenta con un potente motor modular de scraping en JavaScript (QuickJS) optimizado:\n\n` +
        `🎬 <b>AnimeAV1 (v1.4.2) [ACTIVO]:</b>\n` +
        `• Anime en emisión y finalizados en Audio Latino y Subtitulado.\n` +
        `• Servidores rápidos recomendados: <b>Voe (H.264 720p/1080p)</b> compatible con cualquier TV.\n\n` +
        `🍿 <b>AnimeFLV One (v0.3.0) [ACTIVO]:</b>\n` +
        `• Más de 3,500 animes con episodios diarios actualizados al minuto.\n` +
        `• Múltiples opciones de extracción de video directo.\n\n` +
        `📺 <b>Caracol TV Streaming (v1.0.2) [ACTIVO]:</b>\n` +
        `• Canales en directo y programación de novelas y noticias.\n\n` +
        `🎥 <b>FuegoCine & PelisPlus Direct [ACTIVO]:</b>\n` +
        `• Estrenos mundiales de cine en cartelera, resolución 4K UHD y servidores sin publicidad.`;

    await sendMessage(chatId, text, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📥 Descargar MichiTV APK", callback_data: "action_download" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 6. Preguntas frecuentes (FAQ)
async function showFaq(chatId) {
    const text = `❓ <b>Preguntas Frecuentes y Soporte MichiTV</b> 🐾\n\n` +
        `<b>1. ¿Cómo selecciono el audio en Español Latino?</b>\n` +
        `En MichiTV ve a ⚙️ <i>Ajustes > Preferencias de Reproducción</i> y marca <b>Español Latino</b> como predeterminado.\n\n` +
        `<b>2. ¿Cómo funciona la reanudación automática?</b>\n` +
        `MichiTV guarda tu minuto exacto en la base de datos local Room. En la pantalla de Inicio verás la fila <i>"Continuar Viendo"</i> con el botón directo para seguir donde lo dejaste.\n\n` +
        `<b>3. ¿Por qué una película tarda en cargar o se traba?</b>\n` +
        `Prueba cambiar el servidor de video en la ficha técnica (recomendamos el servidor <b>Voe</b>, que es el más estable y rápido para Smart TVs).\n\n` +
        `<b>4. ¿Cómo libero memoria si la app va lenta?</b>\n` +
        `En ⚙️ <i>Ajustes > Almacenamiento</i> pulsa <b>"Limpiar Caché y Reiniciar Memoria"</b> para optimizar el rendimiento.`;

    await sendMessage(chatId, text, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📺 Atajos de Control Remoto", callback_data: "action_mando" }],
                [{ text: "🐱 Pedir Sticker Michi", callback_data: "action_sticker" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 7. Atajos para Control Remoto en TV
async function showRemoteGuide(chatId) {
    const text = `🎮 <b>Guía de Control Remoto para Android TV</b> 🐾\n\n` +
        `MichiTV está 100% optimizado para navegación D-Pad en controles remotos:\n\n` +
        `• <b>Flechas (Arriba / Abajo / Izquierda / Derecha):</b> Navega fluidamente entre películas, filas de catálogo y menú lateral.\n` +
        `• <b>Botón OK / Centro D-Pad:</b> Abre la película seleccionada o inicia la reproducción.\n` +
        `• <b>Play / Pause (en control):</b> Pausa o reanuda el video inmediatamente.\n` +
        `• <b>Flecha Atrás (Back):</b> Cierra el reproductor o vuelve a la pantalla anterior.\n` +
        `• <b>En el Reproductor:</b> Puedes pulsar Arriba/Abajo para mostrar la barra de tiempo, cambiar servidor o pista de audio.`;

    await sendMessage(chatId, text, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📥 Descargar APK", callback_data: "action_download" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 8. Estado del dispositivo o licencia
async function processCheckStatus(chatId, rawCode) {
    const code = rawCode.trim().toUpperCase();
    if (!code.startsWith('MICHI-')) {
        await sendMessage(chatId, `😿 <b>Formato no reconocido.</b>\nEl código de dispositivo debe comenzar con <code>MICHI-</code> (ej: <code>MICHI-78B2</code>).`);
        return;
    }

    const dev = db.devices[code];
    if (!dev) {
        await sendMessage(chatId, `🔍 <b>Dispositivo <code>${code}</code> no encontrado en el sistema.</b>\n` +
            `Este televisor o teléfono aún no tiene una membresía VIP activa.\n\n` +
            `¿Deseas activarlo con tus 7 días gratis o con una licencia?`, {
            reply_markup: {
                inline_keyboard: [
                    [{ text: "🎁 Activar 7 Días VIP Gratis", callback_data: "action_trial" }],
                    [{ text: "🔑 Activar Dispositivo", callback_data: "action_activate" }],
                    [{ text: "⬅️ Menú", callback_data: "action_menu" }]
                ]
            }
        });
        return;
    }

    const expDate = new Date(dev.expires_at);
    const now = new Date();
    const isExpired = expDate <= now;
    const daysLeft = Math.max(0, Math.ceil((expDate - now) / (1000 * 60 * 60 * 24)));

    let statusMsg = `📋 <b>Ficha Técnica de Licencia MichiTV</b> 🐾\n\n` +
        `📱 <b>Dispositivo:</b> <code>${dev.device_code}</code>\n` +
        `⭐️ <b>Estado:</b> ${isExpired ? "🔴 EXPIRADO" : "🟢 ACTIVO • VIP 👑"}\n` +
        `🏷 <b>Plan:</b> ${dev.plan}\n` +
        `📅 <b>Vence:</b> ${expDate.toLocaleDateString('es-ES')} (${daysLeft} días restantes)\n` +
        `🔑 <b>Clave de Activación:</b> <code>${dev.license_key}</code>\n` +
        `👤 <b>Vinculado a:</b> ${dev.tg_username || "Humano anónimo"}\n\n`;

    if (isExpired) {
        statusMsg += `⚠️ <i>Tu licencia ha finalizado. Puedes canjear un nuevo voucher para seguir disfrutando.</i>`;
    } else {
        statusMsg += `🎉 <i>¡Tu MichiTV está ronroneando a máxima potencia y listo para reproducir!</i> 🍿`;
    }

    await sendMessage(chatId, statusMsg, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "🎟 Extender con Voucher", callback_data: "action_redeem" }],
                [{ text: "🐱 Pedir Sticker", callback_data: "action_sticker" }],
                [{ text: "⬅️ Menú", callback_data: "action_menu" }]
            ]
        }
    });
}

// 9. Activación de dispositivo
async function processActivation(chatId, userId, username, rawDeviceCode) {
    let code = rawDeviceCode.trim().toUpperCase();

    if (!code.startsWith('MICHI-')) {
        if (code.length === 4) {
            code = `MICHI-${code}`;
        } else {
            await sendMessage(chatId, `❌ <b>Formato de código inválido.</b>\nIngresa tu código de dispositivo (Ej: <code>MICHI-78B2</code>) o PIN de 4 dígitos. Búscalo en ⚙️ <b>Ajustes</b> de la aplicación.`);
            return;
        }
    }

    if (db.devices[code]) {
        const existingDev = db.devices[code];
        const isExpired = new Date(existingDev.expires_at) <= new Date();

        if (!isExpired) {
            if (existingDev.tg_user_id !== userId) {
                await sendMessage(chatId, `⚠️ <b>Este dispositivo ya está vinculado a otra cuenta de Telegram (${existingDev.tg_username}).</b>\n\nSi crees que es un error, por favor contacta a soporte.`);
            } else {
                await sendMessage(chatId, `🐱 <b>¡Miau! Tu dispositivo <code>${code}</code> ya está activado y vigente.</b>\n\nVence el: ${new Date(existingDev.expires_at).toLocaleDateString('es-ES')}`, {
                    reply_markup: { inline_keyboard: [[{ text: "📋 Ver Estado de Mi TV", callback_data: `status_${code}` }], [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]] }
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
        plan: "VIP MichiTV Pass",
        tg_user_id: userId,
        tg_username: username
    };

    touchUser(userId, username);
    if (!db.users[userId].devices.includes(code)) {
        db.users[userId].devices.push(code);
    }
    saveDB();

    await sendRandomCatSticker(chatId);
    const formattedExp = expires.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });

    const successMsg = `🎉 <b>¡RONRONEO DE VICTORIA! Dispositivo Activado</b> 🐾✨\n\n` +
        `📱 <b>Dispositivo:</b> <code>${code}</code>\n` +
        `⭐️ <b>Membresía:</b> Premium MichiTV VIP 🐾\n` +
        `📅 <b>Válido hasta:</b> ${formattedExp} (${config.default_duration_days} días)\n` +
        `👤 <b>Dueño Humano:</b> ${username}\n\n` +
        `⚡️ <b>¡ACTIVACIÓN EN TIEMPO REAL!</b>\n` +
        `Tu pantalla se actualizará <b>automáticamente al estado VIP</b> en unos segundos sin que tengas que escribir nada con el control remoto. ¡Ponte cómodo con tus pochoclos! 🍿🐱\n\n` +
        `🔑 <i>Clave de respaldo manual (por si acaso):</i> <code>${licenseKey}</code>`;

    await sendMessage(chatId, successMsg, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📋 Consultar Estado", callback_data: `status_${code}` }],
                [{ text: "🐱 Pedir Sticker Michi", callback_data: "action_sticker" }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 10. Canje de vouchers
async function processRedeemStep1(chatId, voucherCode) {
    const v = voucherCode.trim().toUpperCase();
    if (!db.vouchers[v]) {
        await sendMessage(chatId, `😿 <b>Miau... Voucher no encontrado o inválido.</b>\nVerifica que esté bien escrito.`);
        return;
    }
    if (db.vouchers[v].used) {
        await sendMessage(chatId, `⚠️ <b>Este voucher ya fue saboreado por otro michi el ${new Date(db.vouchers[v].used_at).toLocaleDateString()}.</b>`);
        return;
    }

    userStates[chatId] = { type: 'WAITING_VOUCHER_DEVICE', voucher: v };
    await sendMessage(chatId, `😻 <b>¡Voucher verificado con éxito!</b> (${db.vouchers[v].days} días VIP)\n\nAhora envía el <b>Código de Dispositivo</b> que quieres premiar (Ejemplo: <code>MICHI-78B2</code>):`);
}

async function processRedeemStep2(chatId, userId, username, voucherCode, rawDevice) {
    const v = voucherCode.trim().toUpperCase();
    const code = rawDevice.trim().toUpperCase();

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
        plan: voucher.plan || `VIP ${voucher.days} Días 🐾`,
        tg_user_id: userId,
        tg_username: username,
        redeemed_voucher: v
    };

    voucher.used = true;
    voucher.used_at = new Date().toISOString();
    voucher.used_by = username;

    touchUser(userId, username);
    if (!db.users[userId].devices.includes(code)) {
        db.users[userId].devices.push(code);
    }
    saveDB();

    await sendRandomCatSticker(chatId);
    const expStr = expires.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });

    const msg = `🎉 <b>¡VOUCHER CANJEADO CON ÉXITO TOTAL!</b> 🎟🐾\n\n` +
        `📱 <b>Dispositivo Activado:</b> <code>${code}</code>\n` +
        `⏱ <b>Días Agregados:</b> +${voucher.days} días\n` +
        `📅 <b>Nueva Fecha de Vencimiento:</b> ${expStr}\n` +
        `🔑 <b>Clave de Licencia VIP:</b> <code>${licenseKey}</code>\n\n` +
        `<i>Pégala en ⚙️ Ajustes de MichiTV para confirmar tu membresía VIP.</i>`;

    await sendMessage(chatId, msg, {
        reply_markup: {
            inline_keyboard: [
                [{ text: "📋 Ver Estado de Mi TV", callback_data: `status_${code}` }],
                [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
            ]
        }
    });
}

// 11. Gestión Multidispositivo (Mis Pantallas)
async function showUserDevices(chatId, userId, username) {
    const user = touchUser(userId, username);
    if (!user.devices || user.devices.length === 0) {
        await sendMessage(chatId, `😿 <b>Aún no tienes televisores ni celulares vinculados.</b>\n\nPuedes vincular uno escaneando el Código QR en tu TV o con el botón de Activar.`, {
            reply_markup: {
                inline_keyboard: [
                    [{ text: "🔑 Activar Nuevo Dispositivo", callback_data: "action_activate" }],
                    [{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]
                ]
            }
        });
        return;
    }

    let msg = `📺 <b>Gestión de Mis Pantallas (Multidispositivo)</b> 🐾\n\n`;
    const keyboard = [];

    user.devices.forEach((code, index) => {
        const dev = db.devices[code];
        if (dev) {
            const isExpired = new Date(dev.expires_at) <= new Date();
            const statusIcon = isExpired ? "🔴" : "🟢";
            msg += `<b>${index + 1}. Dispositivo:</b> <code>${code}</code> ${statusIcon}\n`;
            msg += `   Vence: ${new Date(dev.expires_at).toLocaleDateString('es-ES')}\n`;
            
            keyboard.push([{ text: `${statusIcon} Ver ${code}`, callback_data: `status_${code}` }]);
        }
    });

    keyboard.push([{ text: "🔑 Añadir Pantalla", callback_data: "action_activate" }]);
    keyboard.push([{ text: "⬅️ Menú Principal", callback_data: "action_menu" }]);

    await sendMessage(chatId, msg, {
        reply_markup: { inline_keyboard: keyboard }
    });
}

// 12. Panel del Administrador
async function showAdminPanel(chatId) {
    const totalDevices = Object.keys(db.devices).length;
    const totalVouchers = Object.keys(db.vouchers).length;
    const usedVouchers = Object.values(db.vouchers).filter(v => v.used).length;
    const totalUsers = Object.keys(db.users).length;

    const stats = `👑 <b>Panel del Jefe Michi Supremo (Admin)</b> 🐾\n\n` +
        `👥 <b>Usuarios registrados:</b> ${totalUsers}\n` +
        `📱 <b>Dispositivos activados:</b> ${totalDevices}\n` +
        `🎟 <b>Vouchers totales:</b> ${totalVouchers} (${usedVouchers} canjeados, ${totalVouchers - usedVouchers} libres)\n\n` +
        `<b>Opciones Rápidas de Administración:</b>`;

    await sendMessage(chatId, stats, {
        reply_markup: {
            inline_keyboard: [
                [
                    { text: "🎟 Crear Voucher 30D", callback_data: "admin_v30" },
                    { text: "🎟 Crear Voucher 90D", callback_data: "admin_v90" }
                ],
                [
                    { text: "🎟 Crear Voucher 1 Año", callback_data: "admin_v365" }
                ],
                [
                    { text: "⬅️ Volver al Menú Principal", callback_data: "action_menu" }
                ]
            ]
        }
    });
}

// 13. Modo Mantenimiento Global con temporizador (solo admin)
// Uso: /mantenimiento on [minutos] [mensaje] | /mantenimiento off | /mantenimiento estado
async function handleMaintenanceCommand(chatId, userId, text) {
    const isAdmin = config.admin_ids.includes(userId) || config.admin_ids.length === 0;
    if (!isAdmin) {
        await sendMessage(chatId, `🔒 <b>Solo el Jefe Michi puede usar este comando.</b> 🐾`);
        return;
    }
    const args = text.split(/\s+/).slice(1); // quita "/mantenimiento"
    const sub = (args[0] || "").toLowerCase();

    if (sub === "off") {
        db.config.maintenance = false;
        db.config.maintenance_until = null;
        saveDB();
        await sendMessage(chatId, `✅ <b>Modo mantenimiento APAGADO.</b>\nLas apps volverán al catálogo solas en segundos. 🐾`);
        return;
    }

    if (sub === "estado" || sub === "status") {
        const m = db.config.maintenance;
        const until = db.config.maintenance_until;
        const left = (m && until) ? Math.max(0, Math.ceil((until - Date.now()) / 60000)) : null;
        await sendMessage(chatId, `🛠️ <b>Estado de mantenimiento:</b> ${m ? "🟡 ACTIVO" : "🟢 APAGADO"}\n` +
            (m ? `💬 Mensaje: ${db.config.maintenance_message}\n` : "") +
            (left !== null ? `⏱ Quedan aprox. ${left} minutos.\n` : "") +
            `\nUsa <code>/mantenimiento on 30 Estamos actualizando</code> o <code>/mantenimiento off</code>.`);
        return;
    }

    if (sub === "on") {
        let minutes = null;
        let msgStart = 1;
        if (args[1] && /^\d+$/.test(args[1])) {
            minutes = parseInt(args[1], 10);
            msgStart = 2;
        }
        const message = args.slice(msgStart).join(" ").trim() ||
            "Estamos realizando mejoras en nuestros servidores. Volvemos enseguida.";
        db.config.maintenance = true;
        db.config.maintenance_message = message;
        db.config.maintenance_until = minutes ? Date.now() + minutes * 60000 : null;
        saveDB();
        await sendMessage(chatId, `🛠️ <b>Modo mantenimiento ACTIVADO.</b> 🐾\n\n` +
            `💬 Mensaje: ${message}\n` +
            (minutes ? `⏱ Duración: ${minutes} minutos (se apaga solo).\n` : `⏱ Duración: indefinida (apágala con <code>/mantenimiento off</code>).\n`) +
            `\nLas apps mostrarán la pantalla de mantenimiento en segundos.`);
        return;
    }

    await sendMessage(chatId, `🛠️ <b>Uso del modo mantenimiento:</b>\n\n` +
        `<code>/mantenimiento on 30 Estamos actualizando</code>\n` +
        `<code>/mantenimiento on Estamos arreglando la app</code> (sin tiempo)\n` +
        `<code>/mantenimiento off</code>\n` +
        `<code>/mantenimiento estado</code>`);
}

// 14. Soporte técnico: ticket del cliente + respuesta del admin por Reply
async function handleSupportMessage(chatId, userId, username, text) {
    const ticketId = Date.now().toString(36).toUpperCase().slice(-6);
    db.support_tickets[ticketId] = {
        id: ticketId,
        user_chat_id: chatId,
        user_id: userId,
        username: username,
        message: text,
        created_at: new Date().toISOString(),
        status: "open"
    };
    saveDB();

    await sendMessage(chatId, `✅ <b>¡Ticket recibido!</b> 🐾\n\nTu mensaje ya está en manos del equipo MichiTV. Te responderemos por este mismo chat en breve.\n🎫 Ticket: <code>${ticketId}</code>`);

    const adminMsg = `🆘 <b>Nuevo Ticket de Soporte</b>\n\n` +
        `👤 Usuario: ${username}\n🆔 (ID: ${ticketId})\n\n` +
        `💬 Mensaje: "${text}"\n\n` +
        `<i>Para responder, mantén presionado ESTE mensaje, elige "Responder" (Reply) y escribe la solución.</i>`;

    const admins = config.admin_ids.length > 0 ? config.admin_ids : [chatId];
    for (const adminId of admins) {
        try {
            // No reenviar al mismo cliente si también es admin: igual le sirve como copia
            await sendMessage(adminId, adminMsg);
        } catch (e) {}
    }
}

async function handleAdminReply(msg) {
    // El admin respondió con Reply a un ticket: reenviar al cliente
    const reply = msg.reply_to_message;
    if (!reply || !reply.text) return false;
    if (reply.text.indexOf("Nuevo Ticket de Soporte") === -1) return false;
    const m = /ID:\s*([A-Z0-9]+)/.exec(reply.text);
    if (!m) return false;
    const ticket = db.support_tickets[m[1]];
    if (!ticket) return false;

    const answer = (msg.text || "").trim();
    if (!answer) return true;
    ticket.status = "answered";
    saveDB();
    try {
        await sendMessage(ticket.user_chat_id, `🐾 <b>Respuesta del equipo MichiTV</b> (🎫 ${ticket.id}):\n\n${answer}`);
        await sendMessage(msg.chat.id, `✅ <b>Respuesta enviada al cliente.</b> 🐾`);
    } catch (e) {
        await sendMessage(msg.chat.id, `⚠️ No pude entregar la respuesta: ${e.message}`);
    }
    return true;
}

// Procesar mensajes entrantes
async function handleMessage(msg) {
    if (!msg.text) return;

    const chatId = msg.chat.id;
    const userId = msg.from.id;
    const username = msg.from.username ? `@${msg.from.username}` : (msg.from.first_name || "Humano Amigo");
    const text = msg.text.trim();
    const isAdmin = config.admin_ids.includes(userId) || config.admin_ids.length === 0;

    // Asignar primer usuario como admin si está vacío
    if (config.admin_ids.length === 0) {
        config.admin_ids.push(userId);
        fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
        console.log(`👑 ¡El usuario ${username} (${userId}) es el Jefe Michi Administrador!`);
    }

    touchUser(userId, username);

    // Respuesta del admin a un ticket (Reply al mensaje del ticket)
    if (msg.reply_to_message && isAdmin) {
        const handled = await handleAdminReply(msg);
        if (handled) return;
    }

    // --- MANEJO DE DEEP LINKING (QR DESDE TV O BOTÓN CELULAR) ---
    if (text.startsWith('/start ') && text.length > 7) {
        const payload = text.substring(7).trim();
        let targetCode = null;

        if (payload.startsWith('activar_')) {
            targetCode = payload.replace('activar_', '').trim();
        } else if (payload.startsWith('MICHI-')) {
            targetCode = payload.trim();
        }

        if (targetCode && targetCode.startsWith('MICHI-')) {
            await sendRandomCatSticker(chatId);
            await sendMessage(chatId, `😻 <b>¡Miau! Detecté tu código escaneado desde tu pantalla:</b> <code>${targetCode}</code>\n\n<i>${getRandomMichiPhrase()}</i>`);
            await processActivation(chatId, userId, username, targetCode);
            return;
        }
    }

    // Manejar estados interactivos de texto
    if (userStates[chatId]) {
        const state = userStates[chatId];
        delete userStates[chatId];

        if (state.type === 'WAITING_DEVICE_CODE') {
            await processActivation(chatId, userId, username, text);
            return;
        } else if (state.type === 'WAITING_VOUCHER_CODE') {
            await processRedeemStep1(chatId, text);
            return;
        } else if (state.type === 'WAITING_VOUCHER_DEVICE') {
            await processRedeemStep2(chatId, userId, username, state.voucher, text);
            return;
        } else if (state.type === 'WAITING_STATUS_CODE') {
            await processCheckStatus(chatId, text);
            return;
        } else if (state.type === 'WAITING_SUPPORT_MSG') {
            await handleSupportMessage(chatId, userId, username, text);
            return;
        }
    }

    // --- COMANDO /start ---
    if (text === '/start') {
        await sendRandomCatSticker(chatId);
        const welcome = `🐾 <b>¡Prrr! ¡Hola, ${username}! Bienvenido a MichiBot</b> 🐱✨\n\n` +
            `Soy el guardián felino inteligente y mayordomo cuántico de <b>MichiTV Cinema OS</b>.\n\n` +
            `<i>${getRandomMichiPhrase()}</i>\n\n` +
            `🚀 <b>¿Qué deseas hacer hoy?</b>\n` +
            `• <b>Descargar APK:</b> Obtén la app en 1 toque directo a este chat.\n` +
            `• <b>Activar TV con QR:</b> Escanea el código en pantalla desde ⚙️ Ajustes.\n` +
            `• <b>Prueba Gratis:</b> Reclama 7 días de suscripción VIP sin cargo.\n` +
            `• <b>Ruleta Diaria:</b> Gira la ruleta y gana premios felinos.\n\n` +
            `¡Toca una patita del menú abajo para comenzar! 👇`;

        await sendMessage(chatId, welcome, {
            reply_markup: getMainMenuKeyboard(isAdmin)
        });
        return;
    }

    // --- COMANDOS DE DESCARGA DE APK ---
    if (text === '/descargar' || text === '/apk' || text === '/download') {
        await handleDownloadApk(chatId);
        return;
    }

    // --- COMANDO /instalar ---
    if (text === '/instalar' || text === '/guia') {
        await showInstallationGuide(chatId);
        return;
    }

    // --- COMANDO /prueba (Free Trial) ---
    if (text === '/prueba' || text === '/demo' || text === '/trial') {
        await handleFreeTrial(chatId, userId, username);
        return;
    }

    // --- COMANDO /suerte o /ruleta ---
    if (text === '/suerte' || text === '/ruleta') {
        await handleDailyRoulette(chatId, userId, username);
        return;
    }

    // --- COMANDO /plugins ---
    if (text === '/plugins' || text === '/canales') {
        await showPluginsInfo(chatId);
        return;
    }

    // --- COMANDO /faq ---
    if (text === '/faq' || text === '/soporte') {
        await showFaq(chatId);
        return;
    }

    // --- COMANDO /mando ---
    if (text === '/mando' || text === '/control' || text === '/atajos') {
        await showRemoteGuide(chatId);
        return;
    }

    // --- COMANDO /mantenimiento (admin: on/off/estado, con minutos opcionales) ---
    if (text.startsWith('/mantenimiento') || text.startsWith('/maintenance')) {
        await handleMaintenanceCommand(chatId, userId, text);
        return;
    }

    // --- COMANDO /soporte (ticket del cliente) ---
    if (text === '/soporte' || text === '/support' || text === '/ayuda_soporte') {
        userStates[chatId] = { type: 'WAITING_SUPPORT_MSG' };
        await sendMessage(chatId, `🆘 <b>Soporte Técnico MichiTV</b> 🐾\n\nCuéntame tu problema en un solo mensaje (qué falla, en qué pantalla y en qué dispositivo):`);
        return;
    }

    // --- COMANDO /michi ---
    if (text === '/michi' || text === '/sticker') {
        await sendRandomCatSticker(chatId);
        await sendMessage(chatId, `🐱 <i>¡Miau! Aquí tienes un sticker con bendición gatuna para tu día.</i> 🐾`, {
            reply_markup: getMainMenuKeyboard(isAdmin)
        });
        return;
    }

    // --- COMANDO /activar ---
    if (text.startsWith('/activar')) {
        const parts = text.split(/\s+/);
        if (parts.length < 2) {
            userStates[chatId] = { type: 'WAITING_DEVICE_CODE' };
            await sendMessage(chatId, `🐱 <b>¡Miau! Acércame tu Código de Dispositivo:</b>\n\nLo encuentras en la sección de ⚙️ <b>Ajustes</b> de MichiTV (Ejemplo: <code>MICHI-78B2</code>):\n\n<i>Escríbelo o pégalo aquí abajo:</i>`);
            return;
        }
        await processActivation(chatId, userId, username, parts[1]);
        return;
    }

    // --- COMANDO /dispositivos ---
    if (text === '/dispositivos' || text === '/pantallas') {
        await showUserDevices(chatId, userId, username);
        return;
    }

    // --- COMANDO /estado ---
    if (text.startsWith('/estado')) {
        const parts = text.split(/\s+/);
        if (parts.length < 2) {
            userStates[chatId] = { type: 'WAITING_STATUS_CODE' };
            await sendMessage(chatId, `🔍 <b>Mis bigotes están listos para rastrear tu licencia.</b>\nEnvía el código de tu dispositivo (Ej: <code>MICHI-78B2</code>):`);
            return;
        }
        await processCheckStatus(chatId, parts[1]);
        return;
    }

    // --- COMANDO /canjear ---
    if (text.startsWith('/canjear')) {
        const parts = text.split(/\s+/);
        if (parts.length < 3) {
            await sendMessage(chatId, `💡 <b>Formato felino correcto:</b>\n<code>/canjear &lt;VOUCHER&gt; &lt;CODIGO_DISPOSITIVO&gt;</code>\n\nEjemplo:\n<code>/canjear MICHI-V-30D-ABCD-1234 MICHI-78B2</code>`);
            return;
        }
        await processRedeemStep2(chatId, userId, username, parts[1], parts[2]);
        return;
    }

    // --- COMANDO /admin ---
    if (text === '/admin' && isAdmin) {
        await showAdminPanel(chatId);
        return;
    }

    // --- COMANDO /ayuda ---
    if (text === '/ayuda' || text === '/help') {
        await showInstallationGuide(chatId);
        return;
    }

    // Respuesta genérica inteligente
    await sendMessage(chatId, `🐱 <i>*Miau curioso*</i>... Elige una de mis patitas interactivas para continuar:`, {
        reply_markup: getMainMenuKeyboard(isAdmin)
    });
}

// Manejo de botones interactivos (Callback Queries)
async function handleCallback(query) {
    const chatId = query.message.chat.id;
    const messageId = query.message.message_id;
    const data = query.data;
    const userId = query.from.id;
    const username = query.from.username ? `@${query.from.username}` : (query.from.first_name || "Humano");
    const isAdmin = config.admin_ids.includes(userId);

    await answerCallbackQuery(query.id);

    if (data === "action_menu") {
        await editMessageText(chatId, messageId, `🐾 <b>Menú Principal de MichiTV</b>\n¿Qué deseas hacer hoy, amigo humano?`, {
            reply_markup: getMainMenuKeyboard(isAdmin)
        });
    } else if (data === "action_download") {
        await handleDownloadApk(chatId);
    } else if (data === "action_install") {
        await showInstallationGuide(chatId);
    } else if (data === "action_trial") {
        await handleFreeTrial(chatId, userId, username);
    } else if (data === "action_roulette") {
        await handleDailyRoulette(chatId, userId, username);
    } else if (data === "action_plugins") {
        await showPluginsInfo(chatId);
    } else if (data === "action_faq") {
        await showFaq(chatId);
    } else if (data === "action_mando") {
        await showRemoteGuide(chatId);
    } else if (data === "action_support") {
        userStates[chatId] = { type: 'WAITING_SUPPORT_MSG' };
        await sendMessage(chatId, `🆘 <b>Soporte Técnico MichiTV</b> 🐾\n\nCuéntame tu problema en un solo mensaje (qué falla, en qué pantalla y en qué dispositivo):`);
    } else if (data === "action_sticker") {
        await sendRandomCatSticker(chatId);
    } else if (data === "action_activate") {
        userStates[chatId] = { type: 'WAITING_DEVICE_CODE' };
        await sendMessage(chatId, `🐱 <b>Envía tu Código de Dispositivo</b> que figura en los Ajustes de MichiTV (Ejemplo: <code>MICHI-78B2</code>):`);
    } else if (data === "action_devices") {
        await showUserDevices(chatId, userId, username);
    } else if (data === "action_redeem") {
        userStates[chatId] = { type: 'WAITING_VOUCHER_CODE' };
        await sendMessage(chatId, `🎟 <b>Envía tu Código Voucher</b> para canjear (Ejemplo: <code>MICHI-V-30D-XXXX-YYYY</code>):`);
    } else if (data === "action_status") {
        userStates[chatId] = { type: 'WAITING_STATUS_CODE' };
        await sendMessage(chatId, `🔍 <b>Envía tu código de dispositivo</b> para inspeccionar su vigencia (Ej: <code>MICHI-78B2</code>):`);
    } else if (data === "action_admin" && isAdmin) {
        await showAdminPanel(chatId);
    } else if (data === "admin_v30" && isAdmin) {
        const v = generateVoucherCode(30);
        db.vouchers[v] = { days: 30, plan: "VIP 30 Días", created_at: new Date().toISOString(), used: false };
        saveDB();
        await sendRandomCatSticker(chatId);
        await sendMessage(chatId, `🎟 <b>Voucher 30 Días Creado:</b>\n<code>${v}</code>`);
    } else if (data === "admin_v90" && isAdmin) {
        const v = generateVoucherCode(90);
        db.vouchers[v] = { days: 90, plan: "VIP 90 Días (Trimestral)", created_at: new Date().toISOString(), used: false };
        saveDB();
        await sendRandomCatSticker(chatId);
        await sendMessage(chatId, `🎟 <b>Voucher 90 Días Creado:</b>\n<code>${v}</code>`);
    } else if (data === "admin_v365" && isAdmin) {
        const v = generateVoucherCode(365);
        db.vouchers[v] = { days: 365, plan: "VIP Anual (1 Año)", created_at: new Date().toISOString(), used: false };
        saveDB();
        await sendRandomCatSticker(chatId);
        await sendMessage(chatId, `🎟 <b>Voucher Anual 365 Días Creado:</b>\n<code>${v}</code>`);
    } else if (data.startsWith("redeem_direct_")) {
        const vCode = data.replace("redeem_direct_", "");
        userStates[chatId] = { type: 'WAITING_VOUCHER_DEVICE', voucher: vCode };
        await sendMessage(chatId, `🐱 <b>Ahora envía el código de tu pantalla</b> (Ej: <code>MICHI-78B2</code>) para vincularle este voucher de regalo:`);
    } else if (data.startsWith("status_")) {
        const devCode = data.replace("status_", "");
        await processCheckStatus(chatId, devCode);
    }
}

// Long Polling de Telegram
let lastUpdateId = 0;

async function pollUpdates() {
    try {
        const updates = await tgCall('getUpdates', {
            offset: lastUpdateId + 1,
            timeout: 25
        });

        for (const update of updates) {
            lastUpdateId = update.update_id;
            if (update.message) {
                await handleMessage(update.message);
            } else if (update.callback_query) {
                await handleCallback(update.callback_query);
            }
        }
    } catch (err) {
        console.error("Aviso de conexión Telegram:", err.message);
        await new Promise(r => setTimeout(r, 4000));
    }
    setImmediate(pollUpdates);
}

// Servidor HTTP Embebido (Descarga Directa de APK y API REST)
const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);

    // Endpoint de descarga directa del APK
    if (url.pathname === '/download' || url.pathname === '/download/michitv.apk') {
        const apkPath = getApkPath();
        if (apkPath && fs.existsSync(apkPath)) {
            const stat = fs.statSync(apkPath);
            res.writeHead(200, {
                'Content-Type': 'application/vnd.android.package-archive',
                'Content-Length': stat.size,
                'Content-Disposition': 'attachment; filename="MichiTV-Cinema-v2.0.0.apk"'
            });
            const stream = fs.createReadStream(apkPath);
            stream.pipe(res);
            return;
        } else {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end("El archivo APK aún no está compilado en el servidor.");
            return;
        }
    }

    // Endpoint de configuracion remota para la app (mantenimiento + cuenta regresiva)
    if (url.pathname === '/api/config') {
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Access-Control-Allow-Origin', '*');
        // Apagado automatico cuando se cumple el temporizador
        if (db.config && db.config.maintenance && db.config.maintenance_until) {
            if (Date.now() >= db.config.maintenance_until) {
                db.config.maintenance = false;
                db.config.maintenance_until = null;
                saveDB();
            }
        }
        const maintenance = !!(db.config && db.config.maintenance);
        const until = (db.config && db.config.maintenance_until) || null;
        const timeLeft = (maintenance && until) ? Math.max(0, until - Date.now()) : null;
        res.writeHead(200);
        res.end(JSON.stringify({
            maintenance: maintenance,
            message: (db.config && db.config.maintenance_message) || "Estamos mejorando MichiTV. Volvemos en minutos.",
            maintenance_until: until,
            timeLeft: timeLeft
        }));
        return;
    }

    // Endpoint de verificación de estado para la app
    if (url.pathname === '/api/status') {
        const deviceCode = (url.searchParams.get('device') || '').toUpperCase();
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Access-Control-Allow-Origin', '*');

        if (db.devices[deviceCode]) {
            const dev = db.devices[deviceCode];
            const isExpired = new Date(dev.expires_at) <= new Date();
            res.writeHead(200);
            res.end(JSON.stringify({
                found: true,
                deviceCode: dev.device_code,
                isActivated: dev.activated && !isExpired,
                planName: dev.plan,
                expiresAt: dev.expires_at,
                licenseKey: dev.license_key,
                tg_username: dev.tg_username || null
            }));
        } else {
            res.writeHead(200);
            res.end(JSON.stringify({
                found: false,
                isActivated: false,
                planName: "Plan Gratuito MichiTV"
            }));
        }
        return;
    }

    // Endpoint de activación directa vía API REST (para auto-pairing instantáneo)
    if (url.pathname === '/api/activate') {
        let deviceCode = (url.searchParams.get('device') || '').toUpperCase().trim();
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Access-Control-Allow-Origin', '*');

        if (deviceCode) {
            if (!deviceCode.startsWith('MICHI-') && deviceCode.length === 4) {
                deviceCode = `MICHI-${deviceCode}`;
            }

            if (db.devices[deviceCode]) {
                const existingDev = db.devices[deviceCode];
                const isExpired = new Date(existingDev.expires_at) <= new Date();
                
                if (!isExpired) {
                    res.writeHead(200);
                    res.end(JSON.stringify({
                        success: true,
                        deviceCode: existingDev.device_code,
                        isActivated: true,
                        planName: existingDev.plan,
                        licenseKey: existingDev.license_key,
                        message: "Device was already active."
                    }));
                    return;
                }
            }

            const licenseKey = generateLicenseKey(deviceCode);
            const expires = new Date();
            expires.setDate(expires.getDate() + (config.default_duration_days || 30));

            db.devices[deviceCode] = {
                device_code: deviceCode,
                license_key: licenseKey,
                activated: true,
                activated_at: new Date().toISOString(),
                expires_at: expires.toISOString(),
                plan: "VIP MichiTV Pass (Auto-Pairing)",
                tg_user_id: 0,
                tg_username: "Auto-Pairing"
            };
            saveDB();

            res.writeHead(200);
            res.end(JSON.stringify({
                success: true,
                deviceCode: deviceCode,
                isActivated: true,
                planName: "VIP MichiTV Pass (Auto-Pairing)",
                licenseKey: licenseKey
            }));
        } else {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Parámetro 'device' requerido" }));
        }
        return;
    }

    // Ping / Info
    if (url.pathname === '/' || url.pathname === '/ping') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: "online",
            bot: `@${config.bot_username}`,
            service: "MichiTV Licensing Quantum Server",
            version: "2.0.0",
            devices_registered: Object.keys(db.devices).length,
            users_registered: Object.keys(db.users).length,
            apk_available: !!getApkPath()
        }));
        return;
    }

    res.writeHead(404);
    res.end();
});

// --- SISTEMA DE NOTIFICACIONES DE EXPIRACIÓN ---
async function checkExpirations() {
    const now = new Date();
    const IN_2_DAYS = new Date(now.getTime() + (48 * 60 * 60 * 1000));
    
    for (const code in db.devices) {
        const dev = db.devices[code];
        if (!dev.tg_user_id || dev.tg_user_id === 0) continue; // Skip auto-paired ones without a user
        
        const expiresAt = new Date(dev.expires_at);
        if (expiresAt > now && expiresAt <= IN_2_DAYS) {
            // Avoid sending too many notifications if we already notified
            if (!dev.notified_expiration) {
                try {
                    await sendMessage(dev.tg_user_id, `⚠️ <b>¡Alerta Gatuna!</b> 🐾\n\nTu dispositivo <code>${dev.device_code}</code> está a menos de 48 horas de expirar su VIP (Vence el ${expiresAt.toLocaleDateString('es-ES')}).\n\n¡Gira la Ruleta o canjea un nuevo Voucher para no perder tus beneficios!`, {
                        reply_markup: {
                            inline_keyboard: [
                                [{ text: "🎟 Canjear Voucher", callback_data: "action_redeem" }],
                                [{ text: "🎰 Girar Ruleta", callback_data: "action_roulette" }]
                            ]
                        }
                    });
                    dev.notified_expiration = true;
                    saveDB();
                } catch (e) {
                    console.error("Error notificando expiración:", e.message);
                }
            }
        }
    }
}
setInterval(checkExpirations, 12 * 60 * 60 * 1000); // Check twice a day

// Inicialización del Servidor y Bot
const PORT = config.server_port || 3000;
server.listen(PORT, async () => {
    console.log(`=======================================================`);
    console.log(`🐾 MichiTV Super-Bot & APK Server: @${config.bot_username} Activo 🐾`);
    console.log(`🌐 Servidor HTTP: http://localhost:${PORT}`);
    console.log(`📦 Descarga APK Directa: http://localhost:${PORT}/download/michitv.apk`);
    console.log(`=======================================================`);

    await setupBotProfile();
    console.log(`🚀 Escuchando en vivo desde Telegram con APK, QR, Ruleta y Stickers...`);
    pollUpdates();
});
