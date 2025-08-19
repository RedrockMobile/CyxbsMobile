package com.cyxbs.pages.qa.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.viewmodel.HomeViewModel

/**
 * description ： Qa主页的adapter
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 21:39
 */

class QaHomeRVAdapter(
    private val viewModel: HomeViewModel
) : PagingDataAdapter<Item, QaHomeRVAdapter.ViewHolder>(COMPARATOR) {

    companion object {
        private val COMPARATOR = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.ID == newItem.ID
            override fun areContentsTheSame(oldItem: Item, newItem: Item) =
                oldItem.ID == newItem.ID &&
                        oldItem.q == newItem.q &&
                        oldItem.a == newItem.a &&
                        oldItem.a_time == newItem.a_time &&
                        oldItem.is_like == newItem.is_like &&
                        oldItem.like_count == newItem.like_count
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root_view = itemView
        private val question = itemView.findViewById<TextView>(R.id.qa_question_item_tv_title)
        private val answer = itemView.findViewById<TextView>(R.id.qa_question_item_tv_content)
        private val time = itemView.findViewById<TextView>(R.id.qa_question_item_tv_time)
        private val likeNumber =
            itemView.findViewById<TextView>(R.id.qa_question_item_tv_likenumber)
        private val like = itemView.findViewById<ImageView>(R.id.qa_question_item_iv_like)
        private val mTag = itemView.findViewById<TextView>(R.id.qa_question_item_tv_tag)

        private var currentData: Item? = null
        private var lastClickTime = 0L
        private val CLICK_INTERVAL = 100L

        init {
            initClick()
        }

        private fun isNetworkAvailable(): Boolean = NetworkUtil.isAvailable ?: false

        private fun initClick() {
            // 点赞逻辑
            like.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < CLICK_INTERVAL) return@setOnClickListener
                lastClickTime = now

                if (!isNetworkAvailable()) {
                    Toast.makeText(itemView.context, "网络不可用", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val data = currentData ?: return@setOnClickListener

                if (data.is_like) {
                    viewModel.unlikeItem(data) {
                        Toast.makeText(itemView.context, "取消点赞失败，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    viewModel.likeItem(data) {
                        Toast.makeText(itemView.context, "点赞失败，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                // 根布局点击跳转
                root_view.setOnClickListener {

                }
            }
        }

        fun bind(item: Item) {
            currentData = item

            val tags = item.tags.split(" ").filter { it.isNotEmpty() }
            mTag.text = "${tags.getOrNull(0) ?: ""}类"

            question.text = item.q.ellipsis()
            answer.text = item.a
            time.text = item.a_time.substring(0, 10).replace("-", ".")
            likeNumber.text = item.like_count.toString()
            checkLike(item)
        }

        private fun String.ellipsis(maxLength: Int = 14) =
            if (this.length > maxLength) this.substring(0, maxLength) + "…" else this

        private fun checkLike(item: Item) {
            if (item.is_like) {
                like.setImageResource(R.drawable.qa_ic_question_item_like)
                likeNumber.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.qa_question_item_like_color)
                )
            } else {
                like.setImageResource(R.drawable.qa_ic_question_item_dislike)
                likeNumber.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.qa_question_item_unlike_color)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.qa_recycle_item_question_item, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

}
