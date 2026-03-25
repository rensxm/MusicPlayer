package com.musicplayer.utils

import android.content.Context
import androidx.room.*
import com.musicplayer.models.Playlist
import com.musicplayer.models.PlaylistTrack
import com.musicplayer.models.Track
import kotlinx.coroutines.flow.Flow

// ─── Track DAO ───────────────────────────────────────────────────────────────
@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun getTracksByDate(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY genre ASC, title ASC")
    fun getTracksByGenre(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY artist ASC, title ASC")
    fun getTracksByArtist(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY album ASC, title ASC")
    fun getTracksByAlbum(): Flow<List<Track>>

    @Query("SELECT * FROM tracks ORDER BY year DESC, title ASC")
    fun getTracksByYear(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchTracks(query: String): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Delete
    suspend fun deleteTrack(track: Track)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getCount(): Int
}

// ─── Playlist DAO ─────────────────────────────────────────────────────────────
@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Long, newName: String)

    // Playlist tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(pt: PlaylistTrack)

    @Delete
    suspend fun removeTrackFromPlaylist(pt: PlaylistTrack)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: Long): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)
}

// ─── Database ─────────────────────────────────────────────────────────────────
@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
    }
}
