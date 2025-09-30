package com.cyxbs.pages.qa.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel
import com.cyxbs.pages.qa.utils.longToWanString

/**
 * description ： 搜索界面的adapter
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/16 21:01
 */

class QaSearchRVAdapter(
    private val searchViewModel: SearchViewModel
) : ListAdapter<Item, QaSearchRVAdapter.ViewHolder>(COMPARATOR) {
    /*
      当前搜索关键字,每次搜索按钮点击后
       在观察者观察到数据后更新keyword
       用来处理新的高亮(关键字变色)
     */
    var keyword: String = ""

    companion object {
        //用来标志局部刷新
        const val PAYLOAD_LIKE = "payload_like"

        private val COMPARATOR = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.ID == newItem.ID

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem

            override fun getChangePayload(oldItem: Item, newItem: Item): Any? {
                return if (oldItem.is_like != newItem.is_like ||
                    oldItem.like_count != newItem.like_count
                ) PAYLOAD_LIKE else null
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootView = itemView
        private val question = itemView.findViewById<TextView>(R.id.qa_question_item_tv_title)
        private val answer = itemView.findViewById<TextView>(R.id.qa_question_item_tv_content)
        private val time = itemView.findViewById<TextView>(R.id.qa_question_item_tv_time)
        private val likenumber =
            itemView.findViewById<TextView>(R.id.qa_question_item_tv_likenumber)
        private val like = itemView.findViewById<ImageView>(R.id.qa_question_item_iv_like)
        private val mTag = itemView.findViewById<TextView>(R.id.qa_question_item_tv_tag)

        init {
            initClick()
        }

        private fun initClick() {
            rootView.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { listener?.invoke(it.ID.toLong()) }
            }

            like.setOnClickListener {
                val data = getItem(bindingAdapterPosition) ?: return@setOnClickListener
                searchViewModel.toggleLikeItem(data.ID, data.is_like)
            }
        }

        fun bind(item: Item) {
            val tags = item.tags.split(" ").filter { it.isNotEmpty() }
            mTag.text = "${tags[0]}类"

            val filterQuestion = item.q
            question.text = highlightKeyword(filterQuestion, keyword)
            answer.text = highlightKeyword(item.a, keyword)
            time.text = item.a_time.substring(0, 10).replace("-", ".")
            bindLike(item)
        }

        fun bindLike(item: Item) {
            likenumber.text = longToWanString(item.like_count)
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

        //用来处理高亮的方法
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.qa_recycle_item_question_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            getItem(position)?.let { holder.bind(it) }
        } else {
            getItem(position)?.let { item ->
                payloads.forEach {
                    if (it == PAYLOAD_LIKE) {
                        holder.bindLike(item)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    private var listener: ((Long) -> Unit)? = null
    fun setOnItemClickListener(listener: ((Long) -> Unit)?) {
        this.listener = listener
    }
}
