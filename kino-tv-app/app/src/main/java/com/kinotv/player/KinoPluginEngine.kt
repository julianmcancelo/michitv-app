package com.kinotv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors

data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String? = null
)

object KinoPluginEngine {
    private const val TAG = "KinoPluginEngine"
    private val client = NetworkHelper.okHttpClient

    suspend fun resolveByName(context: Context, title: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace(Regex("""\(?\d{4}\)?"""), "").trim()
            // 1. Probar primero con FuegoCine
            val resultsFuego = search(context, "fuegocine", cleanTitle)
            if (resultsFuego.isNotEmpty()) {
                val hit = resultsFuego.first()
                if (!hit.ref.isNullOrEmpty()) {
                    val stream = resolveStream(context, "fuegocine", hit.ref)
                    if (stream != null) return@withContext stream
                }
            }
            // 2. Probar con Latino
            val resultsLatino = search(context, "latino", cleanTitle)
            if (resultsLatino.isNotEmpty()) {
                val hit = resultsLatino.first()
                if (!hit.ref.isNullOrEmpty()) {
                    val stream = resolveStream(context, "latino", hit.ref)
                    if (stream != null) return@withContext stream
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error en resolveByName para $title", e)
            null
        }
    }

    /**
     * Busca episodios de una serie por nombre (para fichas Cinemeta sin plugin).
     * Retorna el plugin que los proveyo junto con la lista ("" si no hay).
     */
    suspend fun getEpisodesByName(context: Context, name: String, year: String? = null): Pair<String, List<EpisodeItem>> = withContext(Dispatchers.IO) {
        val cleanName = name.replace(Regex("""\(?\d{4}\)?"""), "").trim()
        if (cleanName.isEmpty()) return@withContext "" to emptyList()
        // 1. FuegoCine primero (resolucion directa probada)
        try {
            val hits = search(context, "fuegocine", cleanName)
            val ordered = hits.filter { it.type == "series" }.ifEmpty { hits }.take(3)
            for (hit in ordered) {
                val ref = hit.ref ?: continue
                if (!Regex("""^\d{5,20}$""").matches(ref)) continue
                val eps = getEpisodes(context, "fuegocine", ref)
                if (eps.isNotEmpty()) return@withContext "fuegocine" to eps
            }
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodesByName fuegocine: $cleanName", e)
        }
        // 2. Latino como respaldo (refs s:{tmdbId} o de sitio)
        try {
            val hits = search(context, "latino", cleanName)
            val orderedLatino = hits.filter { it.type == "series" }.ifEmpty { hits }.take(3)
            for (hit in orderedLatino) {
                val ref = hit.ref ?: continue
                val eps = getEpisodes(context, "latino", ref)
                if (eps.isNotEmpty()) return@withContext "latino" to eps
            }
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodesByName latino: $cleanName", e)
        }
        "" to emptyList()
    }

    suspend fun getHomeRows(context: Context, pluginId: String): List<CatalogRow> = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            setupKinoBridge(context, quickJs, pluginId)
            val code = loadPluginCode(context, pluginId) ?: return@withContext emptyList()
            quickJs.evaluate<Any?>(code)

            val deferred = CompletableDeferred<String>()
            quickJs.function("__resolve_home") { args ->
                deferred.complete(args.firstOrNull()?.toString() ?: "[]")
            }
            quickJs.function("__reject_home") { args ->
                Log.w(TAG, "Error en JS home(): ${args.firstOrNull()}")
                deferred.complete("[]")
            }

            quickJs.evaluate<Any?>("""
                (async function() {
                    try {
                        var res = await home();
                        __resolve_home(JSON.stringify(res || []));
                    } catch(e) {
                        __reject_home(e ? (e.message || String(e)) : "error");
                    }
                })()
            """.trimIndent())

            val rawJson = withTimeoutOrNull(15000) { deferred.await() } ?: "[]"
            if (rawJson.isEmpty() || rawJson == "[]") return@withContext emptyList()

            val arr = JSONArray(rawJson)
            val rows = mutableListOf<CatalogRow>()
            for (i in 0 until arr.length()) {
                val rowObj = arr.getJSONObject(i)
                val title = rowObj.optString("title", "Catálogo")
                val itemsArr = rowObj.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<CatalogItem>()
                for (j in 0 until itemsArr.length()) {
                    val it = itemsArr.getJSONObject(j)
                    val parsedItem = parseCatalogItem(it, pluginId, j)
                    items.add(parsedItem)
                }
                if (items.isNotEmpty()) {
                    rows.add(CatalogRow(title, items))
                }
            }
            rows
        } catch (e: Exception) {
            Log.e(TAG, "Error en getHomeRows para plugin $pluginId", e)
            emptyList()
        } finally {
            quickJs.close()
            executor.shutdown()
        }
    }

    suspend fun search(context: Context, pluginId: String, query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            setupKinoBridge(context, quickJs, pluginId)
            val code = loadPluginCode(context, pluginId) ?: return@withContext emptyList()
            quickJs.evaluate<Any?>(code)

            val deferred = CompletableDeferred<String>()
            quickJs.function("__resolve_search") { args ->
                deferred.complete(args.firstOrNull()?.toString() ?: "[]")
            }
            quickJs.function("__reject_search") { args ->
                Log.w(TAG, "Error en JS search(): ${args.firstOrNull()}")
                deferred.complete("[]")
            }

            val escapedQuery = JSONObject.quote(query)
            quickJs.evaluate<Any?>("""
                (async function() {
                    try {
                        var res = await search($escapedQuery);
                        if (!res || (Array.isArray(res) && res.length === 0)) { try { res = await search({ q: $escapedQuery }); } catch (e2) {} }
                        __resolve_search(JSON.stringify(res || []));
                    } catch(e) {
                        __reject_search(e ? (e.message || String(e)) : "error");
                    }
                })()
            """.trimIndent())

            val rawJson = withTimeoutOrNull(12000) { deferred.await() } ?: "[]"
            if (rawJson.isEmpty() || rawJson == "[]") return@withContext emptyList()

            val arr = JSONArray(rawJson)
            val items = mutableListOf<CatalogItem>()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                items.add(parseCatalogItem(it, pluginId, i))
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error en search para plugin $pluginId: $query", e)
            emptyList()
        } finally {
            quickJs.close()
            executor.shutdown()
        }
    }

    suspend fun browse(context: Context, pluginId: String, ref: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            setupKinoBridge(context, quickJs, pluginId)
            val code = loadPluginCode(context, pluginId) ?: return@withContext emptyList()
            quickJs.evaluate<Any?>(code)

            val deferred = CompletableDeferred<String>()
            quickJs.function("__resolve_browse") { args ->
                deferred.complete(args.firstOrNull()?.toString() ?: "[]")
            }
            quickJs.function("__reject_browse") { args ->
                Log.w(TAG, "Error en JS browse(): ${args.firstOrNull()}")
                deferred.complete("[]")
            }

            val escapedRef = JSONObject.quote(ref)
            quickJs.evaluate<Any?>("""
                (async function() {
                    try {
                        var res = await browse($escapedRef);
                        if (res && res.items) {
                            __resolve_browse(JSON.stringify(res.items));
                        } else if (Array.isArray(res)) {
                            __resolve_browse(JSON.stringify(res));
                        } else {
                            __resolve_browse("[]");
                        }
                    } catch(e) {
                        __reject_browse(e ? (e.message || String(e)) : "error");
                    }
                })()
            """.trimIndent())

            val rawJson = withTimeoutOrNull(15000) { deferred.await() } ?: "[]"
            if (rawJson.isEmpty() || rawJson == "[]") return@withContext emptyList()

            val arr = JSONArray(rawJson)
            val items = mutableListOf<CatalogItem>()
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                items.add(parseCatalogItem(it, pluginId, i))
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error en browse para plugin $pluginId: $ref", e)
            emptyList()
        } finally {
            quickJs.close()
            executor.shutdown()
        }
    }

    suspend fun getEpisodes(context: Context, pluginId: String, ref: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            setupKinoBridge(context, quickJs, pluginId)
            val code = loadPluginCode(context, pluginId) ?: return@withContext emptyList()
            quickJs.evaluate<Any?>(code)

            val deferred = CompletableDeferred<String>()
            quickJs.function("__resolve_episodes") { args ->
                deferred.complete(args.firstOrNull()?.toString() ?: "[]")
            }
            quickJs.function("__reject_episodes") { args ->
                Log.w(TAG, "Error en JS episodes(): ${args.firstOrNull()}")
                deferred.complete("[]")
            }

            val escapedRef = JSONObject.quote(ref)
            quickJs.evaluate<Any?>("""
                (async function() {
                    try {
                        var res = await episodes($escapedRef);
                        __resolve_episodes(JSON.stringify(res || []));
                    } catch(e) {
                        __reject_episodes(e ? (e.message || String(e)) : "error");
                    }
                })()
            """.trimIndent())

            val rawJson = withTimeoutOrNull(15000) { deferred.await() } ?: "[]"
            val episodesList = mutableListOf<EpisodeItem>()

            if (rawJson.startsWith("{")) {
                val obj = JSONObject(rawJson)
                val arr = obj.optJSONArray("episodes") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val epObj = arr.getJSONObject(i)
                    val epRef = epObj.optString("ref", "${ref}_ep_$i")
                    val sNum = epObj.optInt("season", 1)
                    val epNum = epObj.optInt("number", i + 1)
                    val epTitle = epObj.optString("title", "Episodio $epNum")
                    episodesList.add(
                        EpisodeItem(
                            id = epRef,
                            name = epTitle,
                            season = sNum,
                            number = epNum,
                            overview = if (epObj.has("overview")) epObj.optString("overview") else null,
                            thumbnail = if (epObj.has("still")) epObj.optString("still") else if (epObj.has("thumbnail")) epObj.optString("thumbnail") else null,
                            ref = epRef,
                            pluginId = pluginId
                        )
                    )
                }
            } else if (rawJson.startsWith("[")) {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val epObj = arr.getJSONObject(i)
                    val epRef = epObj.optString("ref", "${ref}_ep_$i")
                    val sNum = epObj.optInt("season", 1)
                    val epNum = epObj.optInt("number", i + 1)
                    val epTitle = epObj.optString("title", "Episodio $epNum")
                    episodesList.add(
                        EpisodeItem(
                            id = epRef,
                            name = epTitle,
                            season = sNum,
                            number = epNum,
                            overview = if (epObj.has("overview")) epObj.optString("overview") else null,
                            thumbnail = if (epObj.has("still")) epObj.optString("still") else if (epObj.has("thumbnail")) epObj.optString("thumbnail") else null,
                            ref = epRef,
                            pluginId = pluginId
                        )
                    )
                }
            }
            episodesList
        } catch (e: Exception) {
            Log.e(TAG, "Error en getEpisodes para plugin $pluginId: $ref", e)
            emptyList()
        } finally {
            quickJs.close()
            executor.shutdown()
        }
    }

    suspend fun resolveStream(context: Context, pluginId: String, ref: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            setupKinoBridge(context, quickJs, pluginId)
            val code = loadPluginCode(context, pluginId) ?: return@withContext null
            quickJs.evaluate<Any?>(code)

            val deferred = CompletableDeferred<String?>()
            quickJs.function("__resolve_stream") { args ->
                deferred.complete(args.firstOrNull()?.toString())
            }
            quickJs.function("__reject_stream") { args ->
                Log.w(TAG, "Error en JS resolve(): ${args.firstOrNull()}")
                deferred.complete(null)
            }

            val escapedRef = JSONObject.quote(ref)
            quickJs.evaluate<Any?>("""
                (async function() {
                    try {
                        var res = await resolve($escapedRef);
                        __resolve_stream(JSON.stringify(res || null));
                    } catch(e) {
                        __reject_stream(e ? (e.message || String(e)) : "error");
                    }
                })()
            """.trimIndent())

            val rawResult = withTimeoutOrNull(18000) { deferred.await() }

            if (rawResult.isNullOrEmpty() || rawResult == "null") {
                Log.w(TAG, "resolveStream devolvió nulo para plugin $pluginId con ref $ref")
                return@withContext null
            }

            var streamUrl: String? = null
            val headersMap = mutableMapOf<String, String>()

            if (rawResult.startsWith("{")) {
                val obj = JSONObject(rawResult)
                streamUrl = if (obj.has("url")) obj.optString("url") else null
                val hObj = obj.optJSONObject("headers")
                if (hObj != null) {
                    for (k in hObj.keys()) {
                        headersMap[k] = hObj.getString(k)
                    }
                }
            } else if (rawResult.startsWith("\"")) {
                streamUrl = JSONObject("{\"u\":$rawResult}").getString("u")
            }

            if (streamUrl.isNullOrEmpty()) return@withContext null

            // Si es un embed de Voe, desencriptar a la URL .m3u8 real
            if (streamUrl.contains("voe.sx") || streamUrl.contains("/e/")) {
                val realM3u8 = StreamResolver.resolveVoe(streamUrl)
                if (!realM3u8.isNullOrEmpty()) {
                    streamUrl = realM3u8
                }
            }

            // Inyectar Referer y User-Agent por defecto si no están presentes
            val uri = Uri.parse(streamUrl)
            val host = uri.host
            if (!host.isNullOrEmpty()) {
                if (!headersMap.containsKey("Referer") && !headersMap.containsKey("referer")) {
                    headersMap["Referer"] = "${uri.scheme ?: "https"}://$host/"
                }
            }
            if (!headersMap.containsKey("User-Agent") && !headersMap.containsKey("user-agent")) {
                headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }

            Log.i(TAG, "Stream resuelto con éxito para $pluginId: $streamUrl con headers: $headersMap")

            ResolvedStream(
                url = streamUrl,
                headers = headersMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en resolveStream para plugin $pluginId con ref $ref", e)
            null
        } finally {
            quickJs.close()
            executor.shutdown()
        }
    }

    private fun parseCatalogItem(it: JSONObject, pluginId: String, index: Int): CatalogItem {
        val genresList = mutableListOf<String>()
        val gArr = it.optJSONArray("genres")
        if (gArr != null) {
            for (k in 0 until gArr.length()) genresList.add(gArr.getString(k))
        } else {
            val defaultGenre = when (pluginId) {
                "animeav1", "animeflvone" -> "Anime"
                "iptv-org", "caracol-tv" -> "En Vivo"
                else -> if (it.optString("kind") == "series" || it.optString("kind") == "tv") "Serie" else "Película"
            }
            genresList.add(defaultGenre)
        }

        val kind = it.optString("kind", if (pluginId.contains("anime")) "anime" else "movie")
        val normalizedKind = if (kind == "tv" || kind == "series") "series" else if (kind == "live") "live" else "movie"

        var posterUrl = if (it.has("poster")) it.optString("poster") else if (it.has("logo")) it.optString("logo") else null
        if (posterUrl.isNullOrEmpty() && it.has("cover")) posterUrl = it.optString("cover")
        if (posterUrl.isNullOrEmpty() && it.has("image")) posterUrl = it.optString("image")
        if (posterUrl.isNullOrEmpty() && it.has("thumbnail")) posterUrl = it.optString("thumbnail")
        if (posterUrl.isNullOrEmpty() && it.has("thumb")) posterUrl = it.optString("thumb")

        var bgUrl = if (it.has("backdrop")) it.optString("backdrop") else posterUrl
        if (bgUrl.isNullOrEmpty() && it.has("background")) bgUrl = it.optString("background")

        return CatalogItem(
            id = it.optString("id", "${pluginId}_$index"),
            name = it.optString("title", it.optString("name", "Sin título")),
            type = normalizedKind,
            poster = posterUrl,
            background = bgUrl,
            genres = genresList,
            description = it.optString("overview", it.optString("description", "")),
            year = if (it.has("year")) it.optString("year") else null,
            pluginId = pluginId,
            ref = if (it.has("ref")) it.optString("ref") else if (it.has("id")) it.optString("id") else null,
            rating = if (it.has("rating")) it.optDouble("rating") else null
        )
    }

    private fun loadPluginCode(context: Context, pluginId: String): String? {
        val raw = try {
            context.assets.open("plugins/$pluginId/plugin.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            val customFile = File(context.filesDir, "custom_plugins/$pluginId/plugin.js")
            if (customFile.exists()) {
                customFile.readText()
            } else {
                null
            }
        } ?: return null

        return cleanJsCode(raw)
    }

    private fun cleanJsCode(raw: String): String {
        return raw
            .replace(Regex("""export\s*\{[^}]*\};?"""), "")
            .replace(Regex("""export\s+default\s+"""), "")
            .replace(Regex("""export\s+async\s+function\s+"""), "async function ")
            .replace(Regex("""export\s+function\s+"""), "function ")
            .replace(Regex("""export\s+(const|let|var)\s+"""), "$1 ")
    }

    private suspend fun setupKinoBridge(context: Context, quickJs: QuickJs, pluginId: String) {
        val prefs = context.getSharedPreferences("kino_plugin_storage_$pluginId", Context.MODE_PRIVATE)

        quickJs.function("__native_log") { args ->
            val msg = args.joinToString(" ") { it?.toString() ?: "null" }
            Log.d("KinoPluginJS", "[$pluginId] $msg")
        }

        quickJs.function("__native_atob") { args ->
            var s = args.firstOrNull()?.toString() ?: ""
            try {
                s = s.replace('-', '+').replace('_', '/')
                val pad = (4 - s.length % 4) % 4
                String(Base64.getDecoder().decode(s + "=".repeat(pad)))
            } catch (e: Exception) {
                try {
                    String(Base64.getUrlDecoder().decode(s))
                } catch (e2: Exception) {
                    ""
                }
            }
        }

        quickJs.function("__native_btoa") { args ->
            val s = args.firstOrNull()?.toString() ?: ""
            try {
                Base64.getEncoder().encodeToString(s.toByteArray())
            } catch (e: Exception) {
                ""
            }
        }

        quickJs.function("__native_crypto_hash") { args ->
            val algo = args.getOrNull(0)?.toString()?.lowercase() ?: "sha1"
            val text = args.getOrNull(1)?.toString() ?: ""
            try {
                val digestName = when (algo) {
                    "md5" -> "MD5"
                    "sha256" -> "SHA-256"
                    else -> "SHA-1"
                }
                val md = MessageDigest.getInstance(digestName)
                val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        }

        quickJs.function("__native_storage_get") { args ->
            val key = args.firstOrNull()?.toString() ?: ""
            prefs.getString(key, null)
        }

        quickJs.function("__native_storage_set") { args ->
            val key = args.getOrNull(0)?.toString() ?: ""
            val value = args.getOrNull(1)?.toString() ?: ""
            prefs.edit().putString(key, value).apply()
            true
        }

        quickJs.function("__native_tmdb") { args ->
            val path = args.getOrNull(0)?.toString() ?: ""
            val paramsStr = args.getOrNull(1)?.toString() ?: "{}"
            try {
                TmdbEmulator.handleTmdbRequest(path, org.json.JSONObject(paramsStr))
            } catch (e: Exception) {
                "{\"results\":[]}"
            }
        }

        quickJs.function("__native_config_get") { args ->
            val key = args.firstOrNull()?.toString() ?: ""
            when (key) {
                "idioma" -> "lat"
                "servidor" -> "voe"
                "homeRows" -> "true"
                else -> ""
            }
        }

        quickJs.asyncFunction("kino_native_sleep") { args ->
            val ms = args.firstOrNull()?.toString()?.toLongOrNull() ?: 0L
            kotlinx.coroutines.delay(ms)
            true
        }

        quickJs.asyncFunction("kino_native_fetch") { args ->
            val url = args[0].toString()
            val optsStr = args.getOrNull(1)?.toString()
            val opts = if (!optsStr.isNullOrEmpty() && optsStr.startsWith("{")) JSONObject(optsStr) else JSONObject()

            val method = opts.optString("method", "GET").uppercase()
            val reqBuilder = Request.Builder().url(url)

            val headersObj = opts.optJSONObject("headers")
            var hasUa = false
            if (headersObj != null) {
                for (k in headersObj.keys()) {
                    val v = headersObj.getString(k)
                    reqBuilder.header(k, v)
                    if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                }
            }
            if (!hasUa) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }

            if (method == "POST" || method == "PUT") {
                val bodyRaw = if (opts.has("body")) opts.get("body") else null
                val bodyStr = when (bodyRaw) {
                    null, JSONObject.NULL -> ""
                    is String -> bodyRaw
                    else -> bodyRaw.toString()
                }
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                reqBuilder.method(method, bodyStr.toRequestBody(mediaType))
            } else {
                reqBuilder.method(method, null)
            }

            try {
                client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val out = JSONObject()
                    out.put("status", res.code)
                    out.put("ok", res.isSuccessful)
                    out.put("bodyText", body)

                    val respHeaders = JSONObject()
                    for (i in 0 until res.headers.size) {
                        respHeaders.put(res.headers.name(i).lowercase(), res.headers.value(i))
                    }
                    out.put("headers", respHeaders)

                    out.toString()
                }
            } catch (e: Exception) {
                val errOut = JSONObject()
                errOut.put("status", 500)
                errOut.put("ok", false)
                errOut.put("bodyText", "")
                errOut.put("headers", JSONObject())
                errOut.put("error", e.message ?: "Network error")
                errOut.toString()
            }
        }

        // Bridge setup in JavaScript con console, polyfill de URL y compatibilidad Kino
        quickJs.evaluate<Any?>("""
            globalThis.console = {
                log: function() { var a = Array.prototype.slice.call(arguments); __native_log(a.join(' ')); },
                error: function() { var a = Array.prototype.slice.call(arguments); __native_log('ERROR: ' + a.join(' ')); },
                warn: function() { var a = Array.prototype.slice.call(arguments); __native_log('WARN: ' + a.join(' ')); },
                info: function() { var a = Array.prototype.slice.call(arguments); __native_log('INFO: ' + a.join(' ')); }
            };

            if (typeof URL === 'undefined') {
                globalThis.URL = function(url, base) {
                    var str = String(url || '');
                    if (base && !/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(str)) {
                        var b = String(base);
                        if (str.startsWith('/')) {
                            var m = b.match(/^(https?:\/\/[^\/]+)/i);
                            str = (m ? m[1] : b) + str;
                        } else {
                            str = b.replace(/\/[^\/]*$/, '/') + str;
                        }
                    }
                    this.href = str;
                    var match = str.match(/^(https?:\/\/)?([^\/\?#]+)(.*)$/i);
                    if (match) {
                        var hostPart = match[2];
                        var hostPort = hostPart.split(':');
                        this.hostname = hostPort[0];
                        this.host = hostPart;
                        this.port = hostPort[1] || '';
                        this.origin = (match[1] || 'https://') + hostPart;
                        this.pathname = (match[3] || '').split('?')[0].split('#')[0] || '/';
                        var qIdx = str.indexOf('?');
                        var hIdx = str.indexOf('#');
                        this.search = qIdx >= 0 ? (hIdx >= 0 ? str.slice(qIdx, hIdx) : str.slice(qIdx)) : '';
                        this.hash = hIdx >= 0 ? str.slice(hIdx) : '';
                    } else {
                        this.hostname = '';
                        this.host = '';
                        this.port = '';
                        this.origin = '';
                        this.pathname = '';
                        this.search = '';
                        this.hash = '';
                    }
                };
            }

            globalThis.atob = function(s) { return __native_atob(s); };
            globalThis.btoa = function(s) { return __native_btoa(s); };

            // searchParams + toString para el polyfill de URL (lo usa caracol-tv)
            (function() {
                try {
                    var OrigURL = globalThis.URL;
                    var probe = null;
                    try { probe = new OrigURL('https://x.test/?a=1'); } catch (e) {}
                    if (probe && (!probe.searchParams || typeof probe.searchParams.set !== 'function')) {
                        globalThis.URL = function(url, base) {
                            var inst = new OrigURL(url, base);
                            var params = {};
                            var q = inst.search || '';
                            if (q.charAt(0) === '?') q = q.slice(1);
                            q.split('&').forEach(function(pair) {
                                if (!pair) return;
                                var idx = pair.indexOf('=');
                                var rk = idx >= 0 ? pair.slice(0, idx) : pair;
                                var rv = idx >= 0 ? pair.slice(idx + 1) : '';
                                try { params[decodeURIComponent(rk)] = decodeURIComponent(rv); }
                                catch (e2) { params[rk] = rv; }
                            });
                            function sync() {
                                var qs = Object.keys(params).map(function(k) {
                                    return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
                                }).join('&');
                                var baseHref = String(inst.href).split('?')[0].split('#')[0];
                                var hash = inst.hash || '';
                                inst.href = qs ? baseHref + '?' + qs + hash : baseHref + hash;
                                inst.search = qs ? '?' + qs : '';
                            }
                            inst.searchParams = {
                                set: function(k, v) { params[String(k)] = String(v); sync(); },
                                get: function(k) { var v = params[String(k)]; return v === undefined ? null : v; },
                                has: function(k) { return Object.prototype.hasOwnProperty.call(params, String(k)); },
                                append: function(k, v) { params[String(k)] = String(v); sync(); },
                                toString: function() {
                                    return Object.keys(params).map(function(k) {
                                        return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
                                    }).join('&');
                                }
                            };
                            inst.toString = function() { return inst.href; };
                            return inst;
                        };
                    } else if (probe && typeof probe.toString !== 'function') {
                        // URL nativa sin toString util: no se toca
                    }
                } catch (e) {
                    __native_log('URL patch: ' + (e && e.message || e));
                }
            })();

            globalThis.kino = {
                fetchAnyHost: true,
                manifest: {
                    streamHosts: "any",
                    fetchHosts: "any"
                },
                config: {
                    get: function(k) {
                        return __native_config_get(k);
                    }
                },
                crypto: {
                    hash: function(algo, text) {
                        return __native_crypto_hash(algo, text);
                    }
                },
                storage: {
                    get: async function(k) {
                        var v = __native_storage_get(k);
                        if (!v) return null;
                        try { return JSON.parse(v); } catch(e) { return v; }
                    },
                    set: async function(k, v) {
                        var s = typeof v === 'string' ? v : JSON.stringify(v);
                        __native_storage_set(k, s);
                    }
                },
                sleep: async function(ms) {
                    return await kino_native_sleep(ms);
                },
                error: function(code, msg) {
                    var e = new Error(code + ': ' + msg);
                    e.code = code;
                    return e;
                },
                log: function() {
                    var args = Array.prototype.slice.call(arguments);
                    __native_log(args.join(' '));
                },
                tmdb: async function(path, params) {
                    return JSON.parse(__native_tmdb(String(path || ''), JSON.stringify(params || {})));
                },
                cookies: {
                    get: function(url, name) { return null; },
                    set: function(url, name, value) {}
                }
            };

            globalThis.kino.fetch = async function(url, opts) {
                var optsJson = opts ? JSON.stringify(opts) : "{}";
                var raw = await kino_native_fetch(url, optsJson);
                var r = JSON.parse(raw);
                var rHeaders = r.headers || {};
                return {
                    status: r.status,
                    ok: r.ok,
                    text: async function() { return r.bodyText; },
                    json: async function() { return JSON.parse(r.bodyText || '{}'); },
                    headers: {
                        get: function(k) {
                            if (!k) return null;
                            var lk = String(k).toLowerCase();
                            for (var key in rHeaders) {
                                if (key.toLowerCase() === lk) return rHeaders[key];
                            }
                            return null;
                        }
                    }
                };
            };
        """.trimIndent())
    }
}
