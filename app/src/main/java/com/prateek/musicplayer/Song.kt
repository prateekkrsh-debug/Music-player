package com.prateek.musicplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val folder: String,
    val albumId: Long,
    val dateAdded: Long,
    val sizeBytes: Long,
    val year: Int,
    val uri: Uri,
)

fun scanSongs(context: Context): List<Song> {
    val songs = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.YEAR,
    )
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            songs += Song(
                id = id,
                title = cleanLabel(cursor.getString(titleCol) ?: cursor.getString(nameCol), "Unknown"),
                artist = cleanLabel(cursor.getString(artistCol), "Unknown"),
                album = cleanLabel(cursor.getString(albumCol), "Unknown album"),
                genre = "Unknown",
                durationMs = cursor.getLong(durationCol),
                folder = cursor.getString(pathCol)?.trimEnd('/')?.ifBlank { null } ?: "Unknown folder",
                albumId = cursor.getLong(albumIdCol),
                dateAdded = cursor.getLong(addedCol),
                sizeBytes = cursor.getLong(sizeCol),
                year = cursor.getInt(yearCol),
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
            )
        }
    }
    return songs
}

fun albumArtUri(albumId: Long): Uri {
    return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

fun cleanLabel(value: String?, fallback: String): String {
    val cleaned = value?.trim().orEmpty()
    if (cleaned.isBlank() || cleaned.equals("<unknown>", true) || cleaned.equals("unknown", true)) return fallback
    return cleaned
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
