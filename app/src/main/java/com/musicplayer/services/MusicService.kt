package com.musicplayer.services

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.musicplayer.MainActivity
import com.musicplayer.R
import com.musicplayer.models.Track
import com.musicplayer.utils.MediaScanner

class MusicService : Service() {

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null

    var currentTrack: Track? = null
        private set
    var playlist: List<Track> = emptyList()
    var currentIndex: Int = -1
        private set

    // Use backing field to avoid name clash with MediaPlayer.isPlaying (val) inside apply{}
    private var _isPlaying: Boolean = false
    val isPlaying: Boolean get() = _isPlaying

    var isShuffled: Boolean = false
    var repeatMode: Int = REPEAT_NONE

    var onTrackChanged: ((Track) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null

    companion object {
        const val REPEAT_NONE = 0
        const val REPEAT_ALL  = 1
        const val REPEAT_ONE  = 2

        const val CHANNEL_ID      = "music_player_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_PAUSE = "com.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT       = "com.musicplayer.NEXT"
        const val ACTION_PREV       = "com.musicplayer.PREV"
        const val ACTION_STOP       = "com.musicplayer.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT       -> playNext()
            ACTION_PREV       -> playPrevious()
            ACTION_STOP       -> { stop(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    fun playTrack(track: Track, trackList: List<Track> = playlist) {
        playlist     = trackList
        currentIndex = playlist.indexOfFirst { it.id == track.id }
        currentTrack = track

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            val uri = MediaScanner.getTrackUri(track.id)
            setDataSource(applicationContext, uri)
            prepareAsync()
            setOnPreparedListener {
                start()
                // Explicitly reference the Service field, not MediaPlayer.isPlaying
                this@MusicService._isPlaying = true
                onPlayStateChanged?.invoke(true)
                updateNotification()
            }
            setOnCompletionListener { handleCompletion() }
        }
        onTrackChanged?.invoke(track)
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying = false
        } else {
            player.start()
            _isPlaying = true
        }
        onPlayStateChanged?.invoke(_isPlaying)
        updateNotification()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = when {
            isShuffled                       -> playlist.indices.random()
            currentIndex < playlist.size - 1 -> currentIndex + 1
            repeatMode == REPEAT_ALL          -> 0
            else                              -> return
        }
        playTrack(playlist[nextIndex])
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        val prevIndex = when {
            getCurrentPosition() > 3000      -> { seekTo(0); return }
            currentIndex > 0                 -> currentIndex - 1
            repeatMode == REPEAT_ALL          -> playlist.size - 1
            else                             -> 0
        }
        playTrack(playlist[prevIndex])
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying  = false
        stopForeground(true)
    }

    fun seekTo(ms: Int)           { mediaPlayer?.seekTo(ms) }
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int        = mediaPlayer?.duration ?: 0

    private fun handleCompletion() {
        when (repeatMode) {
            REPEAT_ONE -> currentTrack?.let { playTrack(it) }
            else       -> playNext()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Music player controls" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateNotification() {
        val track = currentTrack ?: return
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track.title)
            .setContentText(track.artistDisplay())
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_skip_previous, "Previous", buildPendingIntent(ACTION_PREV))
            .addAction(
                if (_isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (_isPlaying) "Pause" else "Play",
                buildPendingIntent(ACTION_PLAY_PAUSE)
            )
            .addAction(R.drawable.ic_skip_next, "Next", buildPendingIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}
