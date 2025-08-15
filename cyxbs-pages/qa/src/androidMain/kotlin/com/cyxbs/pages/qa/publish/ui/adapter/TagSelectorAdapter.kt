package com.cyxbs.pages.qa.publish.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.qa.R

/**
 * description ： 标签Tag选择Rv的Adapter
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 13:02
 */
class TagSelectorAdapter(
    private val selectedBgRes: Int,
    private val defaultBgRes: Int,
    private val selectedTextColorRes: Int,
    private val defaultTextColorRes: Int
) :
    ListAdapter<String, TagSelectorAdapter.TagViewHolder>(object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }) {
    // 标签是否可以点击
    private var isTagClickable = true

    // 存选中标签
    private val selectedTags = mutableSetOf<String>()

    inner class TagViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val mTag = view.findViewById<TextView>(R.id.qa_publish_tv_tag_item)

        fun bind(tag: String) {
            mTag.text = tag
            val isSelected = selectedTags.contains(tag)
            applyStyle(isSelected)

        }

        private fun applyStyle(state: Boolean) {
            val context = itemView.context
            if (state) {
                view.background = AppCompatResources.getDrawable(context, selectedBgRes)
                mTag.setTextColor(ContextCompat.getColor(context, selectedTextColorRes))
                view.isSelected = true
            } else {
                view.background = AppCompatResources.getDrawable(context, defaultBgRes)
                mTag.setTextColor(ContextCompat.getColor(context, defaultTextColorRes))
                view.isSelected = false
            }
        }

        init {
            view.setOnClickListener {
                if (isTagClickable) {
                    val tag = mTag.text.toString()
                    val newState = !selectedTags.contains(tag)
                    //更新数据源
                    if (newState) {
                        selectedTags.add(tag)
                    } else {
                        selectedTags.remove(tag)
                    }
                    applyStyle(newState)
                }
            }
        }
    }

    //返回选中的Tag合辑
    fun getSelectTag(): Set<String> {
        return selectedTags.toSet()
    }

    //返回选中的标签的字符串
    //用 空格 分隔
    fun getSelectedTagString() =
        selectedTags
            .joinToString(" ") { it.removeSuffix("类") }
            .ifBlank { " " }

    //Tag是否可以被选择
    fun requestTagClickable(clickable: Boolean) {
        isTagClickable = clickable
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        return TagViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.qa_recycle_item_tag, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}