package com.cyxbs.pages.qa.publish.ui.adapter

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
import com.cyxbs.pages.qa.detail.ui.DetailActivity
import com.cyxbs.pages.qa.publish.network.bean.response.SearchData
import com.cyxbs.pages.qa.publish.ui.adapter.QuestionCardAdapter.QuestionCardUI
import com.cyxbs.pages.qa.utils.longToWanString

/**
 * description ： 问题卡片的RVAdapter
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/13 21:37
 */
class QuestionCardAdapter :
	ListAdapter<QuestionCardUI, RecyclerView.ViewHolder>(
		object : DiffUtil.ItemCallback<QuestionCardUI>() {
			override fun areItemsTheSame(
				oldItem: QuestionCardUI,
				newItem: QuestionCardUI
			): Boolean {
				return when {
					oldItem is QuestionCardUI.Header && newItem is QuestionCardUI.Header ->
						oldItem.title == newItem.title

					oldItem is QuestionCardUI.QuestionItem && newItem is QuestionCardUI.QuestionItem ->
						oldItem.data.id == newItem.data.id

					else -> false
				}
			}

			override fun areContentsTheSame(
				oldItem: QuestionCardUI,
				newItem: QuestionCardUI
			): Boolean {
				return oldItem == newItem
			}

			//局部绑定
			//当数据变化只是点赞状态时
			override fun getChangePayload(oldItem: QuestionCardUI, newItem: QuestionCardUI): Any? {
				if (oldItem is QuestionCardUI.QuestionItem && newItem is QuestionCardUI.QuestionItem) {
					if (oldItem.data.isLike != newItem.data.isLike || oldItem.data.likeCount != newItem.data.likeCount) {
						return PAYLOAD_LIKE
					}
				}
				return null
			}
		}
	) {

	companion object {
		private const val VIEW_TYPE_HEADER = 0
		private const val VIEW_TYPE_ITEM = 1

		private const val PAYLOAD_LIKE = "payload_like"
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_ITEM -> QuestionCardViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.qa_recycle_item_question_card, parent, false)
			)

			else -> QuestionCardHeaderViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.qa_recycle_item_question_card_header, parent, false)
			)

		}
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int
	) {
		when (val item = getItem(position)) {
			is QuestionCardUI.Header -> (holder as QuestionCardHeaderViewHolder).bind(item.title)
			is QuestionCardUI.QuestionItem -> (holder as QuestionCardViewHolder).bind(item.data)
		}
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int,
		payloads: List<Any?>
	) {
		val item = getItem(position)
		if (payloads.isEmpty()) {
			//全量绑定
			onBindViewHolder(holder, position)
		} else {
			//局部刷新
			if (item is QuestionCardUI.QuestionItem && holder is QuestionCardViewHolder) {
				payloads.forEach {
					if (it == PAYLOAD_LIKE) {
						holder.bindLike(item.data)
					}
				}
			}
		}
	}


	override fun getItemViewType(position: Int): Int {
		return when (getItem(position)) {
			is QuestionCardUI.Header -> VIEW_TYPE_HEADER
			is QuestionCardUI.QuestionItem -> VIEW_TYPE_ITEM
		}
		return super.getItemViewType(position)
	}


	inner class QuestionCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val mTitle = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_question)
		val mTag = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_tag)
		val mAnswer = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_answer)
		val mTime = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_time)
		val likeCount = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_like_count)

		//点赞按钮
		val mLike = view.findViewById<ImageView>(R.id.qa_publish_iv_question_card_like)

		init {
			view.setOnClickListener {
				DetailActivity.startActivity(
					itemView.context,
					(getItem(absoluteAdapterPosition) as QuestionCardUI.QuestionItem).data.id
				)
			}

			mLike.setOnClickListener {
				listener?.invoke(getItem(absoluteAdapterPosition) as QuestionCardUI.QuestionItem)
			}
		}

		fun bind(data: SearchData) {
			//把Tag分割出来
			val tags = data.tags.split(" ").filter { it.isNotEmpty() }
			mTag.text = "${tags[0]}类"

			mTitle.text = data.q
			mAnswer.text = data.a
			likeCount.text = longToWanString(data.likeCount)
			mTime.text = data.aTime.substring(0, 10).replace("-", ".")

			applyStyle(data.isLike)
		}

		fun bindLike(item: SearchData) {
			//局部刷新
			likeCount.text = longToWanString(item.likeCount)
			applyStyle(item.isLike)
		}

		private fun applyStyle(isLike: Boolean) {
			val context = itemView.context
			if (isLike) {
				mLike.setImageResource(R.drawable.qa_ic_publish_question_card_liked)
				likeCount.setTextColor(
					ContextCompat.getColor(
						context,
						R.color.qa_publish_question_card_liked_color
					)
				)

			} else {
				mLike.setImageResource(R.drawable.qa_ic_publish_question_card_like)
				likeCount.setTextColor(
					ContextCompat.getColor(
						context,
						R.color.qa_text_content_color
					)
				)
			}
		}
	}

	inner class QuestionCardHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val mTittle = view.findViewById<TextView>(R.id.qa_publish_tv_question_card_header_title)

		fun bind(tittle: String) {
			mTittle.text = tittle
		}

		init {
			mTittle.setOnClickListener {
				titleClickListener?.invoke()
			}
		}
	}

	sealed class QuestionCardUI {
		data class Header(val title: String) : QuestionCardUI()
		data class QuestionItem(val data: SearchData) : QuestionCardUI()
	}


	//点赞事件的监听
	private var listener: ((data: QuestionCardUI.QuestionItem) -> Unit)? = null

	private var titleClickListener: (() -> Unit)? = null

	fun setOnLikeClickListener(listener: (data: QuestionCardUI.QuestionItem) -> Unit) {
		this.listener = listener
	}

	fun setOnTitleClickListener(listener: () -> Unit) {
		this.titleClickListener = listener
	}
}