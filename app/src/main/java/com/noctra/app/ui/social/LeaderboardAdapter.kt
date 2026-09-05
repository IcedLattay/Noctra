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

class LeaderboardAdapter(
    private val onEncourageClick: (LeaderboardEntryUiModel) -> Unit
) : ListAdapter<LeaderboardEntryUiModel, LeaderboardAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textRank: TextView = itemView.findViewById(R.id.text_rank)
        private val iconMedal: ImageView = itemView.findViewById(R.id.icon_medal)
        private val textFriendName: TextView = itemView.findViewById(R.id.text_friend_name)
        private val textStreak: TextView = itemView.findViewById(R.id.text_streak)
        private val btnEncourage: ImageView = itemView.findViewById(R.id.btn_encourage)

        fun bind(item: LeaderboardEntryUiModel) {
            textRank.text = "#${item.rank}"
            textFriendName.text = item.displayName
            textStreak.text = itemView.context.getString(R.string.social_day_streak, item.currentStreak)

            // Show medal for top 3
            if (item.isTopThree) {
                iconMedal.visibility = View.VISIBLE
                iconMedal.setImageResource(when (item.rank) {
                    1 -> R.drawable.ic_medal_gold
                    2 -> R.drawable.ic_medal_silver
                    3 -> R.drawable.ic_medal_bronze
                    else -> R.drawable.ic_medal_gold
                })
            } else {
                iconMedal.visibility = View.GONE
            }

            // Highlight current user
            if (item.isCurrentUser) {
                itemView.setBackgroundResource(R.drawable.bg_friend_request_row)
            } else {
                itemView.background = null
            }

            // Encouragement button
            btnEncourage.setOnClickListener {
                onEncourageClick(item)
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
