package com.musicplayer

import android.content.*
import android.os.*
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import com.musicplayer.databinding.ActivityPlayerBinding
import com.musicplayer.models.Track
import com.musicplayer.services.MusicService

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false

    private val handler = Handler(Looper.getMainLooper())
    private val seekRunnable = object : Runnable {
        override fun run() {
            val svc = musicService ?: return
            val pos = svc.getCurrentPosition()
            val dur = svc.getDuration()
            if (dur > 0) {
                binding.seekBar.max = dur
                binding.seekBar.progress = pos
                binding.tvCurrentTime.text = formatTime(pos)
                binding.tvTotalTime.text   = formatTime(dur)
            }
            handler.postDelayed(this, 500)
        }
    }

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true
            updateUI()
            handler.post(seekRunnable)
            setupCallbacks()
        }
        override fun onServiceDisconnected(name: ComponentName?) { isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        bindService(
            Intent(this, MusicService::class.java),
            serviceConn,
            Context.BIND_AUTO_CREATE
        )

        setupControls()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnNext.setOnClickListener     { musicService?.playNext() }
        binding.btnPrev.setOnClickListener     { musicService?.playPrevious() }

        binding.btnShuffle.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            svc.isShuffled = !svc.isShuffled
            binding.btnShuffle.alpha = if (svc.isShuffled) 1.0f else 0.4f
        }

        binding.btnRepeat.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            svc.repeatMode = (svc.repeatMode + 1) % 3
            updateRepeatButton()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar)  {}
        })
    }

    private fun setupCallbacks() {
        musicService?.onTrackChanged = { track ->
            runOnUiThread { updateUI() }
        }
        musicService?.onPlayStateChanged = { playing ->
            runOnUiThread { updatePlayButton(playing) }
        }
    }

    private fun updateUI() {
        val svc   = musicService ?: return
        val track = svc.currentTrack ?: return

        binding.tvTitle.text  = track.title
        binding.tvArtist.text = track.artistDisplay()
        binding.tvAlbum.text  = track.albumDisplay()
        binding.tvGenre.text  = track.genreDisplay()

        updatePlayButton(svc.isPlaying)
        updateRepeatButton()
        binding.btnShuffle.alpha = if (svc.isShuffled) 1.0f else 0.4f

        loadAlbumArt(track)
    }

    private fun loadAlbumArt(track: Track) {
        Glide.with(this)
            .asBitmap()
            .load(track.albumArtUri)
            .placeholder(R.drawable.ic_music_note_large)
            .error(R.drawable.ic_music_note_large)
            .transform(RoundedCorners(32))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, t: Transition<in Bitmap>?) {
                    binding.ivAlbumArt.setImageBitmap(bitmap)
                    applyPaletteColors(bitmap)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(p: android.graphics.drawable.Drawable?) {
                    binding.ivAlbumArt.setImageResource(R.drawable.ic_music_note_large)
                }
            })
    }

    private fun applyPaletteColors(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            palette ?: return@generate
            val dominant = palette.getDominantColor(getColor(R.color.bg_dark))
            val vibrant  = palette.getVibrantColor(getColor(R.color.accent))

            val grad = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(dominant, getColor(R.color.bg_dark))
            )
            binding.root.background = grad

            binding.tvTitle.setTextColor(
                palette.getLightVibrantColor(getColor(R.color.text_primary))
            )
            binding.btnPlayPause.backgroundTintList =
                android.content.res.ColorStateList.valueOf(vibrant)
        }
    }

    private fun updatePlayButton(playing: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateRepeatButton() {
        val svc = musicService ?: return
        when (svc.repeatMode) {
            MusicService.REPEAT_NONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 0.4f
            }
            MusicService.REPEAT_ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 1.0f
            }
            MusicService.REPEAT_ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.alpha = 1.0f
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        handler.removeCallbacks(seekRunnable)
        if (isBound) unbindService(serviceConn)
        super.onDestroy()
    }
}
