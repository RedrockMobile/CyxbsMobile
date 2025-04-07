package com.cyxbs.pages.notification.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.bean.SystemMsgBean
import com.cyxbs.pages.notification.util.Date

/**
 * Author by OkAndGreat
 * Date on 2022/4/30 15:09.
 */
class SystemNotificationRvAdapter(
    val onClick: (SystemMsgBean) -> Unit
) : ListAdapter<SystemMsgBean, SystemNotificationRvAdapter.InnerHolder>(
    object : DiffUtil.ItemCallback<SystemMsgBean>() {
        override fun areItemsTheSame(oldItem: SystemMsgBean, newItem: SystemMsgBean): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SystemMsgBean, newItem: SystemMsgBean): Boolean {
            return oldItem == newItem
        }
    }
) {
    inner class InnerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemSysNotificationClMain: View by lazy { itemView.findViewById(R.id.item_sys_notification_cl_main) }
        val itemSysNotificationIvRedDot: ImageView by lazy { itemView.findViewById(R.id.item_sys_notification_iv_red_dot) }
        val itemSysNotificationTvTitle: TextView by lazy { itemView.findViewById(R.id.item_sys_notification_tv_title) }
        val itemSysNotificationTvContent: TextView by lazy { itemView.findViewById(R.id.item_sys_notification_tv_content) }
        val itemSysNotificationTvTime: TextView by lazy { itemView.findViewById(R.id.item_sys_notification_tv_time) }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): InnerHolder {
        return InnerHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.notification_item_sys, parent, false)
        )
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        val data = getItem(position)
        if (data.has_read) holder.itemSysNotificationIvRedDot.visibility = View.INVISIBLE
        else holder.itemSysNotificationIvRedDot.visibility = View.VISIBLE
        holder.itemSysNotificationTvTitle.text = data.title
        holder.itemSysNotificationTvContent.text = data.content
        holder.itemSysNotificationTvTime.text = Date.getUnExactTime(data.publish_time)
        holder.itemSysNotificationClMain.setOnClickListener {
            onClick(data)
        }
    }
}