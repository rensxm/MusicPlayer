package com.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicplayer.databinding.ItemPlaylistBinding
import com.musicplayer.models.Playlist

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit,
    private val onLongClick: (Playlist) -> Boolean
) : ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(DiffCb()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(p: Playlist) {
            b.tvPlaylistName.text = p.name
            b.root.setOnClickListener { onClick(p) }
            b.root.setOnLongClickListener { onLongClick(p) }
        }
    }

    class DiffCb : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
    }
}
