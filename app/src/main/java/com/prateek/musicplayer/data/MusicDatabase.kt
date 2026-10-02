package com.prateek.musicplayer.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val songId: Long,
    val playedAt: Long,
    val playCount: Int,
)

@Dao
interface MusicDao {
    @Query("SELECT songId FROM favorites")
    fun favorites(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun favorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun unfavorite(songId: Long)

    @Query("SELECT * FROM playlists ORDER BY name")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistSong(entity: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: Long, songId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongIds(playlistId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: HistoryEntity)

    @Query("SELECT * FROM history WHERE songId = :songId")
    suspend fun historyFor(songId: Long): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 20")
    fun recent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY playCount DESC LIMIT 20")
    fun mostPlayed(): Flow<List<HistoryEntity>>
}

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, HistoryEntity::class],
    version = 1,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun dao(): MusicDao
}
