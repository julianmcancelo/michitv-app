package com.kinotv.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val htmlUrl: String
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progressPercent: Int) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "michi_app_updates"
    private const val KEY_GITHUB_OWNER = "github_owner"
    private const val KEY_GITHUB_REPO = "github_repo"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"

    // Repositorio predeterminado (fácilmente personalizable en Ajustes)
    private const val DEFAULT_OWNER = "Julian"
    private const val DEFAULT_REPO = "MichiTV"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState

    fun getGithubOwner(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GITHUB_OWNER, DEFAULT_OWNER) ?: DEFAULT_OWNER
    }

    fun getGithubRepo(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun setGithubRepoConfig(context: Context, owner: String, repo: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GITHUB_OWNER, owner.trim())
            .putString(KEY_GITHUB_REPO, repo.trim())
            .apply()
    }

    fun isAutoCheckEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHECK, true)
    }

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHECK, enabled)
            .apply()
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Comprueba inteligentemente si existe una nueva release en GitHub.
     */
    suspend fun checkForUpdates(context: Context, force: Boolean = false): ReleaseInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        // Si no es forzado y se comprobó hace menos de 4 horas, saltar
        if (!force && (now - lastCheck < 4 * 60 * 60 * 1000L)) {
            return@withContext null
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        val owner = getGithubOwner(context)
        val repo = getGithubRepo(context)
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MichiTV-Android-App")
                .build()

            val response = NetworkHelper.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "No se pudo obtener release de GitHub: código ${response.code}")
                return@withContext null
            }

            val bodyStr = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyStr)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val changelog = json.optString("body", "Mejoras generales y corrección de errores.")
            val htmlUrl = json.optString("html_url", "")
            val publishedAt = json.optString("published_at", "")

            // Buscar asset .apk
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl.isNullOrEmpty()) {
                Log.w(TAG, "Release $tagName encontrada pero no contiene ningún archivo .apk adjunto.")
                return@withContext null
            }

            val currentVer = getCurrentVersionName(context)
            val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()
            val cleanCurrent = currentVer.removePrefix("v").removePrefix("V").trim()

            if (isNewerVersion(cleanRemote, cleanCurrent)) {
                Log.i(TAG, "¡Nueva versión disponible! Remota: $cleanRemote, Local: $cleanCurrent")
                return@withContext ReleaseInfo(
                    tagName = tagName,
                    versionName = cleanRemote,
                    releaseTitle = title,
                    changelog = changelog,
                    apkUrl = apkUrl,
                    apkSize = apkSize,
                    publishedAt = publishedAt,
                    htmlUrl = htmlUrl
                )
            } else {
                Log.i(TAG, "La aplicación está actualizada ($cleanCurrent >= $cleanRemote)")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando GitHub Releases", e)
            return@withContext null
        }
    }

    /**
     * Compara dos cadenas de versión semántica (ej. "1.1.0" > "1.0.4").
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val rParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val lParts = local.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        } catch (e: Exception) {
            return remote != local
        }
    }

    /**
     * Descarga el APK de la actualización con reporte de progreso en tiempo real.
     */
    suspend fun downloadUpdate(context: Context, apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0L, 0L, 0)

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "MichiTV-Actualizacion.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "MichiTV-Android-App")
                .build()

            val response = NetworkHelper.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = UpdateDownloadState.Error("Error al conectar con el servidor: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: run {
                _downloadState.value = UpdateDownloadState.Error("Respuesta vacía del servidor de descargas.")
                return@withContext null
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var lastPercent = 0

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _downloadState.value = UpdateDownloadState.Downloading(bytesDownloaded, totalBytes, percent)
                        }
                    }
                    output.flush()
                }
            }

            _downloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)
            return@withContext apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Fallo descargando la actualización de la app", e)
            _downloadState.value = UpdateDownloadState.Error("Fallo en la descarga: ${e.localizedMessage}")
            return@withContext null
        }
    }

    /**
     * Lanza el instalador nativo del sistema Android usando FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "El archivo APK a instalar no existe: ${apkFile.absolutePath}")
                return
            }

            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al lanzar instalador de APK", e)
            _downloadState.value = UpdateDownloadState.Error("Error al iniciar instalador: ${e.message}")
        }
    }

    fun resetState() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}
