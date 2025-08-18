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
import com.cyxbs.pages.qa.detail.ui.DetailActivity
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.publish.ui.adapter.QuestionCardAdapter.QuestionCardUI

/**
 * description ： Qa主页的adapter
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 21:39
 */

class QaHomeRVAdapter(
    private val onClick: (Item, (Boolean) -> Unit) -> Unit
) : PagingDataAdapter<Item, QaHomeRVAdapter.ViewHolder>(COMPARATOR) {

    companion object {
        private val COMPARATOR = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.ID == newItem.ID

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                // 只对真正有差异的字段触发刷新 为什么这里这样操作 是因为我们在点赞时会触发一次刷新(乐观刷新)这里会让paging误以为更新数据了导致paging又调一次
                return oldItem.ID == newItem.ID &&
                        oldItem.q == newItem.q &&
                        oldItem.a == newItem.a &&
                        oldItem.a_time == newItem.a_time &&
                        oldItem.is_like == newItem.is_like &&
                        oldItem.like_count == newItem.like_count
            }

        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root_view = itemView.rootView
        private val question = itemView.findViewById<TextView>(R.id.qa_question_item_tv_title)
        private val answer = itemView.findViewById<TextView>(R.id.qa_question_item_tv_content)
        private val time = itemView.findViewById<TextView>(R.id.qa_question_item_tv_time)
        private val likenumber =
            itemView.findViewById<TextView>(R.id.qa_question_item_tv_likenumber)
        private val like = itemView.findViewById<ImageView>(R.id.qa_question_item_iv_like)
        private var currentData: Item? = null

        private val mTag = itemView.findViewById<TextView>(R.id.qa_question_item_tv_tag)

        // 防抖点击
        private var lastClickTime = 0L
        private val CLICK_INTERVAL = 100L

        init {
            initClick()
        }

        private fun isNetworkAvailable(): Boolean {
            // 返回 true/false/null，null 可当作不可用处理
            return NetworkUtil.isAvailable ?: false
        }


        private fun initClick() {
            like.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < CLICK_INTERVAL) return@setOnClickListener
                lastClickTime = now

                if (!isNetworkAvailable()) {
                    Toast.makeText(itemView.context, "网络不可用，请检查网络", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                val data = currentData ?: return@setOnClickListener

                // 乐观更新
                val updatedItem = data.copy(
                    is_like = !data.is_like,
                    like_count = if (data.is_like) data.like_count - 1 else data.like_count + 1,
                    UpdatedAt = data.UpdatedAt ?: "" // 保证非空 删
                )

                currentData = updatedItem
                bind(updatedItem)

                // 调用外部 lambda（Fragment 或 Activity 的 HomeViewModel 方法）
                onClick.invoke(updatedItem) { success ->
                    if (!success) {
                        // 请求失败，回滚
                        currentData = data
                        bind(data)
                        Toast.makeText(itemView.context, "点赞失败，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            root_view.setOnClickListener {
                currentData?.let {
                    DetailActivity.startActivity(itemView.context,it.ID.toLong())
                }
            }
        }

        fun bind(item: Item) {
            item.let {
                val tags = item.tags.split(" ").filter { it.isNotEmpty() }
                mTag.text = "${tags[0]}类"
                currentData = it

                question.text = it.q.ellipsis()

                answer.text = it.a
                time.text = it.a_time.substring(0, 10).replace("-", ".")
                likenumber.text = it.like_count.toString()
                checkLike(it)
            }
        }

        fun String.ellipsis(maxLength: Int = 14): String {
            return if (this.length > maxLength) {
                this.substring(0, maxLength) + "…"
            } else {
                this
            }
        }

        fun checkLike(item: Item) {
            if (item.is_like) {
                like.setImageResource(R.drawable.qa_ic_question_item_like)
                likenumber.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.qa_question_item_like_color
                    )
                )
            } else {
                like.setImageResource(R.drawable.qa_ic_question_item_dislike)
                likenumber.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.qa_question_item_unlike_color
                    )
                )

            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.qa_recycle_item_question_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

}
