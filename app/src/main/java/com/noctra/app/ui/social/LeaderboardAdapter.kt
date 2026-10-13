package com.noctra.app.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R

class LeaderboardAdapter : ListAdapter<LeaderboardEntryUiModel, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }

    // Position 0 is the static "Compete with Friends" banner header so it
    // scrolls with the cards; entries start at position 1
    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_leaderboard_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_leaderboard_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is EntryViewHolder) {
            holder.bind(getItem(position - 1))
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: androidx.cardview.widget.CardView = itemView.findViewById(R.id.card)
        private val cardContent: View = itemView.findViewById(R.id.card_content)
        private val textRank: TextView = itemView.findViewById(R.id.text_rank)
        private val textFriendName: TextView = itemView.findViewById(R.id.text_friend_name)
        private val textStreak: TextView = itemView.findViewById(R.id.text_streak)
        private val iconMedal: ImageView = itemView.findViewById(R.id.icon_medal)
        private val textYourRank: TextView = itemView.findViewById(R.id.text_your_rank)

        fun bind(item: LeaderboardEntryUiModel) {
            val density = itemView.resources.displayMetrics.density
            // Placeholder card — same outline as a regular card with a
            // lighter fill, content hidden, no shadow
            if (item.isPlaceholder) {
                card.setBackgroundResource(R.drawable.bg_leaderboard_placeholder)
                card.cardElevation = 0f
                cardContent.visibility = View.INVISIBLE
                iconMedal.visibility = View.GONE
                textYourRank.visibility = View.GONE
                return
            }
            cardContent.visibility = View.VISIBLE
            card.cardElevation = 2 * density

            textRank.text = "#${item.rank}"
            textFriendName.text = item.displayName
            textStreak.text = itemView.context.getString(R.string.social_day_streak, item.currentStreak)

            // Same top margin on every card so spacing stays even; the pill
            // and medal middles both land on the card's top edge
            val cardTop = (12 * density).toInt()
            (card.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = cardTop

            // Medal for top 3 — sticks out over the top-right corner
            if (item.isTopThree) {
                iconMedal.visibility = View.VISIBLE
                iconMedal.setImageResource(when (item.rank) {
                    1 -> R.drawable.ic_medal_gold
                    2 -> R.drawable.ic_medal_silver
                    3 -> R.drawable.ic_medal_bronze
                    else -> R.drawable.ic_medal_gold
                })
                (iconMedal.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin =
                    cardTop - (12 * density).toInt()
            } else {
                iconMedal.visibility = View.GONE
            }

            if (item.isCurrentUser) {
                card.setBackgroundResource(R.drawable.bg_leaderboard_current_user_outline)
                card.setCardBackgroundColor(itemView.context.getColor(android.R.color.transparent))
                textYourRank.visibility = View.VISIBLE
            } else {
                card.setBackgroundResource(R.drawable.bg_leaderboard_friend_outline)
                textYourRank.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LeaderboardEntryUiModel>() {
        override fun areItemsTheSame(
            oldItem: LeaderboardEntryUiModel,
            newItem: LeaderboardEntryUiModel
        ): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(
            oldItem: LeaderboardEntryUiModel,
            newItem: LeaderboardEntryUiModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}
