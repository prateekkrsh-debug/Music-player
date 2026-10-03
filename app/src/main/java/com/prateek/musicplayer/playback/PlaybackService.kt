package com.prateek.musicplayer.playback

import android.app.PendingIntent
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.prateek.musicplayer.MainActivity
import com.prateek.musicplayer.MusicApp
import com.prateek.musicplayer.Song
import com.prateek.musicplayer.TagCache
import com.prateek.musicplayer.albumArtUri
import com.prateek.musicplayer.scanSongs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class PlaybackService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var sleepRunnable: Runnable? = null
    private var userPaused = false
    private var routeLossWhilePlaying = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val current = player ?: return
            if (!pauseOnDisconnect()) return
            if (current.isPlaying) {
                routeLossWhilePlaying = true
                userPaused = false
                current.pause()
                persist()
                Log.i(TAG, "Paused because audio route was lost")
            }
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.none { it.isCarAudioOutput() }) return
            Log.i(TAG, "Car-like audio output connected")
            maybeResumeAfterCarConnect()
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setNotificationId(41).build())
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        player = exoPlayer
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        librarySession = MediaLibrarySession.Builder(this, exoPlayer, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) {
                    if (player.playWhenReady) userPaused = false
                    persist()
                }
            }
        })
        restore(play = false)
        val noisy = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, noisy)
        }
        val audioManager = getSystemService(AudioManager::class.java)
        audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
        Log.i(TAG, "Playback service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CAR_CONNECTED) maybeResumeAfterCarConnect()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persist()
        sleepRunnable?.let(handler::removeCallbacks)
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback) }
        librarySession?.release()
        librarySession = null
        player?.release()
        player = null
        io.shutdownNow()
        super.onDestroy()
    }

    private fun maybeResumeAfterCarConnect() {
        val current = player ?: return
        if (!resumeOnCar() || !routeLossWhilePlaying || userPaused || current.mediaItemCount == 0) {
            Log.i(TAG, "Car connected, not auto-resuming")
            return
        }
        routeLossWhilePlaying = false
        current.play()
        Log.i(TAG, "Resumed previous session after car audio connected")
    }

    private fun persist() {
        val current = player ?: return
        if (current.mediaItemCount == 0) return
        val items = JSONArray()
        for (index in 0 until current.mediaItemCount) {
            val item = current.getMediaItemAt(index)
            items.put(
                JSONObject()
                    .put("id", item.mediaId)
                    .put("uri", item.localConfiguration?.uri?.toString().orEmpty())
                    .put("title", item.mediaMetadata.title?.toString().orEmpty())
                    .put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                    .put("album", item.mediaMetadata.albumTitle?.toString().orEmpty()),
            )
        }
        val json = JSONObject()
            .put("index", current.currentMediaItemIndex.coerceAtLeast(0))
            .put("position", current.currentPosition.coerceAtLeast(0L))
            .put("shuffle", current.shuffleModeEnabled)
            .put("repeat", current.repeatMode)
            .put("wasPlaying", current.isPlaying)
            .put("routeLoss", routeLossWhilePlaying)
            .put("userPaused", userPaused)
            .put("items", items)
        runCatching { filesDir.resolve(SESSION_FILE).writeText(json.toString()) }
    }

    private fun restore(play: Boolean) {
        val current = player ?: return
        val file = filesDir.resolve(SESSION_FILE)
        if (!file.exists()) return
        val saved = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val items = saved.optJSONArray("items") ?: return
        val media = buildList {
            for (index in 0 until items.length()) {
                val row = items.optJSONObject(index) ?: continue
                val uri = row.optString("uri")
                if (uri.isBlank()) continue
                add(
                    MediaItem.Builder()
                        .setMediaId(row.optString("id"))
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(row.optString("title"))
                                .setArtist(row.optString("artist"))
                                .setAlbumTitle(row.optString("album"))
                                .setIsPlayable(true)
                                .build(),
                        )
                        .build(),
                )
            }
        }
        if (media.isEmpty()) return
        routeLossWhilePlaying = saved.optBoolean("routeLoss")
        userPaused = saved.optBoolean("userPaused")
        current.shuffleModeEnabled = saved.optBoolean("shuffle")
        current.repeatMode = saved.optInt("repeat", Player.REPEAT_MODE_OFF)
        current.setMediaItems(media, saved.optInt("index").coerceIn(0, media.lastIndex), saved.optLong("position"))
        current.prepare()
        current.playWhenReady = play && saved.optBoolean("wasPlaying") && !userPaused
        Log.i(TAG, "Restored ${media.size} queued songs, play=$play")
    }

    private fun pauseOnDisconnect(): Boolean = readSetting("pause_disconnect", true)

    private fun resumeOnCar(): Boolean = readSetting("resume_on_car", true)

    private fun readSetting(key: String, default: Boolean): Boolean = runCatching {
        runBlocking { (application as MusicApp).setting(key, default) }
    }.getOrDefault(default)

    private fun scheduleSleep(delayMs: Long) {
        sleepRunnable?.let(handler::removeCallbacks)
        val stop = Runnable { player?.pause() }
        sleepRunnable = stop
        handler.postDelayed(stop, delayMs)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            Log.i(TAG, "Controller connected: ${controller.packageName}")
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SLEEP_END, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SLEEP -> scheduleSleep(args.getInt(EXTRA_MINUTES, 15) * 60_000L)
                ACTION_SLEEP_END -> player?.addListener(endListener)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.i(TAG, "Playback resumption requested")
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            io.execute {
                val saved = runCatching { JSONObject(filesDir.resolve(SESSION_FILE).readText()) }.getOrNull()
                val items = saved?.optJSONArray("items")
                if (saved == null || items == null || items.length() == 0) {
                    future.setException(UnsupportedOperationException("No previous song"))
                    return@execute
                }
                val media = mutableListOf<MediaItem>()
                for (index in 0 until items.length()) {
                    val row = items.optJSONObject(index) ?: continue
                    val uri = row.optString("uri")
                    if (uri.isBlank()) continue
                    media += MediaItem.Builder().setMediaId(row.optString("id")).setUri(uri).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(row.optString("title")).setArtist(row.optString("artist")).setAlbumTitle(row.optString("album")).setIsPlayable(true).build(),
                    ).build()
                }
                if (media.isEmpty()) {
                    future.setException(UnsupportedOperationException("Saved songs are gone"))
                } else {
                    future.set(MediaSession.MediaItemsWithStartPosition(media, saved.optInt("index").coerceIn(0, media.lastIndex), saved.optLong("position")))
                }
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(folder("root", "Music Player"), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            io.execute {
                val children = runCatching { childrenFor(parentId) }.getOrElse {
                    Log.i(TAG, "Library browse failed: ${it.message}")
                    emptyList()
                }
                val from = (page * pageSize).coerceAtMost(children.size)
                val to = (from + pageSize).coerceAtMost(children.size)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            io.execute {
                val item = runCatching { findItem(mediaId) }.getOrNull()
                if (item == null) future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                else future.set(LibraryResult.ofItem(item, null))
            }
            return future
        }
    }

    private val endListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                player?.pause()
                player?.removeListener(this)
            }
        }
    }

    private fun childrenFor(parentId: String): List<MediaItem> {
        val songs = scanSongs(this)
        return when (parentId) {
            "root" -> listOf(
                folder("songs", "All Songs"),
                folder("recent", "Recently Played"),
                folder("favorites", "Favorites"),
                folder("playlists", "Playlists"),
                folder("artists", "Artists"),
                folder("albums", "Albums"),
            )
            "songs" -> songs.map { it.toBrowseItem() }
            "recent" -> idsToSongs(songs, recentIds())
            "favorites" -> idsToSongs(songs, favoriteIds())
            "playlists" -> playlistFolders()
            "artists" -> songs.map { it.artist }.distinct().sorted().map { folder("artist:$it", it) }
            "albums" -> songs.map { it.album }.distinct().sorted().map { folder("album:$it", it) }
            else -> when {
                parentId.startsWith("artist:") -> songs.filter { it.artist == parentId.removePrefix("artist:") }.map { it.toBrowseItem() }
                parentId.startsWith("album:") -> songs.filter { it.album == parentId.removePrefix("album:") }.map { it.toBrowseItem() }
                parentId.startsWith("playlist:") -> idsToSongs(songs, playlistIds(parentId.removePrefix("playlist:").toLongOrNull() ?: -1))
                else -> emptyList()
            }
        }
    }

    private fun findItem(mediaId: String): MediaItem? {
        if (mediaId == "root" || mediaId in setOf("songs", "recent", "favorites", "playlists", "artists", "albums") || mediaId.startsWith("artist:") || mediaId.startsWith("album:") || mediaId.startsWith("playlist:")) {
            return folder(mediaId, mediaId)
        }
        return scanSongs(this).firstOrNull { it.id.toString() == mediaId }?.toBrowseItem()
    }

    private fun idsToSongs(songs: List<Song>, ids: List<Long>): List<MediaItem> {
        val byId = songs.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toBrowseItem() }
    }

    private fun favoriteIds(): List<Long> = runCatching {
        runBlocking { (application as MusicApp).database.dao().favorites().first() }
    }.getOrDefault(emptyList())

    private fun recentIds(): List<Long> = runCatching {
        runBlocking { (application as MusicApp).database.dao().recent().first().map { it.songId } }
    }.getOrDefault(emptyList())

    private fun playlistFolders(): List<MediaItem> = runCatching {
        runBlocking { (application as MusicApp).database.dao().playlists().first() }
            .map { folder("playlist:${it.id}", it.name) }
    }.getOrDefault(emptyList())

    private fun playlistIds(id: Long): List<Long> = runCatching {
        runBlocking { (application as MusicApp).database.dao().playlistSongIds(id).first() }
    }.getOrDefault(emptyList())

    private fun folder(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()
    }

    private fun Song.toBrowseItem(): MediaItem {
        val tag = TagCache.tags[id]
        val art = tag?.artUrl?.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }
            ?: albumArtUri(albumId).takeIf { albumId != 0L }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(tag?.title ?: title)
                    .setArtist(tag?.artist ?: artist)
                    .setAlbumTitle(tag?.album ?: album)
                    .setArtworkUri(art)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    private fun AudioDeviceInfo.isCarAudioOutput(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BUS
    }

    companion object {
        const val TAG = "MusicPlayback"
        const val ACTION_SLEEP = "sleep"
        const val ACTION_SLEEP_END = "sleep_end"
        const val ACTION_CAR_CONNECTED = "car_connected"
        const val EXTRA_MINUTES = "minutes"
        private const val SESSION_FILE = "playback-session.json"
    }
}

class CarAudioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        if (state != BluetoothProfile.STATE_CONNECTED) return
        Log.i(PlaybackService.TAG, "Bluetooth audio profile connected, checking car resume")
        context.startService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_CAR_CONNECTED))
    }
}
