package com.prateek.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.prateek.musicplayer.MainActivity
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                mediaSession?.player?.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(41)
                .build(),
        )
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_SLEEP, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SLEEP_END, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
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
                        ACTION_SLEEP_END -> player.addListener(endListener)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        val noisy = android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, noisy, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, noisy)
        }
    }

    private val endListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                mediaSession?.player?.pause()
                mediaSession?.player?.removeListener(this)
            }
        }
    }

    private fun scheduleSleep(delayMs: Long) {
        sleepRunnable?.let(handler::removeCallbacks)
        val stop = Runnable { mediaSession?.player?.pause() }
        sleepRunnable = stop
        handler.postDelayed(stop, delayMs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, true)
    }


    override fun onDestroy() {
        sleepRunnable?.let(handler::removeCallbacks)
        runCatching { unregisterReceiver(noisyReceiver) }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SLEEP = "sleep"
        const val ACTION_SLEEP_END = "sleep_end"
        const val EXTRA_MINUTES = "minutes"
    }
}
