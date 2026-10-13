package com.kinotv.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteConfig(
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "Estamos mejorando MichiTV. Volvemos en minutos.",
    val timeLeftMs: Long? = null,
    val announcementId: String? = null,
    val announcementMessage: String? = null
)

object RemoteConfigManager {
    private const val PREFS = "michi_remote_config"
    private const val KEY_SERVER = "server_url_override"

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER, null)
            ?: TelegramActivationManager.getServerUrl(context)
    }

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SERVER, url.trim().removeSuffix("/")).apply()
    }

    private fun candidateHosts(context: Context): List<String> {
        return listOf(
            getServerUrl(context),
            TelegramActivationManager.getServerUrl(context),
            "https://michitv-cloud.vercel.app",
            "http://10.0.2.2:3000",
            "http://192.168.0.25:3000",
            "http://192.168.0.164:3000",
            "http://192.168.0.148:3000",
            "http://127.0.0.1:3000",
            "http://localhost:3000"
        ).map { it.trim().removeSuffix("/") }.distinct()
    }

    suspend fun fetchConfig(context: Context): RemoteConfig {
        return withContext(Dispatchers.IO) {
            for (host in candidateHosts(context)) {
                try {
                    val url = URL("$host/api/config")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2500
                        readTimeout = 2500
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode == 200) {
                        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                        conn.disconnect()
                        val json = JSONObject(jsonStr)
                        val tl = if (json.has("timeLeft") && !json.isNull("timeLeft")) {
                            json.optLong("timeLeft")
                        } else null
                        val announcementObj = json.optJSONObject("announcement")
                        val aId = announcementObj?.optString("id")?.takeIf { it.isNotBlank() }
                        val aMsg = announcementObj?.optString("message")?.takeIf { it.isNotBlank() }
                        val aActive = announcementObj?.optBoolean("active", true) ?: true

                        return@withContext RemoteConfig(
                            maintenance = json.optBoolean("maintenance", false),
                            maintenanceMessage = json.optString(
                                "message",
                                json.optString("maintenance_message", "Estamos mejorando MichiTV. Volvemos en minutos.")
                            ),
                            timeLeftMs = tl,
                            announcementId = if (aActive) aId else null,
                            announcementMessage = if (aActive) aMsg else null
                        )
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // Probar con el siguiente host
                }
            }
            RemoteConfig()
        }
    }
}
