package com.noctra.app.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R

class FriendAdapter(
    private val onRemoveClick: (LeaderboardEntryUiModel) -> Unit,
    private val onBannerClick: () -> Unit
) : ListAdapter<LeaderboardEntryUiModel, RecyclerView.ViewHolder>(LeaderboardAdapter.DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
        private const val VIEW_TYPE_EMPTY = 2
    }

    // null = hide badge
    private var pendingBadgeCount: Int? = null

    fun setPendingBadge(count: Int) {
        pendingBadgeCount = if (count > 0) count else null
        notifyItemChanged(0)
    }

    // Position 0 is the banner + label header so it scrolls with the rows.
    // When there are no friends, position 1 shows the empty placeholder.
    override fun getItemCount(): Int = super.getItemCount() + if (super.getItemCount() == 0) 2 else 1

    override fun getItemViewType(position: Int): Int =
        when {
            position == 0 -> VIEW_TYPE_HEADER
            super.getItemCount() == 0 -> VIEW_TYPE_EMPTY
            else -> VIEW_TYPE_ENTRY
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_friends_header, parent, false))
            VIEW_TYPE_EMPTY -> EmptyViewHolder(inflater.inflate(R.layout.item_friends_empty, parent, false))
            else -> EntryViewHolder(inflater.inflate(R.layout.item_friend_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind()
            is EntryViewHolder -> holder.bind(getItem(position - 1))
            // EmptyViewHolder is static text, nothing to bind
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val banner: View = view.findViewById(R.id.banner_card)
        private val badge: TextView = view.findViewById(R.id.text_banner_badge)

        fun bind() {
            banner.setOnClickListener { onBannerClick() }
            val count = pendingBadgeCount
            if (count != null) {
                badge.visibility = View.VISIBLE
                badge.text = if (count > 9) "9+" else count.toString()
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textFriendName: TextView = itemView.findViewById(R.id.text_friend_name)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)

        fun bind(item: LeaderboardEntryUiModel) {
            textFriendName.text = item.displayName
            btnRemove.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}
