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

    var keyword: String = ""

    companion object {
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
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
                bind(updatedItem)

                // 调用 ViewModel 封装的方法处理点赞/取消
                if (updatedItem.is_like) {
                    searchViewModel.likeItem(updatedItem) {
                        // 失败回滚
                        currentData = data
                        bind(data)
                        Toast.makeText(itemView.context, "点赞失败，请重试", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    searchViewModel.unlikeItem(updatedItem) {
                        currentData = data
                        bind(data)
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

            question.text = highlightKeyword(item.q, keyword)
            val filterQuestion = item.a.ellipsis()
            answer.text = highlightKeyword(filterQuestion, keyword)
            time.text = item.a_time.substring(0, 10).replace("-", ".")
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.qa_recycle_item_question_item, parent, false)
        return ViewHolder(view)
    }
}
