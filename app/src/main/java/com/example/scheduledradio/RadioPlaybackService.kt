package com.example.scheduledradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class RadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentStreamUrl: String = ""
    private lateinit var prefs: RadioPreferences

    companion object {
        const val CMD_PLAY = "CMD_PLAY"
        const val CMD_STOP = "CMD_STOP"
        const val CMD_UPDATE_AUDIO = "CMD_UPDATE_AUDIO"
        const val EXTRA_URL = "EXTRA_URL"
        private const val CHANNEL_ID = "radio_playback_channel"
        private const val NOTIFICATION_ID = 999
    }

    override fun onCreate() {
        super.onCreate()
        prefs = RadioPreferences(this)
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (currentStreamUrl.isNotBlank()) {
                            setMediaItem(MediaItem.fromUri(currentStreamUrl))
                            prepare()
                            play()
                        }
                    }
                })
            }

        applySavedVolume()
        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            CMD_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                currentStreamUrl = url
                applySavedVolume()
                startForeground(NOTIFICATION_ID, buildNotification("Playing Sarath's Radio..."))

                player?.apply {
                    stop()
                    clearMediaItems()
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    play()
                }
            }

            CMD_UPDATE_AUDIO -> {
                applySavedVolume()
            }

            CMD_STOP -> {
                stopPlaybackAndService()
            }
        }
        return START_STICKY
    }

    private fun applySavedVolume() {
        player?.volume = if (prefs.isMuted) 0f else prefs.volumeLevel
    }

    private fun stopPlaybackAndService() {
        currentStreamUrl = ""
        player?.stop()
        player?.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = CMD_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sarath's Radio")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Radio", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
