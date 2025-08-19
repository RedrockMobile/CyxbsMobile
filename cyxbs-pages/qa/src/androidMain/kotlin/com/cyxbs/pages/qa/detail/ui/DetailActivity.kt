package com.cyxbs.pages.qa.detail.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.detail.bean.QuestionItem
import com.cyxbs.pages.qa.detail.viewmodel.QaDetailVm
import com.cyxbs.pages.qa.publish.ui.activity.PublishActivity

/**
 * description ： QA详情页
 * author : wyf
 * date : 2025/8/11 23:23
 */
class DetailActivity : BaseActivity() {
    companion object {
        private const val EXTRA_QUESTION_ID = "id"

        fun startActivity(context: Context, id: Long) {
            context.startActivity(Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_QUESTION_ID, id)
            })
        }
    }

    private val mViewModel by viewModels<QaDetailVm>()
    private val qaDetailBack by R.id.qa_detail_btn_back.view<ImageView>()
    private val qaDetailPublish by R.id.qa_detail_btn_publish.view<Button>()
    private val qaDetailProblem by R.id.qa_detail_tv_question.view<TextView>()
    private val qaDetailTime by R.id.qa_detail_tv_time.view<TextView>()
    private val qaDetailContent by R.id.qa_detail_tv_answer.view<TextView>()
    private val qaDetailLike by R.id.qa_detail_tv_like_count.view<TextView>()
    private val qaDetailLook by R.id.qa_detail_tv_looked_count.view<TextView>()
    private val qaDetailLikeIm by R.id.qa_detail_iv_like.view<ImageView>()

    private val qaDetailTags by R.id.qa_detail_tv_tag.view<TextView>()
    private val qaIvNoNet by R.id.qa_detail_iv_no_net.view<ImageView>()
    private val qaTvNoNet by R.id.qa_detail_tv_no_net.view<TextView>()
    private val qaContent by R.id.qa_detail_sv_content.view<ScrollView>()
    private var isInit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_detail)
        val id = intent.getLongExtra(EXTRA_QUESTION_ID, 1L)
        mViewModel.setId(id)

        initClick()
        observerData()
        getDate()

    }

    private fun getDate() {
        mViewModel.getQaDetail()
    }


    private fun initClick() {
        qaDetailBack.setOnClickListener {
            finish()
        }

        qaDetailPublish.setOnClickListener {
            PublishActivity.startActivity(this)
        }

        qaDetailLikeIm.setOnClickListener {
            mViewModel.toggleLikeQuestion()
        }

        qaDetailBack.setOnClickListener {
            finish()
        }
    }

    private fun observerData() {
        mViewModel.apply {
            questionData.observe {
                bind(it)
            }
            searchError.observe {
                if (it) {
                    qaContent.gone()
                    qaIvNoNet.visible()
                    qaTvNoNet.visible()
                }
            }
            likeError.observe {
                if (it) {
                    toast("网络异常")
                }
            }


        }
    }

    private fun bind(data: QuestionItem) {
        if (!isInit) {
            qaDetailProblem.text = data.q
            qaDetailContent.text = data.a
            qaDetailLook.text = data.viewCount.toString()
            qaDetailTime.text = data.createdAt.substring(0, 10).replace("-", ".")
            val tags = data.tags.split(" ").filter { it.isNotEmpty() }
            qaDetailTags.text = tags[0]
            isInit = true//标记已经初始化
        }
        qaDetailLike.text = data.likeCount.toString()
        val isLiked = data.isLike
        applyStyle(isLiked)
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

}