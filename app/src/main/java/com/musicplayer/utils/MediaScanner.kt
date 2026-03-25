package com.musicplayer.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.musicplayer.models.Track

object MediaScanner {

    /**
     * Scan device for audio tracks.
     * @param folderPath  If non-null, only tracks whose DATA path starts with this prefix
     *                    (i.e. the chosen folder and its sub-folders) are returned.
     */
    fun scanDevice(context: Context, folderPath: String? = null): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // Base filter: must be music and longer than 30 s
        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        var selectionArgs: Array<String>? = null

        // If a folder is selected, restrict to that subtree via DATA LIKE 'path/%'
        if (folderPath != null) {
            val normalized = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
            selection += " AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("${normalized}%")
        }

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val genreCol   = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val yearCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val pathCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol    = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id      = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val artUri  = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                ).toString()

                val genre = if (genreCol >= 0) cursor.getString(genreCol) ?: ""
                            else getGenreForTrack(context, id)

                tracks.add(
                    Track(
                        id          = id,
                        title       = cursor.getString(titleCol) ?: "Unknown",
                        artist      = cursor.getString(artistCol) ?: "",
                        album       = cursor.getString(albumCol) ?: "",
                        genre       = genre,
                        duration    = cursor.getLong(durCol),
                        dateAdded   = cursor.getLong(dateCol) * 1000L,
                        year        = cursor.getInt(yearCol),
                        path        = cursor.getString(pathCol) ?: "",
                        albumArtUri = artUri,
                        size        = cursor.getLong(sizeCol),
                        mimeType    = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    )
                )
            }
        }
        return tracks
    }

    /** Fallback: query genre via MediaStore.Audio.Genres for older APIs */
    private fun getGenreForTrack(context: Context, trackId: Long): String {
        val uri = MediaStore.Audio.Genres.getContentUriForAudioId("external", trackId.toInt())
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Genres.NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) ?: "" else ""
            } ?: ""
        } catch (e: Exception) { "" }
    }

    fun getTrackUri(trackId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
}
