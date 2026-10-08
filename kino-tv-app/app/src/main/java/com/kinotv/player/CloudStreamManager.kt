package com.kinotv.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class CloudStreamPlugin(
    val name: String,
    val internalName: String,
    val version: Int,
    val url: String,
    val author: String,
    val language: String,
    val tvTypes: List<String>,
    val description: String?,
    val iconUrl: String?,
    val isTorrent: Boolean,
    val isEnabled: Boolean
)

data class CloudStreamRepo(
    val name: String,
    val url: String,
    val pluginLists: List<String>
)

object CloudStreamManager {
    private const val TAG = "CloudStreamManager"
    private const val PREFS_NAME = "kino_cloudstream_repos"
    private const val REPOS_KEY = "saved_repos"
    private val client = NetworkHelper.okHttpClient

    /**
     * Resuelve y limpia URLs de CloudStream como cloudstreamrepo:// o https://cs.repo/?...
     */
    fun normalizeRepoUrl(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("cloudstreamrepo://")) {
            clean = "https://" + clean.removePrefix("cloudstreamrepo://")
        } else if (clean.startsWith("https://cs.repo/?")) {
            val query = clean.substringAfter("https://cs.repo/?")
            clean = if (query.startsWith("http")) query else "https://$query"
        }
        return clean
    }

    /**
     * Obtiene los repositorios guardados en SharedPreferences
     */
    fun getSavedRepos(context: Context): List<CloudStreamRepo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(REPOS_KEY, null) ?: return emptyList()
        val list = mutableListOf<CloudStreamRepo>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val listsArr = obj.optJSONArray("pluginLists") ?: JSONArray()
                val lists = mutableListOf<String>()
                for (j in 0 until listsArr.length()) lists.add(listsArr.getString(j))
                list.add(
                    CloudStreamRepo(
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        pluginLists = lists
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando repos guardados", e)
        }
        return list
    }

    /**
     * Guarda un nuevo repositorio de CloudStream
     */
    fun saveRepo(context: Context, repo: CloudStreamRepo) {
        val current = getSavedRepos(context).toMutableList()
        current.removeAll { it.url == repo.url }
        current.add(repo)
        val arr = JSONArray()
        current.forEach { r ->
            val obj = JSONObject()
            obj.put("name", r.name)
            obj.put("url", r.url)
            val listsArr = JSONArray()
            r.pluginLists.forEach { listsArr.put(it) }
            obj.put("pluginLists", listsArr)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(REPOS_KEY, arr.toString())
            .apply()
    }

    /**
     * Descarga e inspecciona un repositorio de CloudStream a partir de su URL
     */
    suspend fun fetchRepo(url: String): Pair<CloudStreamRepo?, List<CloudStreamPlugin>> = withContext(Dispatchers.IO) {
        val targetUrl = normalizeRepoUrl(url)
        try {
            val req = Request.Builder().url(targetUrl).build()
            val body = client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext Pair(null, emptyList())
                res.body?.string() ?: ""
            }

            if (body.isEmpty()) return@withContext Pair(null, emptyList())

            val plugins = mutableListOf<CloudStreamPlugin>()
            var repoName = "Repositorio CloudStream"
            val pluginListUrls = mutableListOf<String>()

            if (body.trim().startsWith("{")) {
                val json = JSONObject(body)
                repoName = json.optString("name", "Repositorio CloudStream")
                val lists = json.optJSONArray("pluginLists")
                if (lists != null) {
                    for (i in 0 until lists.length()) {
                        pluginListUrls.add(lists.getString(i))
                    }
                }
            } else if (body.trim().startsWith("[")) {
                // Es un plugins.json directo
                pluginListUrls.add(targetUrl)
            }

            // Descargar las listas de plugins
            for (pUrl in pluginListUrls) {
                try {
                    val pReq = Request.Builder().url(pUrl).build()
                    val pBody = client.newCall(pReq).execute().use { it.body?.string() ?: "" }
                    if (pBody.trim().startsWith("[")) {
                        val pArr = JSONArray(pBody)
                        for (j in 0 until pArr.length()) {
                            val pObj = pArr.getJSONObject(j)
                            val name = pObj.optString("name", "Sin nombre")
                            val internalName = pObj.optString("internalName", name)
                            val version = pObj.optInt("version", 1)
                            val pluginFileUrl = pObj.optString("url", "")
                            val author = pObj.optString("author", "Autor Desconocido")
                            val lang = pObj.optString("lang", "en").lowercase()
                            val status = pObj.optInt("status", 1)
                            val desc = if (pObj.has("description")) pObj.optString("description") else null
                            val icon = if (pObj.has("iconUrl")) pObj.optString("iconUrl") else null

                            val typesList = mutableListOf<String>()
                            val typesArr = pObj.optJSONArray("tvTypes")
                            if (typesArr != null) {
                                for (k in 0 until typesArr.length()) typesList.add(typesArr.getString(k))
                            }

                            val isTorrent = typesList.any { it.equals("Torrent", ignoreCase = true) }
                            val isEnabled = status != 0 && !isTorrent

                            plugins.add(
                                CloudStreamPlugin(
                                    name = name,
                                    internalName = internalName,
                                    version = version,
                                    url = pluginFileUrl,
                                    author = author,
                                    language = lang,
                                    tvTypes = typesList,
                                    description = desc,
                                    iconUrl = icon,
                                    isTorrent = isTorrent,
                                    isEnabled = isEnabled
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error descargando pluginList: $pUrl", e)
                }
            }

            // Ordenar: primero instalables, luego español, luego alfabéticamente
            val sorted = plugins.sortedWith(
                compareByDescending<CloudStreamPlugin> { it.isEnabled }
                    .thenByDescending { isSpanish(it.language) }
                    .thenBy { it.name }
            )

            val repo = CloudStreamRepo(
                name = repoName,
                url = targetUrl,
                pluginLists = pluginListUrls
            )

            Pair(repo, sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching CloudStream repo $url", e)
            Pair(null, emptyList())
        }
    }

    private fun isSpanish(lang: String): Boolean {
        val l = lang.lowercase()
        return l == "es" || l.startsWith("es-") || l.startsWith("es_") || l == "mx" || l == "lat" || l == "latino"
    }
}
