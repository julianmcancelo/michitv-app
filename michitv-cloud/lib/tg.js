/**
 * Helpers de Telegram API (sin dependencias, fetch nativo).
 */

function apiBase() {
  const token = process.env.BOT_TOKEN;
  if (!token) throw new Error('Falta BOT_TOKEN en las variables de entorno.');
  return `https://api.telegram.org/bot${token}`;
}

async function tgCall(method, body = {}) {
  const res = await fetch(`${apiBase()}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.description || 'Error en Telegram API');
  return data.result;
}

async function sendMessage(chatId, text, extra = {}) {
  return tgCall('sendMessage', { chat_id: chatId, text, parse_mode: 'HTML', ...extra });
}

async function sendChatAction(chatId, action = 'typing') {
  try {
    await tgCall('sendChatAction', { chat_id: chatId, action });
  } catch (e) {}
}

async function editMessageText(chatId, messageId, text, extra = {}) {
  return tgCall('editMessageText', { chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', ...extra });
}

async function answerCallbackQuery(callbackQueryId, text = '') {
  return tgCall('answerCallbackQuery', { callback_query_id: callbackQueryId, text });
}

const CAT_STICKERS = [
  'CAACAgQAAxUAAWrIBi8sDiuAo2YgOTeZmq2TR399AAIeDQACZiCxUeZJ5XdhkD7EPQQ',
  'CAACAgQAAxUAAWrIBi_F4bz0scr5H0pw7qLv51gYAALmCwAC5tO4Ud7yc8umEFMyPQQ',
  'CAACAgQAAxUAAWrIBi8XKiedPSh2hY52-nDM5aDRAAIwDQACifqxUYIhZ4fg-7qiPQQ',
  'CAACAgQAAxUAAWrIBi-6VhXZ3cl3jm0lRb7ws4O6AAIQCwAC9rewUZ8mOZT4oJTdPQQ',
  'CAACAgQAAxUAAWrIBi_hBunnm0hU8XPYPnqVB8SIAAKXAANaW7oChB0yK-tGTdA9BA',
  'CAACAgQAAxUAAWrIBi9SQpI5-VuyTo8QAlWppPeKAAKZAANaW7oCOTmgh0oC6Hg9BA',
];

async function sendRandomCatSticker(chatId) {
  try {
    const s = CAT_STICKERS[Math.floor(Math.random() * CAT_STICKERS.length)];
    await tgCall('sendSticker', { chat_id: chatId, sticker: s });
  } catch (e) {
    console.warn('sticker:', e.message);
  }
}

module.exports = { tgCall, sendMessage, sendChatAction, editMessageText, answerCallbackQuery, sendRandomCatSticker };
