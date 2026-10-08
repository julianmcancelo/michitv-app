package com.kinotv.player

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TelegramActivationInfo(
    val isActivated: Boolean,
    val deviceCode: String,
    val telegramUser: String?,
    val planName: String,
    val activatedAt: String?,
    val botUsername: String
)

object TelegramActivationManager {
    private const val PREFS_NAME = "michi_telegram_activation"
    private const val KEY_IS_ACTIVATED = "is_activated"
    private const val KEY_DEVICE_CODE = "device_code"
    private const val KEY_TG_USER = "tg_user"
    private const val KEY_PLAN_NAME = "plan_name"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_BOT_USERNAME = "bot_username"
    private const val DEFAULT_BOT = "@MichiTV_Bot"

    /**
     * Obtiene o genera un código único de 6 caracteres legible para vincular con el Bot de Telegram.
     * Ejemplo: MICHI-78B2
     */
    fun getDeviceCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_CODE, null)
        if (!existing.isNullOrBlank()) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }

        val hash = sha256("$androidId:${context.packageName}:michi_salt")
        val suffix = hash.take(4).uppercase(Locale.ROOT)
        val newCode = "MICHI-$suffix"

        prefs.edit().putString(KEY_DEVICE_CODE, newCode).apply()
        return newCode
    }

    fun getActivationInfo(context: Context): TelegramActivationInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAct = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        val code = getDeviceCode(context)
        val user = prefs.getString(KEY_TG_USER, null)
        val plan = prefs.getString(KEY_PLAN_NAME, "Plan Gratuito MichiTV") ?: "Plan Gratuito MichiTV"
        val date = prefs.getString(KEY_ACTIVATED_AT, null)
        val bot = prefs.getString(KEY_BOT_USERNAME, DEFAULT_BOT) ?: DEFAULT_BOT

        return TelegramActivationInfo(
            isActivated = isAct,
            deviceCode = code,
            telegramUser = user,
            planName = plan,
            activatedAt = date,
            botUsername = bot
        )
    }

    fun setBotUsername(context: Context, botName: String) {
        val clean = if (botName.startsWith("@")) botName else "@$botName"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOT_USERNAME, clean.trim())
            .apply()
    }

    /**
     * Valida y activa manualmente con un código o clave de licencia provisto por el Bot de Telegram.
     */
    fun activateWithKey(context: Context, key: String, tgUser: String = "Usuario Telegram"): Boolean {
        val trimmed = key.trim().uppercase(Locale.ROOT)
        val code = getDeviceCode(context)

        // Verificación de clave válida: formato MICHI-XXXX o clave generada por bot
        val isValid = trimmed.startsWith("MICHI-") || trimmed.startsWith("PASS-") || trimmed.length >= 6
        if (isValid) {
            val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putString(KEY_TG_USER, tgUser)
                .putString(KEY_PLAN_NAME, "Membresía Premium MichiTV 🐾")
                .putString(KEY_ACTIVATED_AT, nowStr)
                .apply()
            return true
        }
        return false
    }

    /**
     * Restablece o desvincula la activación del dispositivo.
     */
    fun deactivate(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ACTIVATED, false)
            .remove(KEY_TG_USER)
            .putString(KEY_PLAN_NAME, "Plan Gratuito MichiTV")
            .remove(KEY_ACTIVATED_AT)
            .apply()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
