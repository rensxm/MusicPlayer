package com.musicplayer.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val duration: Long,         // milliseconds
    val dateAdded: Long,        // unix timestamp
    val year: Int,
    val path: String,
    val albumArtUri: String?,
    val size: Long,
    val mimeType: String = ""
) {
    fun codecDisplay(): String {
        return when {
            mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3"
            mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "AAC / M4A"
            mimeType.contains("flac") -> "FLAC"
            mimeType.contains("ogg") -> "OGG"
            mimeType.contains("wav") || mimeType.contains("wave") -> "WAV"
            mimeType.contains("opus") -> "OPUS"
            mimeType.contains("wma") -> "WMA"
            mimeType.contains("aiff") -> "AIFF"
            path.endsWith(".mp3", true) -> "MP3"
            path.endsWith(".flac", true) -> "FLAC"
            path.endsWith(".ogg", true) -> "OGG"
            path.endsWith(".wav", true) -> "WAV"
            path.endsWith(".m4a", true) -> "AAC / M4A"
            path.endsWith(".aac", true) -> "AAC / M4A"
            path.endsWith(".opus", true) -> "OPUS"
            path.endsWith(".wma", true) -> "WMA"
            else -> mimeType.substringAfterLast("/").uppercase().ifBlank { "Unknown" }
        }
    }
    fun durationFormatted(): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun genreDisplay(): String = if (genre.isBlank() || genre == "<unknown>") "Unknown" else genre
    fun artistDisplay(): String = if (artist.isBlank() || artist == "<unknown>") "Unknown Artist" else artist
    fun albumDisplay(): String = if (album.isBlank() || album == "<unknown>") "Unknown Album" else album
}
