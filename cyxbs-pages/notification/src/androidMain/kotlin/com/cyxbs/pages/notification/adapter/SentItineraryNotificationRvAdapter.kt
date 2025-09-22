package com.cyxbs.pages.notification.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.dp2pxF
import com.cyxbs.components.utils.extensions.drawable
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.sp2dpF
import com.cyxbs.components.utils.extensions.string
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.util.Date

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/22
 * @Description: 针对已发送的行程是否取消提醒的item的两种状态，
 * 并未使用不同的ViewHold展示，而是根据当前行程的提醒状态分开渲染内容
 *
 */
class SentItineraryNotificationRvAdapter(
    private val onCancelReminderClick: ((itineraryId: Int, index: Int) -> Unit)? = null
) : ListAdapter<SentItineraryMsgBean, SentItineraryNotificationRvAdapter.VH>(sentItineraryDiffUtil) {
    companion object {
        private val sentItineraryDiffUtil: DiffUtil.ItemCallback<SentItineraryMsgBean> =
            object : DiffUtil.ItemCallback<SentItineraryMsgBean>() {
                override fun areItemsTheSame(
                  oldItem: SentItineraryMsgBean, newItem: SentItineraryMsgBean
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                  oldItem: SentItineraryMsgBean, newItem: SentItineraryMsgBean
                ): Boolean {
                    return oldItem.title == newItem.title &&
                            oldItem.content == newItem.content &&
                            oldItem.updateTime == newItem.updateTime &&
                            oldItem.hasCancel == newItem.hasCancel &&
                            oldItem.hasStart == newItem.hasStart
                }

            }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.notification_itinerary_tv_sent_title)
        val content: TextView = itemView.findViewById(R.id.notification_itinerary_tv_sent_content)
        val startTime: TextView =
            itemView.findViewById(R.id.notification_itinerary_tv_sent_generate_time)
        val unCancelDot: ImageView =
            itemView.findViewById(R.id.notification_itinerary_iv_status_uncancel_dot)
        val startStatusHint: TextView =
            itemView.findViewById(R.id.notification_itinerary_tv_start_status_hint)
        val canceledHint: TextView =
            itemView.findViewById(R.id.notification_itinerary_tv_canceled_hint)
        val cancelReminder: LinearLayout =
            itemView.findViewById(R.id.notification_itinerary_ll_cancel_reminder)

        init {
            onCancelReminderClick?.apply {
                cancelReminder.setOnSingleClickListener(1000) {
                    // 取消该行程的提醒
                    val position = bindingAdapterPosition
                    this.invoke(getItem(position).id, position)
                }
            }

            // 动态测绘给content留下的空间后，动态设置content的每行的最大文字数，以完成item内的文字对齐
            content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    val paddingDp = 17
                    val emsCount = ((itemView.width - 2 * paddingDp.dp2px) / 14.sp2dpF.dp2pxF)
                    content.setEms(emsCount.toInt())
                    return true
                }
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item_itiner_sent_normal, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val itemData = getItem(position)
        // 先绑定数据，再判断是否转变为取消状态
        bindItemData(holder, itemData)
        if (itemData.hasCancel) {
            item2canceledStatus(holder)
        }
    }

    private fun bindItemData(holder: VH, data: SentItineraryMsgBean) {
        holder.apply {
            title.text = String.format(R.string.notification_itinerary_item_rawTitle.string, data.title)
            content.text = data.content
            startTime.text = Date.getItineraryUpdateTime(data.publishTime)
            if (data.hasStart) {
                if (data.hasOver) {
                    item2overStatus(holder)
                } else
                    item2doingStatus(holder)
            }
        }
    }

    /**
     * 把行程item变为进行中的状态
     * @param holder
     */
    private fun item2doingStatus(holder: VH) {
        holder.startStatusHint.apply {
            setText(R.string.notification_isDoing)
            setTextColor(R.color.notification_itinerary_item_is_doing_hint_text.color)
        }
        holder.unCancelDot.background =
            R.drawable.notification_ic_itinerary_uncancel_dot_doing.drawable
    }

    /**
     * 把行程item变为已结束的状态
     * @param holder
     */
    private fun item2overStatus(holder: VH) {
        // 按照逻辑，行程已结束之后再取消提醒没有意义，故移除点击取消提醒的回调
        holder.cancelReminder.setOnClickListener(null)
        holder.startStatusHint.apply {
            setText(R.string.notification_hasOvered)
            setTextColor(R.color.notification_itinerary_item_has_overed_hint_text.color)
        }
        holder.unCancelDot.background =
            R.drawable.notification_ic_itinerary_uncancel_dot_over.drawable
    }

    /**
     * 把行程item变为已取消的状态
     * @param holder
     */
    private fun item2canceledStatus(holder: VH) {
        holder.cancelReminder.apply {
            // 移除点击回调，节省资源
            setOnClickListener(null)
            gone()
        }

        holder.unCancelDot.gone()
        holder.canceledHint.visible()
        textColor2canceled(holder)

    }

    /**
     * 把item部分文本的颜色设置为行程已取消的颜色
     * @param holder
     */
    private fun textColor2canceled(holder: VH) {
        holder.title.setTextColor(R.color.notification_itinerary_item_canceled_title_text.color)
        holder.content.setTextColor(R.color.notification_itinerary_item_canceled_content_text.color)
        holder.startTime.setTextColor(R.color.notification_itinerary_item_canceled_generate_time_text.color)
        holder.startStatusHint.setTextColor(R.color.notification_itinerary_item_canceled_start_status_hint_text.color)
        holder.canceledHint.setTextColor(R.color.notification_itinerary_item_canceled_hint_text.color)
    }
}