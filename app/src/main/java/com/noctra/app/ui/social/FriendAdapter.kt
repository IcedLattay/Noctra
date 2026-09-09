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
    }

    var equipment: Map<String, Map<String, com.noctra.app.data.model.ShopItem>> = emptyMap()

    // null = hide badge
    private var pendingBadgeCount: Int? = null

    fun setPendingBadge(count: Int) {
        pendingBadgeCount = if (count > 0) count else null
        notifyItemChanged(0)
    }

    // Position 0 is the banner + label header so it scrolls with the rows
    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_friends_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_friend_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind()
            is EntryViewHolder -> holder.bind(getItem(position - 1))
        }
    }

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
        private val avatarShleepy: ShleepyAvatarView = itemView.findViewById(R.id.avatar_shleepy)
        private val textFriendName: TextView = itemView.findViewById(R.id.text_friend_name)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)

        fun bind(item: LeaderboardEntryUiModel) {
            textFriendName.text = item.displayName
            avatarShleepy.setEquipped(equipment[item.userId] ?: emptyMap())
            btnRemove.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}
