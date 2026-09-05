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

class FriendRequestAdapter(
    private val isIncoming: Boolean,
    private val onAcceptClick: ((FriendRequestUiModel) -> Unit)? = null,
    private val onDeclineClick: (FriendRequestUiModel) -> Unit
) : ListAdapter<FriendRequestUiModel, FriendRequestAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.text_name)
        private val textEmail: TextView = itemView.findViewById(R.id.text_email)
        private val btnAccept: ImageView = itemView.findViewById(R.id.btn_accept)
        private val btnDecline: ImageView = itemView.findViewById(R.id.btn_decline)

        fun bind(item: FriendRequestUiModel) {
            textName.text = item.displayName
            textEmail.text = item.email

            // Show/hide accept button based on incoming/outgoing
            btnAccept.visibility = if (isIncoming) View.VISIBLE else View.GONE

            btnAccept.setOnClickListener {
                onAcceptClick?.invoke(item)
            }

            btnDecline.setOnClickListener {
                onDeclineClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FriendRequestUiModel>() {
        override fun areItemsTheSame(
            oldItem: FriendRequestUiModel,
            newItem: FriendRequestUiModel
        ): Boolean {
            return oldItem.friendshipId == newItem.friendshipId
        }

        override fun areContentsTheSame(
            oldItem: FriendRequestUiModel,
            newItem: FriendRequestUiModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}
