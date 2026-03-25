package com.musicplayer

import android.content.*
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.adapters.TrackAdapter
import com.musicplayer.databinding.ActivityPlaylistBinding
import com.musicplayer.services.MusicService
import com.musicplayer.utils.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private lateinit var adapter: TrackAdapter

    private var playlistId: Long = -1
    private var playlistName: String = ""

    private var musicService: MusicService? = null
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicService.MusicBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId   = intent.getLongExtra("playlist_id", -1)
        playlistName = intent.getStringExtra("playlist_name") ?: "Playlist"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = playlistName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindService(Intent(this, MusicService::class.java), serviceConn, Context.BIND_AUTO_CREATE)

        adapter = TrackAdapter(
            onTrackClick = { track ->
                val tracks = adapter.currentList
                musicService?.playTrack(track, tracks)
                adapter.currentPlayingId = track.id
            },
            onTrackLongClick = { track ->
                lifecycleScope.launch {
                    db.playlistDao().removeTrack(playlistId, track.id)
                }
                true
            }
        )
        binding.rvPlaylistTracks.layoutManager = LinearLayoutManager(this)
        binding.rvPlaylistTracks.adapter = adapter

        lifecycleScope.launch {
            db.playlistDao().getTracksForPlaylist(playlistId).collectLatest {
                adapter.submitList(it)
                binding.tvTrackCount.text = "${it.size} tracks"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        unbindService(serviceConn)
        super.onDestroy()
    }
}
