package com.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.databinding.ItemTrackBinding
import com.musicplayer.models.Track

class TrackAdapter(
    private val onTrackClick: (Track) -> Unit,
    private val onTrackLongClick: (Track) -> Boolean
) : ListAdapter<Track, TrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    var currentPlayingId: Long = -1L
        set(value) {
            val old = currentList.indexOfFirst { it.id == field }
            val new = currentList.indexOfFirst { it.id == value }
            field = value
            if (old >= 0) notifyItemChanged(old)
            if (new >= 0) notifyItemChanged(new)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackViewHolder(private val binding: ItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track) {
            binding.apply {
                tvTitle.text    = track.title
                tvArtist.text   = track.artistDisplay()
                tvDuration.text = track.durationFormatted()
                tvGenre.text    = track.genreDisplay()

                // Album art
                Glide.with(root.context)
                    .load(track.albumArtUri)
                    .placeholder(R.drawable.ic_music_note_large)
                    .error(R.drawable.ic_music_note_large)
                    .centerCrop()
                    .into(ivAlbumArt)

                // Highlight playing
                val isPlaying = track.id == currentPlayingId
                root.isSelected = isPlaying
                tvTitle.setTextColor(
                    root.context.getColor(
                        if (isPlaying) R.color.accent else R.color.text_primary
                    )
                )

                root.setOnClickListener { onTrackClick(track) }
                root.setOnLongClickListener { onTrackLongClick(track) }
            }
        }
    }

    class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
        override fun areItemsTheSame(old: Track, new: Track) = old.id == new.id
        override fun areContentsTheSame(old: Track, new: Track) = old == new
    }
}
