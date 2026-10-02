package com.prateek.musicplayer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteTag(
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String,
)

object TagCache {
    var tags by mutableStateOf(mapOf<Long, RemoteTag>())

    fun load(context: Context) {
        val file = context.filesDir.resolve("music-tags.txt")
        if (!file.exists()) return
        val loaded = mutableMapOf<Long, RemoteTag>()
        file.readLines().forEach { line ->
            val parts = line.split("\t")
            if (parts.size == 5) {
                loaded[parts[0].toLongOrNull() ?: return@forEach] = RemoteTag(parts[1], parts[2], parts[3], parts[4])
            }
        }
        tags = loaded
    }

    fun save(context: Context) {
        val file = context.filesDir.resolve("music-tags.txt")
        file.writeText(tags.entries.joinToString("\n") { (id, tag) ->
            listOf(id, tag.title, tag.artist, tag.album, tag.artUrl).joinToString("\t")
        })
    }
}

object TagLookup {
    fun search(rawTitle: String): RemoteTag? {
        val cleaned = rawTitle
            .replace(Regex("\\((?i)[^)]*(official|lyric|audio|video|hd|remaster)[^)]*\\)"), "")
            .replace(Regex("(?i)official music video|official video|lyric video"), "")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return null
        val url = "https://itunes.apple.com/search?term=" +
            URLEncoder.encode(cleaned, "UTF-8") + "&entity=song&limit=1"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "MusicPlayer/2.3")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val item = results.getJSONObject(0)
        val art = item.optString("artworkUrl100").replace("100x100bb", "600x600bb")
        return RemoteTag(
            title = item.optString("trackName").ifBlank { cleaned },
            artist = item.optString("artistName").ifBlank { "Unknown" },
            album = item.optString("collectionName").ifBlank { "Unknown album" },
            artUrl = art,
        )
    }
}
