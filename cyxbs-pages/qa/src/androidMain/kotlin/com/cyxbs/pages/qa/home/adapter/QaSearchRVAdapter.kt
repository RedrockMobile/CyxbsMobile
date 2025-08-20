package com.cyxbs.pages.qa.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel

/**
 * description ： 搜索界面的adapter
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/16 21:01
 */
class QaSearchRVAdapter(
    private val searchViewModel: SearchViewModel
) : ListAdapter<Item, QaSearchRVAdapter.ViewHolder>(COMPARATOR) {

    var keyword: String = "" // 当前搜索关键字

    companion object {
        const val PAYLOAD_LIKE = "payload_like"

        private val COMPARATOR = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.ID == newItem.ID

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.ID == newItem.ID &&
                        oldItem.q == newItem.q &&
                        oldItem.a == newItem.a &&
                        oldItem.a_time == newItem.a_time &&
                        oldItem.is_like == newItem.is_like &&
                        oldItem.like_count == newItem.like_count

            override fun getChangePayload(oldItem: Item, newItem: Item): Any? {
                return if (oldItem.is_like != newItem.is_like ||
                    oldItem.like_count != newItem.like_count
                ) {
                    PAYLOAD_LIKE
                } else null
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootView = itemView.rootView
        private val question = itemView.findViewById<TextView>(R.id.qa_question_item_tv_title)
        private val answer = itemView.findViewById<TextView>(R.id.qa_question_item_tv_content)
        private val time = itemView.findViewById<TextView>(R.id.qa_question_item_tv_time)
        private val likenumber =
            itemView.findViewById<TextView>(R.id.qa_question_item_tv_likenumber)
        private val like = itemView.findViewById<ImageView>(R.id.qa_question_item_iv_like)
        private val mTag = itemView.findViewById<TextView>(R.id.qa_question_item_tv_tag)

        private var currentData: Item? = null
        private var lastClickTime = 0L
        private val CLICK_INTERVAL = 100L

        init {
            initClick()
        }

        private fun initClick() {
            rootView.setOnClickListener {
                currentData?.let { listener?.invoke(it.ID.toLong()) }
            }

            like.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < CLICK_INTERVAL) return@setOnClickListener
                lastClickTime = now

                val data = currentData ?: return@setOnClickListener

                // 乐观更新
                val updatedItem = data.copy(
                    is_like = !data.is_like,
                    like_count = if (data.is_like) data.like_count - 1 else data.like_count + 1,
                    UpdatedAt = data.UpdatedAt ?: ""
                )
                currentData = updatedItem
                bindLike(updatedItem)

                if (updatedItem.is_like) {
                    searchViewModel.likeItem(updatedItem) {
                        currentData = data
                        bindLike(data)
                        Toast.makeText(itemView.context, "点赞失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    searchViewModel.unlikeItem(updatedItem) {
                        currentData = data
                        bindLike(data)
                        Toast.makeText(itemView.context, "取消点赞失败，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }

        fun bind(item: Item) {
            currentData = item
            val tags = item.tags.split(" ").filter { it.isNotEmpty() }
            mTag.text = "${tags[0]}类"

            // 高亮使用 Adapter 的 keyword，每次绑定都刷新
            question.text = highlightKeyword(item.q, this@QaSearchRVAdapter.keyword)
            val filterAnswer = item.a.ellipsis()
            answer.text = highlightKeyword(filterAnswer, this@QaSearchRVAdapter.keyword)
            time.text = item.a_time.substring(0, 10).replace("-", ".")
            bindLike(item)
        }

        fun bindLike(item: Item) {
            likenumber.text = item.like_count.toString()
            checkLike(item)
        }

        private fun String.ellipsis(maxLength: Int = 14): String =
            if (this.length > maxLength) this.substring(0, maxLength) + "…" else this

        private fun checkLike(item: Item) {
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

        private fun highlightKeyword(text: String, keyword: String): CharSequence {
            if (keyword.isEmpty()) return text
            val spannable = android.text.SpannableString(text)
            var startIndex = text.indexOf(keyword, ignoreCase = true)
            while (startIndex >= 0) {
                val endIndex = startIndex + keyword.length
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(
                            itemView.context,
                            com.cyxbs.components.config.R.color.config_blue_button
                        )
                    ),
                    startIndex,
                    endIndex,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = text.indexOf(keyword, startIndex + keyword.length, ignoreCase = true)
            }
            return spannable
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_LIKE)) {
            getItem(position)?.let { holder.bindLike(it) }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.qa_recycle_item_question_item, parent, false)
        return ViewHolder(view)
    }

    private var listener: ((Long) -> Unit)? = null
    fun setOnItemClickListener(listener: ((Long) -> Unit)?) {
        this.listener = listener
    }
}