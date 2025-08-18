package com.cyxbs.pages.qa.detail.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.detail.viewmodel.QaDetailVm
import com.cyxbs.pages.qa.publish.ui.activity.PublishActivity
import com.google.android.flexbox.FlexboxLayout

/**
 * description ： QA详情页
 * author : wyf
 * date : 2025/8/11 23:23
 */
class DetailActivity : BaseActivity() {
    companion object {
        fun startActivity(context: Context, id: Long) {
            context.startActivity(Intent(context, DetailActivity::class.java).apply {
                putExtra(
                    "id",
                    id
                )
            })
        }
    }

    private val mViewModel by viewModels<QaDetailVm>()

    private var id: Long = -1L

    private var isLiked: Boolean = false

    private val qaDetailBack by R.id.qa_de_return.view<ImageView>()
    private val qaDetailPublish by R.id.qa_de_btn.view<Button>()
    private val qaDetailProblem by R.id.qa_de_tv.view<TextView>()
    private val qaDetailTime by R.id.qa_de_time.view<TextView>()
    private val qaDetailContent by R.id.qa_de_content.view<TextView>()
    private val qaDetailLike by R.id.qa_de_likecount.view<TextView>()
    private val qaDetailLook by R.id.qa_de_lookcount.view<TextView>()
    private val qaDetailLikeIm by R.id.qa_de_like.view<ImageView>()

    private val qaDetailTags by R.id.qa_de_tags.view<FlexboxLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_detail)
        id = intent.getLongExtra("id", 1)
        getDate()
        initClick()
        observeLike()
    }


    private fun initClick() {
        qaDetailBack.setOnClickListener {
            finish()
        }
        qaDetailPublish.setOnClickListener {
            PublishActivity.startActivity(this)
        }
        qaDetailLikeIm.setOnClickListener {
            isLiked = !isLiked
            applyStyle(isLiked)
            if (!isLiked) {
                mViewModel.cancelLike(id)
            } else {
                mViewModel.putLike(id)
            }
        }


    }

    private fun getDate() {
        Log.d("DetailDebug", "id: $id")
        mViewModel.getQaDetail(id)
        mViewModel.errorLiveData.observe {
            if (it) {
                toast("数据加载错误")
            }
        }
        mViewModel.detailLiveData.observe {
            qaDetailProblem.text = it.item.q
            qaDetailContent.text = it.item.a
            qaDetailLike.text = it.item.like_count.toString()
            qaDetailLook.text = it.item.view_count.toString()
            qaDetailTime.text = it.item.CreatedAt.substring(0, 10).replace("-", ".")
            isLiked = it.item.is_like
            applyStyle(isLiked)

            val tags = it.item.tags.split(" ").filter { it.isNotEmpty() }
            qaDetailTags.removeAllViews()
            if (tags.isNotEmpty()) {
                addTag(tags[0])
            }
            /*tags.forEach {
                addTag(it)
            }*/
        }

    }

    private fun applyStyle(isLike: Boolean) {
        if (isLike) {
            qaDetailLikeIm.setImageResource(R.drawable.qa_ic_publish_question_card_liked)
            qaDetailLike.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.qa_publish_question_card_liked_color
                )
            )

        } else {
            qaDetailLikeIm.setImageResource(R.drawable.qa_ic_publish_question_card_like)
            qaDetailLike.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.qa_text_content_color
                )
            )
        }
    }

    private fun observeLike() {
        mViewModel.likestatus.observe {
            if (it) {
                updateLikeCount()
            } else {
                isLiked = !isLiked
                applyStyle(isLiked)
                toast("点赞失败，请重试")
            }
        }
    }

    private fun updateLikeCount() {
        val likeCount = qaDetailLike.text.toString().toIntOrNull()
        val newLikeCount = if (isLiked) likeCount?.plus(1) else likeCount?.minus(1)
        qaDetailLike.text = newLikeCount.toString()
    }

    private fun addTag(tag: String) {
        val tagView = LayoutInflater.from(this)
            .inflate(R.layout.qa_flexbox_item_question_card_tag, qaDetailTags, false)

        tagView.findViewById<TextView>(R.id.qa_publish_tv_tag_item).apply {
            text = tag + "类"
        }
        qaDetailTags.addView(tagView)
    }
}