package com.vinylcast.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CoverArtResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
) {
    suspend fun resolveCoverUrl(artist: String, album: String, title: String): String? =
        withContext(Dispatchers.IO) {
            fetchFromItunes(artist, album, title) ?: fetchFromMusicBrainz(artist, album, title)
        }

    private fun fetchFromItunes(artist: String, album: String, title: String): String? {
        val query = listOf(artist, album.ifBlank { title })
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return null

        return try {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("media", "music")
                .addQueryParameter("entity", "song")
                .addQueryParameter("limit", "3")
                .build()
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: return null)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null
                val artUrl = results.getJSONObject(0).optString("artworkUrl100", "")
                if (artUrl.isBlank()) return null
                artUrl.replace("100x100bb", "1000x1000bb")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromMusicBrainz(artist: String, album: String, title: String): String? {
        val releaseTerm = album.ifBlank { title }
        if (artist.isBlank() && releaseTerm.isBlank()) return null

        return try {
            val luceneQuery = buildString {
                if (artist.isNotBlank()) append("artist:\"$artist\"")
                if (artist.isNotBlank() && releaseTerm.isNotBlank()) append(" AND ")
                if (releaseTerm.isNotBlank()) append("release:\"$releaseTerm\"")
            }
            val url = "https://musicbrainz.org/ws/2/release/".toHttpUrl().newBuilder()
                .addQueryParameter("query", luceneQuery)
                .addQueryParameter("fmt", "json")
                .addQueryParameter("limit", "1")
                .build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "VinylCast/1.0 (https://github.com/vinylcast)")
                .get()
                .build()

            val mbid = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: return null)
                val releases = json.optJSONArray("releases") ?: return null
                if (releases.length() == 0) return null
                releases.getJSONObject(0).optString("id", "").takeIf { it.isNotBlank() }
            } ?: return null

            val coverUrl = "https://coverartarchive.org/release/$mbid/front-500"
            // Verify cover exists (Cover Art Archive returns 307/200 or 404)
            val headReq = Request.Builder().url(coverUrl).head().build()
            httpClient.newCall(headReq).execute().use { headResp ->
                if (headResp.isSuccessful) coverUrl else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
