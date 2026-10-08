package com.kinotv.player

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "Kino Community",
    val iconAssetPath: String? = null,
    val capabilities: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    var isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val rootDir: File? = null
)

data class CatalogPluginItem(
    val id: String,
    val name: String,
    val description: String = "",
    val repo: String? = null,
    val stremioUrl: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    var isInstalled: Boolean = false
)

object PluginManager {
    private const val PREFS_NAME = "kino_plugins_prefs"
    private const val KEY_ENABLED_PREFIX = "plugin_enabled_"
    private val client = OkHttpClient()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPluginEnabled(context: Context, pluginId: String): Boolean {
        // Por defecto FuegoCine, Latino e IPTV-Org vienen activados para dar máxima experiencia
        val defaultVal = true
        return getPrefs(context).getBoolean(KEY_ENABLED_PREFIX + pluginId, defaultVal)
    }

    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED_PREFIX + pluginId, enabled).apply()
    }

    suspend fun getInstalledPlugins(context: Context): List<PluginInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PluginInfo>()

        // 1. Plugins empaquetados en assets/plugins/
        try {
            val assetPlugins = context.assets.list("plugins") ?: emptyArray()
            for (pId in assetPlugins) {
                try {
                    val manifestStr = context.assets.open("plugins/$pId/kino-plugin.json").bufferedReader().use { it.readText() }
                    val obj = JSONObject(manifestStr)
                    val caps = mutableListOf<String>()
                    val capsArr = obj.optJSONArray("capabilities")
                    if (capsArr != null) {
                        for (i in 0 until capsArr.length()) caps.add(capsArr.getString(i))
                    }
                    val cats = mutableListOf<String>()
                    val catsArr = obj.optJSONArray("categories")
                    if (catsArr != null) {
                        for (i in 0 until catsArr.length()) cats.add(catsArr.getString(i))
                    }

                    val info = PluginInfo(
                        id = pId,
                        name = obj.optString("name", pId.replaceFirstChar { it.uppercase() }),
                        version = obj.optString("version", "1.0.0"),
                        description = obj.optString("description", ""),
                        author = obj.optString("author", "Kino"),
                        iconAssetPath = "plugins/$pId/icon.png",
                        capabilities = caps,
                        categories = cats,
                        isEnabled = isPluginEnabled(context, pId),
                        isCustom = false
                    )
                    list.add(info)
                } catch (e: Exception) {
                    // Si no tiene kino-plugin.json, ignorar
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Plugins instalados por el usuario en almacenamiento local
        val customDir = File(context.filesDir, "custom_plugins")
        if (customDir.exists() && customDir.isDirectory) {
            customDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val manifestFile = File(dir, "kino-plugin.json")
                    if (manifestFile.exists()) {
                        try {
                            val obj = JSONObject(manifestFile.readText())
                            val pId = obj.optString("id", dir.name)
                            val info = PluginInfo(
                                id = pId,
                                name = obj.optString("name", dir.name),
                                version = obj.optString("version", "1.0.0"),
                                description = obj.optString("description", ""),
                                author = obj.optString("author", "Personalizado"),
                                iconAssetPath = null,
                                capabilities = listOf("home", "resolve", "search"),
                                categories = listOf("custom"),
                                isEnabled = isPluginEnabled(context, pId),
                                isCustom = true,
                                rootDir = dir
                            )
                            list.add(info)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        list
    }

    suspend fun getAvailableCatalog(context: Context): List<CatalogPluginItem> = withContext(Dispatchers.IO) {
        val installedIds = getInstalledPlugins(context).map { it.id }.toSet()
        val result = mutableListOf<CatalogPluginItem>()

        try {
            val jsonStr = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("plugins") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val id = it.optString("id")
                val cats = mutableListOf<String>()
                val catsArr = it.optJSONArray("categories")
                if (catsArr != null) {
                    for (c in 0 until catsArr.length()) cats.add(catsArr.getString(c))
                }
                val tags = mutableListOf<String>()
                val tagsArr = it.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) tags.add(tagsArr.getString(t))
                }

                result.add(
                    CatalogPluginItem(
                        id = id,
                        name = it.optString("name", id.replaceFirstChar { it.uppercase() }),
                        description = it.optString("description", ""),
                        repo = if (it.has("repo")) it.optString("repo") else null,
                        stremioUrl = if (it.has("stremio")) it.optString("stremio") else null,
                        categories = cats,
                        tags = tags,
                        isInstalled = installedIds.contains(id)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

    suspend fun installPluginFromManifestUrl(context: Context, pluginUrl: String): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(pluginUrl).build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext Result.failure(Exception("Error al descargar: HTTP ${res.code}"))
            val body = res.body?.string() ?: return@withContext Result.failure(Exception("Respuesta vacía"))

            val obj = JSONObject(body)
            val id = obj.optString("id", "custom_" + System.currentTimeMillis())
            val name = obj.optString("name", id)

            val customDir = File(context.filesDir, "custom_plugins/$id")
            customDir.mkdirs()

            // Guardar manifest
            File(customDir, "kino-plugin.json").writeText(body)

            // Descargar entry js si viene una url absoluta, o intentar descargar de la misma base
            val entry = obj.optString("entry", "plugin.js")
            val jsUrl = if (entry.startsWith("http")) entry else {
                val base = pluginUrl.substringBeforeLast("/")
                "$base/$entry"
            }

            try {
                val jsReq = Request.Builder().url(jsUrl).build()
                val jsRes = client.newCall(jsReq).execute()
                if (jsRes.isSuccessful) {
                    val jsBody = jsRes.body?.string() ?: ""
                    File(customDir, "plugin.js").writeText(jsBody)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setPluginEnabled(context, id, true)
            val info = PluginInfo(
                id = id,
                name = name,
                version = obj.optString("version", "1.0.0"),
                description = obj.optString("description", ""),
                capabilities = listOf("home", "resolve", "search"),
                isEnabled = true,
                isCustom = true,
                rootDir = customDir
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomPlugin(context: Context, pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val customDir = File(context.filesDir, "custom_plugins/$pluginId")
        if (customDir.exists()) {
            customDir.deleteRecursively()
        } else {
            false
        }
    }
}
