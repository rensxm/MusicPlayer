package com.musicplayer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.musicplayer.adapters.PlaylistAdapter
import com.musicplayer.adapters.TrackAdapter
import com.musicplayer.databinding.ActivityMainBinding
import com.musicplayer.models.Playlist
import com.musicplayer.models.PlaylistTrack
import com.musicplayer.models.Track
import com.musicplayer.services.MusicService
import com.musicplayer.utils.AppDatabase
import com.musicplayer.utils.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { AppDatabase.getInstance(this) }

    // Preferences key for persisting the selected folder
    private val PREFS_NAME = "music_player_prefs"
    private val PREF_FOLDER_PATH = "selected_folder_path"

    // Service
    private var musicService: MusicService? = null
    private var isBound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true
            setupServiceCallbacks()
            updateMiniPlayer()
        }
        override fun onServiceDisconnected(name: ComponentName?) { isBound = false }
    }

    // Adapters
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var playlistAdapter: PlaylistAdapter

    // State
    private var allTracks: List<Track> = emptyList()
    private var currentSortMode = SortMode.TITLE
    private var currentTab = 0
    private var selectedCodecs: MutableSet<String> = mutableSetOf()

    /** Absolute filesystem path of the selected folder, or null = all folders. */
    private var selectedFolderPath: String? = null

    enum class SortMode { TITLE, DATE, GENRE, ARTIST, ALBUM, YEAR }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanAndLoadTracks()
        else Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
    }

    /**
     * Folder picker launcher — uses ACTION_OPEN_DOCUMENT_TREE so the user can
     * browse any directory in the system file manager.
     */
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) return@registerForActivityResult

        // Convert the content-tree URI to an absolute filesystem path
        val path = resolveTreeUriToPath(treeUri)
        if (path != null) {
            selectedFolderPath = path
            saveSelectedFolder(path)
            updateFolderBanner()
            scanAndLoadTracks()
        } else {
            Toast.makeText(this, "Could not resolve folder path", Toast.LENGTH_SHORT).show()
        }
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            musicService?.let { svc ->
                val pos = svc.getCurrentPosition()
                val dur = svc.getDuration()
                if (dur > 0) {
                    binding.miniProgressBar.progress = (pos * 100 / dur)
                }
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore previously selected folder from prefs
        selectedFolderPath = loadSelectedFolder()

        setupToolbar()
        setupTabs()
        setupRecyclerViews()
        setupMiniPlayer()
        setupFab()
        setupFolderBanner()
        bindMusicService()
        checkPermissionAndScan()
    }

    override fun onResume() {
        super.onResume()
        progressHandler.post(progressRunnable)
        updateMiniPlayer()
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
    }

    override fun onDestroy() {
        if (isBound) unbindService(serviceConn)
        super.onDestroy()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Music Player"
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Tracks"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Playlists"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                binding.rvTracks.visibility    = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.rvPlaylists.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.fabAdd.visibility      = if (tab.position == 1) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerViews() {
        // Tracks
        trackAdapter = TrackAdapter(
            onTrackClick = { track -> playTrack(track) },
            onTrackLongClick = { track -> showTrackOptions(track); true }
        )
        binding.rvTracks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = trackAdapter
        }

        // Playlists
        playlistAdapter = PlaylistAdapter(
            onClick = { playlist -> openPlaylist(playlist) },
            onLongClick = { playlist -> showPlaylistOptions(playlist); true }
        )
        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = playlistAdapter
        }

        // Observe playlists
        lifecycleScope.launch {
            db.playlistDao().getAllPlaylists().collectLatest {
                playlistAdapter.submitList(it)
            }
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            musicService?.currentTrack?.let { openPlayerActivity(it) }
        }
        binding.btnMiniPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
        }
        binding.btnMiniNext.setOnClickListener {
            musicService?.playNext()
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener { showCreatePlaylistDialog() }
    }

    private fun setupFolderBanner() {
        binding.btnClearFolder.setOnClickListener {
            clearFolderFilter()
        }
        updateFolderBanner()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)
    }

    private fun setupServiceCallbacks() {
        musicService?.onTrackChanged = { track ->
            runOnUiThread {
                updateMiniPlayer()
                trackAdapter.currentPlayingId = track.id
            }
        }
        musicService?.onPlayStateChanged = { playing ->
            runOnUiThread {
                binding.btnMiniPlayPause.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
    }

    // ─── Folder Selection ─────────────────────────────────────────────────────

    /** Open the system folder picker. */
    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    /** Remove the folder filter and reload all tracks. */
    private fun clearFolderFilter() {
        selectedFolderPath = null
        clearSavedFolder()
        updateFolderBanner()
        scanAndLoadTracks()
    }

    /** Show/hide the folder banner and update its text. */
    private fun updateFolderBanner() {
        val path = selectedFolderPath
        if (path != null) {
            binding.folderBanner.visibility = View.VISIBLE
            // Display only the last two path segments for readability
            val display = path.trimEnd('/').let { p ->
                val parts = p.split('/')
                if (parts.size >= 2) "📁 …/${parts[parts.size - 2]}/${parts.last()}"
                else "📁 ${parts.last()}"
            }
            binding.tvFolderPath.text = display
        } else {
            binding.folderBanner.visibility = View.GONE
        }
    }

    /**
     * Convert a content-tree URI (e.g. content://com.android.externalstorage.documents/tree/primary%3AMusic)
     * into an absolute filesystem path like /storage/emulated/0/Music.
     */
    private fun resolveTreeUriToPath(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            // docId is typically "primary:Music" or "XXXX-XXXX:SomeFolder"
            val parts = docId.split(":")
            if (parts.size < 2) return null
            val volume = parts[0]
            val relativePath = parts[1]

            val root = if (volume.equals("primary", ignoreCase = true)) {
                "/storage/emulated/0"
            } else {
                "/storage/$volume"
            }
            if (relativePath.isEmpty()) root else "$root/$relativePath"
        } catch (e: Exception) {
            null
        }
    }

    // ─── Persist selected folder ───────────────────────────────────────────────
    private fun saveSelectedFolder(path: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_FOLDER_PATH, path).apply()
    }

    private fun clearSavedFolder() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(PREF_FOLDER_PATH).apply()
    }

    private fun loadSelectedFolder(): String? =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FOLDER_PATH, null)

    // ─── Permission & Scan ────────────────────────────────────────────────────
    private fun checkPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ->
                scanAndLoadTracks()
            else -> permissionLauncher.launch(permission)
        }
    }

    private fun scanAndLoadTracks() {
        val folderToScan = selectedFolderPath
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val tracks = withContext(Dispatchers.IO) {
                val scanned = MediaScanner.scanDevice(applicationContext, folderToScan)
                db.trackDao().insertTracks(scanned)
                scanned
            }
            allTracks = tracks
            applyFilters()
            binding.progressBar.visibility = View.GONE
        }

        // Also observe DB for any updates (only when scanning all folders,
        // because filtered scans are one-shot)
        if (folderToScan == null) {
            lifecycleScope.launch {
                db.trackDao().getAllTracks().collectLatest { dbTracks ->
                    if (dbTracks.isNotEmpty()) {
                        allTracks = dbTracks
                        applyFilters()
                    }
                }
            }
        }
    }

    // ─── Sorting & Filtering ──────────────────────────────────────────────────
    private fun applySort(mode: SortMode) {
        currentSortMode = mode
        applyFilters()
    }

    private fun applyFilters() {
        val filtered = if (selectedCodecs.isEmpty()) allTracks
                       else allTracks.filter { it.codecDisplay() in selectedCodecs }

        val sorted = when (currentSortMode) {
            SortMode.TITLE  -> filtered.sortedBy { it.title.lowercase() }
            SortMode.DATE   -> filtered.sortedByDescending { it.dateAdded }
            SortMode.GENRE  -> filtered.sortedWith(compareBy({ it.genreDisplay() }, { it.title.lowercase() }))
            SortMode.ARTIST -> filtered.sortedWith(compareBy({ it.artistDisplay() }, { it.title.lowercase() }))
            SortMode.ALBUM  -> filtered.sortedWith(compareBy({ it.albumDisplay() }, { it.title.lowercase() }))
            SortMode.YEAR   -> filtered.sortedWith(compareByDescending<Track> { it.year }.thenBy { it.title.lowercase() })
        }
        trackAdapter.submitList(sorted)

        val folderLabel = selectedFolderPath?.let {
            val last = it.trimEnd('/').substringAfterLast('/')
            "  •  📁 $last"
        } ?: ""

        val countMsg = if (selectedCodecs.isEmpty()) "${allTracks.size} tracks$folderLabel"
                       else "${sorted.size} / ${allTracks.size} tracks  •  ${selectedCodecs.joinToString(", ")}$folderLabel"
        binding.tvTrackCount.text = countMsg
    }

    private fun showCodecFilterDialog() {
        val available = allTracks.map { it.codecDisplay() }.toSortedSet().toTypedArray()
        if (available.isEmpty()) {
            Toast.makeText(this, "No tracks loaded", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(available.size) { available[it] in selectedCodecs }

        AlertDialog.Builder(this)
            .setTitle("Filter by Codec")
            .setMultiChoiceItems(available, checked) { _, which, isChecked ->
                if (isChecked) selectedCodecs.add(available[which])
                else selectedCodecs.remove(available[which])
            }
            .setPositiveButton("Apply") { _, _ -> applyFilters() }
            .setNeutralButton("Show All") { _, _ ->
                selectedCodecs.clear()
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Playback ─────────────────────────────────────────────────────────────
    private fun playTrack(track: Track) {
        val currentList = trackAdapter.currentList
        musicService?.playTrack(track, currentList)
        trackAdapter.currentPlayingId = track.id
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        val svc = musicService ?: return
        val track = svc.currentTrack ?: run {
            binding.miniPlayer.visibility = View.GONE
            return
        }
        binding.miniPlayer.visibility = View.VISIBLE
        binding.tvMiniTitle.text  = track.title
        binding.tvMiniArtist.text = track.artistDisplay()
        binding.btnMiniPlayPause.setImageResource(
            if (svc.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        Glide.with(this)
            .load(track.albumArtUri)
            .placeholder(R.drawable.ic_music_note_large)
            .error(R.drawable.ic_music_note_large)
            .into(binding.ivMiniArt)
    }

    private fun openPlayerActivity(track: Track) {
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────
    private fun showTrackOptions(track: Track) {
        val options = arrayOf("Play", "Add to Playlist", "Track Info")
        AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playTrack(track)
                    1 -> showAddToPlaylistDialog(track)
                    2 -> showTrackInfo(track)
                }
            }.show()
    }

    private fun showAddToPlaylistDialog(track: Track) {
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) {
                var list = emptyList<Playlist>()
                db.playlistDao().getAllPlaylists().collect { list = it }
                list
            }
            if (playlists.isEmpty()) {
                Toast.makeText(this@MainActivity, "Create a playlist first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = playlists.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Add to Playlist")
                .setItems(names) { _, i ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val pl = playlists[i]
                        val pos = db.playlistDao().getTrackCount(pl.id)
                        db.playlistDao().addTrackToPlaylist(
                            PlaylistTrack(pl.id, track.id, pos)
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
        }
    }

    private fun showTrackInfo(track: Track) {
        val info = """
            Title: ${track.title}
            Artist: ${track.artistDisplay()}
            Album: ${track.albumDisplay()}
            Genre: ${track.genreDisplay()}
            Duration: ${track.durationFormatted()}
            Year: ${if (track.year > 0) track.year else "Unknown"}
            Size: ${track.size / 1024 / 1024} MB
            Path: ${track.path}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Track Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Playlist name"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.playlistDao().insertPlaylist(Playlist(name = name))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlaylistOptions(playlist: Playlist) {
        val options = arrayOf("Open", "Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(playlist.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPlaylist(playlist)
                    1 -> renamePlaylistDialog(playlist)
                    2 -> confirmDeletePlaylist(playlist)
                }
            }.show()
    }

    private fun renamePlaylistDialog(playlist: Playlist) {
        val input = EditText(this).apply {
            setText(playlist.name)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Playlist")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.playlistDao().renamePlaylist(playlist.id, newName)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle("Delete Playlist")
            .setMessage("Delete \"${playlist.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.playlistDao().deletePlaylist(playlist)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPlaylist(playlist: Playlist) {
        val intent = Intent(this, PlaylistActivity::class.java).apply {
            putExtra("playlist_id", playlist.id)
            putExtra("playlist_name", playlist.name)
        }
        startActivity(intent)
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                val query = q?.trim() ?: ""
                val base = if (selectedCodecs.isEmpty()) allTracks
                           else allTracks.filter { it.codecDisplay() in selectedCodecs }
                val filtered = if (query.isEmpty()) base
                else base.filter {
                    it.title.contains(query, true) ||
                    it.artist.contains(query, true) ||
                    it.album.contains(query, true)
                }
                trackAdapter.submitList(filtered)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sort_title          -> { applySort(SortMode.TITLE);  true }
            R.id.sort_date           -> { applySort(SortMode.DATE);   true }
            R.id.sort_genre          -> { applySort(SortMode.GENRE);  true }
            R.id.sort_artist         -> { applySort(SortMode.ARTIST); true }
            R.id.sort_album          -> { applySort(SortMode.ALBUM);  true }
            R.id.sort_year           -> { applySort(SortMode.YEAR);   true }
            R.id.action_scan         -> { scanAndLoadTracks();         true }
            R.id.action_filter_codec -> { showCodecFilterDialog();     true }
            R.id.action_choose_folder -> { openFolderPicker();         true }
            R.id.action_reset_folder  -> { clearFolderFilter();        true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
