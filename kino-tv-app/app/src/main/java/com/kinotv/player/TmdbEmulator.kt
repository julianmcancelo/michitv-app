package com.kinotv.player

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray

object TmdbEmulator {
    fun handleTmdbRequest(path: String, params: JSONObject): String {
        Log.d("TmdbEmulator", "Request: path=$path, params=$params")
        try {
            if (path.startsWith("/search/")) {
                val query = params.optString("query", "")
                if (query.isBlank()) return JSONObject().put("results", JSONArray()).toString()
                val results = runBlocking { CatalogService.search(query) }
                val outArr = JSONArray()
                for (item in results) {
                    val isMovie = item.type == "movie"
                    val idStr = item.id.replace("tt", "").replace(Regex("[^0-9]"), "")
                    val idNum = idStr.toIntOrNull() ?: item.id.hashCode()

                    val obj = JSONObject()
                    obj.put("id", Math.abs(idNum))
                    obj.put("media_type", if (isMovie) "movie" else "tv")
                    obj.put("title", item.name)
                    obj.put("name", item.name)
                    obj.put("original_title", item.name)
                    obj.put("original_name", item.name)
                    obj.put("overview", item.description ?: "")
                    obj.put("poster_path", item.poster)
                    obj.put("backdrop_path", item.background)
                    if (item.rating != null && item.rating > 0) obj.put("vote_average", item.rating)
                    obj.put("release_date", "${item.year ?: "2020"}-01-01")
                    obj.put("first_air_date", "${item.year ?: "2020"}-01-01")
                    outArr.put(obj)
                }
                val res = JSONObject()
                res.put("results", outArr)
                Log.d("TmdbEmulator", "search '$query' -> ${outArr.length()} results")
                return res.toString()
            }
            else if (path.startsWith("/movie/") || path.startsWith("/tv/")) {
                val idMatch = Regex("""/(movie|tv)/(\d+)""").find(path)
                if (idMatch != null) {
                    val type = idMatch.groupValues[1]
                    val idNum = idMatch.groupValues[2]
                    
                    // Solo necesitamos devolver un objeto con title, name, year, etc.
                    // Podr�amos buscar en Cinemeta de nuevo, pero Latino a veces solo necesita esto
                    // Si devolvemos algo gen�rico, �funcionar�? Latino usa esto para sacar el t�tulo original
                    // Vamos a intentar obtener meta de Cinemeta!
                    val imdbId = "tt${idNum.padStart(7, '0')}"
                    val meta = runBlocking { CatalogService.getDetails(type, imdbId) }
                    
                    val obj = JSONObject()
                    obj.put("id", idNum.toIntOrNull() ?: 0)
                    obj.put("title", meta?.name ?: "Sin Titulo")
                    obj.put("name", meta?.name ?: "Sin Titulo")
                    obj.put("original_title", meta?.name ?: "Sin Titulo")
                    obj.put("original_name", meta?.name ?: "Sin Titulo")
                    obj.put("overview", meta?.description ?: "")
                    obj.put("release_date", "${meta?.year ?: "2020"}-01-01")
                    obj.put("first_air_date", "${meta?.year ?: "2020"}-01-01")
                    obj.put("last_air_date", "${meta?.year ?: "2020"}-01-01")
                    obj.put("poster_path", meta?.poster)
                    obj.put("backdrop_path", meta?.background)
                    obj.put("external_ids", JSONObject().put("imdb_id", imdbId))
                    obj.put("translations", JSONObject().put("translations", JSONArray()))
                    obj.put("episode_run_time", JSONArray())
                    
                    // Si es serie, Latino busca seasons
                    if (type == "tv") {
                        val seasonsArr = JSONArray()
                        val seasons = meta?.videos?.map { it.season }?.distinct() ?: listOf(1)
                        for (s in seasons) {
                            val sObj = JSONObject()
                            sObj.put("season_number", s)
                            sObj.put("episode_count", meta?.videos?.count { it.season == s } ?: 10)
                            seasonsArr.put(sObj)
                        }
                        obj.put("seasons", seasonsArr)
                    }
                    
                    return obj.toString()
                }
            }
            // Default empty
            return JSONObject().put("results", JSONArray()).toString()
        } catch (e: Exception) {
            Log.e("TmdbEmulator", "Error", e)
            return JSONObject().put("results", JSONArray()).toString()
        }
    }
}

