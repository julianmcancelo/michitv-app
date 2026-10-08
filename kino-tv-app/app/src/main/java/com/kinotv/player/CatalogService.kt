package com.kinotv.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class CinemetaCatalogResponse(
    val metas: List<CatalogItem> = emptyList()
)

@Serializable
data class CinemetaDetailResponse(
    val meta: DetailMeta? = null
)

@Serializable
data class DetailMeta(
    val id: String,
    val name: String,
    val type: String = "movie",
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val videos: List<EpisodeItem> = emptyList()
)

@Serializable
data class EpisodeItem(
    val id: String,
    val name: String? = null,
    val season: Int = 1,
    val number: Int = 1,
    val overview: String? = null,
    val thumbnail: String? = null,
    val ref: String? = null,
    val pluginId: String? = null
)

@Serializable
data class CatalogItem(
    val id: String,
    val name: String,
    val type: String = "movie",
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val pluginId: String? = null,
    val ref: String? = null,
    val rating: Double? = null
)

data class CatalogRow(
    val title: String,
    val items: List<CatalogItem>
)

object CatalogService {
    private val client = NetworkHelper.okHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getPopularMovies(): List<CatalogItem> = fetchCatalog("https://v3-cinemeta.strem.io/catalog/movie/top.json")
    suspend fun getPopularSeries(): List<CatalogItem> = fetchCatalog("https://v3-cinemeta.strem.io/catalog/series/top.json")
    suspend fun getTrendingAnime(): List<CatalogItem> = fetchCatalog("https://anime-kitsu.strem.fun/catalog/anime/kitsu-anime-trending.json")

    suspend fun search(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val movies = fetchCatalog("https://v3-cinemeta.strem.io/catalog/movie/top/search=$q.json")
        val series = fetchCatalog("https://v3-cinemeta.strem.io/catalog/series/top/search=$q.json")
        movies + series
    }

    suspend fun getDetails(type: String, id: String): DetailMeta? = withContext(Dispatchers.IO) {
        val normalizedType = if (type == "series" || type == "tv") "series" else "movie"
        val url = if (id.startsWith("kitsu:")) {
            "https://anime-kitsu.strem.fun/meta/anime/$id.json"
        } else {
            "https://v3-cinemeta.strem.io/meta/$normalizedType/$id.json"
        }
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<CinemetaDetailResponse>(body)
                parsed.meta
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchCatalog(url: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = json.decodeFromString<CinemetaCatalogResponse>(body)
                parsed.metas
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
