package com.cyxbs.pages.qa.publish.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.publish.ui.adapter.TagSelectorAdapter.TagViewHolder
import com.cyxbs.pages.qa.publish.viewmodel.PublishViewModel

/**
 * description ： 标签Tag选择Rv的Adapter
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 13:02
 */
class TagSelectorAdapter(
	private val data: List<String>,
	private val selectedBgRes: Int,
	private val defaultBgRes: Int,
	private val selectedTextColorRes: Int,
	private val defaultTextColorRes: Int,
	private val viewModel: PublishViewModel
) : RecyclerView.Adapter<TagViewHolder>() {
	// 标签是否可以点击
	private var isTagClickable = true


	inner class TagViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
		val mTag = view.findViewById<TextView>(R.id.qa_publish_tv_tag_item)

		fun bind(tag: String) {
			mTag.text = tag
			val isSelected = viewModel.getSelectTag() == tag
			applyStyle(isSelected)

		}

		private fun applyStyle(state: Boolean) {
			val context = itemView.context
			if (state) {
				view.background = AppCompatResources.getDrawable(context, selectedBgRes)
				mTag.setTextColor(ContextCompat.getColor(context, selectedTextColorRes))
			} else {
				view.background = AppCompatResources.getDrawable(context, defaultBgRes)
				mTag.setTextColor(ContextCompat.getColor(context, defaultTextColorRes))
			}
		}

		init {
			view.setOnClickListener {
				if (!isTagClickable) {
					return@setOnClickListener
				}
				//如果新选择的string和已经选择的不一样，则代表新点击的为选中
				val isSelect = viewModel.getSelectTag() != mTag.text.toString()
				if (isSelect) {
					viewModel.setSelectTag(mTag.text.toString())
				} else {
					//取消选择
					viewModel.setSelectTag("")
				}
				listener?.invoke(isSelect, absoluteAdapterPosition)
			}
		}
	}

	//返回选中的Tag
	fun getSelectedTagString(): String {
		return viewModel.getSelectTag()?.removeSuffix("类") ?: ""
	}

	//是否有标签被选择
	fun isSelected(): Boolean {
		return !viewModel.getSelectTag().isNullOrEmpty()
	}

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

	fun getItem(pos: Int) = data[pos]

	override fun getItemCount(): Int {
		return data.size
	}

	private var listener: ((state: Boolean, position: Int) -> Unit)? = null

	fun setOnTagClickListener(listener: ((state: Boolean, position: Int) -> Unit)) {
		this.listener = listener
	}


}