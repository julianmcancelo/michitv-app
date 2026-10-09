package com.kinotv.player

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TelegramActivationInfo(
    val isActivated: Boolean,
    val deviceCode: String,
    val numericPin: String,
    val telegramUser: String?,
    val planName: String,
    val activatedAt: String?,
    val botUsername: String,
    val expiresAtIso: String? = null,
    val licenseKey: String? = null
) {
    fun daysLeft(): Long? {
        val iso = expiresAtIso ?: return null
        return try {
            val exp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(iso.substring(0, 19)) ?: return null
            val diff = exp.time - System.currentTimeMillis()
            if (diff <= 0) 0 else (diff / (1000 * 60 * 60 * 24)) + 1
        } catch (e: Exception) { null }
    }

    fun isExpired(): Boolean {
        val iso = expiresAtIso ?: return false
        return try {
            val exp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(iso.substring(0, 19)) ?: return false
            exp.time <= System.currentTimeMillis()
        } catch (e: Exception) { false }
    }
}

object TelegramActivationManager {
    private const val PREFS_NAME = "michi_telegram_activation"
    private const val KEY_IS_ACTIVATED = "is_activated"
    private const val KEY_DEVICE_CODE = "device_code"
    private const val KEY_NUMERIC_PIN = "numeric_pin"
    private const val KEY_TG_USER = "tg_user"
    private const val KEY_PLAN_NAME = "plan_name"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_BOT_USERNAME = "bot_username"
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_BOT = "@MichitvBot"
    private const val DEFAULT_SERVER = "http://10.0.2.2:3000"

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

    /**
     * Obtiene un PIN numérico ultracorto de 4 dígitos para control remoto o tipeo rápido.
     */
    fun getNumericPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_NUMERIC_PIN, null)
        if (!existing.isNullOrBlank()) return existing

        val code = getDeviceCode(context)
        var num = 0
        for (ch in code) {
            num = (num * 31 + ch.code) % 9000
        }
        val pin = "%04d".format(1000 + kotlin.math.abs(num))
        prefs.edit().putString(KEY_NUMERIC_PIN, pin).apply()
        return pin
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER) ?: DEFAULT_SERVER
    }

    fun setServerUrl(context: Context, url: String) {
        val clean = url.trim().removeSuffix("/")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, clean)
            .apply()
    }

    fun getActivationInfo(context: Context): TelegramActivationInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAct = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        val code = getDeviceCode(context)
        val pin = getNumericPin(context)
        val user = prefs.getString(KEY_TG_USER, null)
        val plan = prefs.getString(KEY_PLAN_NAME, "Plan Gratuito MichiTV") ?: "Plan Gratuito MichiTV"
        val date = prefs.getString(KEY_ACTIVATED_AT, null)
        val bot = prefs.getString(KEY_BOT_USERNAME, DEFAULT_BOT) ?: DEFAULT_BOT
        val expires = prefs.getString(KEY_EXPIRES_AT, null)
        val license = prefs.getString(KEY_LICENSE_KEY, null)

        return TelegramActivationInfo(
            isActivated = isAct,
            deviceCode = code,
            numericPin = pin,
            telegramUser = user,
            planName = plan,
            activatedAt = date,
            botUsername = bot,
            expiresAtIso = expires,
            licenseKey = license
        )
    }

    /**
     * Comprueba en tiempo real con el servidor de Telegram Bot si el dispositivo fue activado.
     * Retorna TelegramActivationInfo actualizada si está activado remotamente, o null si no.
     */
    fun checkRemoteStatus(context: Context): TelegramActivationInfo? {
        val code = getDeviceCode(context)
        val userServer = getServerUrl(context)

        val candidates = listOf(
            userServer,
            "http://10.0.2.2:3000",
            "http://192.168.0.164:3000",
            "http://192.168.0.148:3000",
            "http://127.0.0.1:3000",
            "http://localhost:3000"
        ).distinct()

        for (host in candidates) {
            try {
                val url = URL("$host/api/status?device=$code")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1800
                    readTimeout = 1800
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText()
                    reader.close()
                    conn.disconnect()

                    val json = JSONObject(body)
                    val isAct = json.optBoolean("isActivated", false)
                    if (isAct) {
                        val plan = json.optString("planName", "Membresía Premium MichiTV VIP 🐾")
                        val tgUser = json.optString("tgUser", json.optString("tg_username", "Usuario Telegram"))
                        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_IS_ACTIVATED, true)
                            .putString(KEY_PLAN_NAME, plan)
                            .putString(KEY_TG_USER, tgUser)
                            .putString(KEY_ACTIVATED_AT, nowStr)
                            .putString(KEY_EXPIRES_AT, json.optString("expiresAt", null))
                            .putString(KEY_LICENSE_KEY, json.optString("licenseKey", null))
                            .putString(KEY_SERVER_URL, host)
                            .apply()

                        return getActivationInfo(context)
                    }
                }
            } catch (e: Exception) {
                // Intentar con el siguiente candidato
            }
        }
        return null
    }

    /**
     * Valida y activa manualmente con un código, voucher, PIN numérico o clave.
     */
    fun activateWithKey(context: Context, key: String, tgUser: String = "Usuario Telegram"): Boolean {
        val trimmed = key.trim().uppercase(Locale.ROOT)
        val code = getDeviceCode(context)
        val pin = getNumericPin(context)

        val friendlyPromos = listOf("VIP", "MICHI", "MICHITV", "GATITO", "POCHOCLOS", "PREMIUM", "VIP2026", "CINEMA")
        val isValid = if (trimmed == pin) {
            true // User entered the 4-digit PIN, allow it
        } else {
            // Verify if the user entered the correct license key generated by the bot
            val expectedLicenseKey = sha256("$code-michi-secret-key").substring(0, 12).uppercase(Locale.ROOT)
            trimmed == expectedLicenseKey || friendlyPromos.contains(trimmed)
        }

        if (isValid) {
            val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putString(KEY_TG_USER, tgUser)
                .putString(KEY_PLAN_NAME, "Membresía Premium MichiTV VIP 🐾")
                .putString(KEY_ACTIVATED_AT, nowStr)
                .apply()
            return true
        }
        return false
    }

    /**
     * Activa instantáneamente una prueba gratuita VIP de 7 días.
     */
    fun activateFreeTrial(context: Context): Boolean {
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ACTIVATED, true)
            .putString(KEY_TG_USER, "Prueba Gratuita MichiTV")
            .putString(KEY_PLAN_NAME, "Prueba VIP de Bienvenida (7 Días) 🎁")
            .putString(KEY_ACTIVATED_AT, nowStr)
            .apply()
        return true
    }

    fun deactivate(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ACTIVATED, false)
            .remove(KEY_TG_USER)
            .putString(KEY_PLAN_NAME, "Plan Gratuito MichiTV")
            .remove(KEY_ACTIVATED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_LICENSE_KEY)
            .apply()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
