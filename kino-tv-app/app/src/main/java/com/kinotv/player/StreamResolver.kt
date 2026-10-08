package com.kinotv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Base64

object StreamResolver {
    private val client = NetworkHelper.okHttpClient
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /**
     * Resuelve un enlace de Voe (como los que devuelven AnimeAV1 y FuegoCine/Latino)
     * a una URL directa reproducible .m3u8 en HLS.
     */
    suspend fun resolveVoe(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = embedUrl
            for (hop in 0 until 3) {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", UA)
                    .build()

                val html = client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withContext null
                    res.body?.string() ?: return@withContext null
                }

                val packedRegex = Regex("""<script type="application/json">\s*\[\s*"([^"]+)"\s*\]\s*</script>""")
                val match = packedRegex.find(html)
                if (match != null) {
                    val packed = match.groupValues[1]
                    val streamUrl = decodeVoePacked(packed)
                    if (!streamUrl.isNullOrEmpty()) return@withContext streamUrl
                }

                // Si hay redirección por script de Voe
                val redirectRegex = Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""")
                val redirMatch = redirectRegex.find(html)
                if (redirMatch != null) {
                    currentUrl = redirMatch.groupValues[1]
                } else {
                    break
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeVoePacked(packed: String): String? {
        try {
            // ROT13
            val rot = StringBuilder()
            for (c in packed) {
                if (c in 'a'..'z') {
                    rot.append((((c - 'a' + 13) % 26) + 'a'.code).toChar())
                } else if (c in 'A'..'Z') {
                    rot.append((((c - 'A' + 13) % 26) + 'A'.code).toChar())
                } else {
                    rot.append(c)
                }
            }

            var clean = rot.toString()
            val junks = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
            for (junk in junks) {
                clean = clean.replace(junk, "")
            }

            val b64 = String(Base64.getDecoder().decode(clean))
            val shifted = StringBuilder()
            for (c in b64) {
                shifted.append((c.code - 3).toChar())
            }

            val reversed = shifted.reverse().toString()
            val jsonStr = String(Base64.getDecoder().decode(reversed))
            val obj = JSONObject(jsonStr)
            return obj.optString("source", null)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
