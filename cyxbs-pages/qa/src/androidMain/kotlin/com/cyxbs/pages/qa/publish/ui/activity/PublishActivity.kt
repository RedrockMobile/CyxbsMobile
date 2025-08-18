package com.cyxbs.pages.qa.publish.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View.OVER_SCROLL_NEVER
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.publish.ui.adapter.QuestionCardAdapter
import com.cyxbs.pages.qa.publish.ui.adapter.QuestionCardAdapter.QuestionCardUI
import com.cyxbs.pages.qa.publish.ui.adapter.TagSelectorAdapter
import com.cyxbs.pages.qa.publish.viewmodel.PublishViewModel
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent


/**
 * description ： 发布问题的Activity
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/11 23:20
 */
class PublishActivity : BaseActivity() {
    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, PublishActivity::class.java))
        }
    }

    private val viewModel by viewModels<PublishViewModel>()


    //返回键
    private val qaPublishBtnBack by R.id.qa_publish_btn_back.view<ImageView>()

    //发布
    private val mBtnPublish by R.id.qa_publish_btn_publish.view<Button>()

    //编辑栏
    private val mEditText by R.id.qa_publish_et_question.view<EditText>()

    //搜索结果
    private val mRvSearch by R.id.qa_publish_rv_question_card.view<RecyclerView>()

    private val selectedBg by lazy {
        AppCompatResources.getDrawable(
            this,
            R.drawable.qa_shape_publish_tag_selected_bg
        )
    }
    private val defaultBg by lazy {
        AppCompatResources.getDrawable(
            this,
            R.drawable.qa_shape_publish_tag_default_bg
        )
    }
    private val selectedTextColor by lazy {
        ContextCompat.getColor(
            this,
            R.color.qa_publish_tag_selected_text_color
        )
    }
    private val defaultTextColor by lazy {
        ContextCompat.getColor(
            this,
            R.color.qa_publish_tag_default_text_color
        )
    }

    //标签选择器的RV
    private val mRvTagSelector by R.id.qa_publish_rv_tag_selector.view<RecyclerView>()
        .addInitialize {
            val flexboxLayoutManager = FlexboxLayoutManager(this@PublishActivity)
                .apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                    justifyContent = JustifyContent.SPACE_BETWEEN
                    maxLine = 2
                }
            this.layoutManager = flexboxLayoutManager
            this.overScrollMode = OVER_SCROLL_NEVER
            this.isNestedScrollingEnabled = false
        }

    private val tagSelectorAdapter by lazy {
        TagSelectorAdapter(
            viewModel.dataTag,
            R.drawable.qa_shape_publish_tag_selected_bg,
            R.drawable.qa_shape_publish_tag_default_bg,
            R.color.qa_publish_tag_selected_text_color,
            R.color.qa_publish_tag_default_text_color
        ).apply {
            setOnTagClickListener { status, pos ->
                //先将所有的style置为false
                viewModel.dataTag.fastForEachIndexed { index, _ ->
                    changeBtnStyle(false, index, mRvTagSelector)
                }
                //将特定位置设置为选中
                changeBtnStyle(status, pos, mRvTagSelector)

            }
        }
    }

    private val questionCardAdapter = QuestionCardAdapter()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_publish)
        initView()
        initClick()
        observeData()
    }

    private fun observeData() {
        viewModel.apply {
            searchData.observe {
                if (mEditText.isVisible) {
                    //展示现相似问题
                    mRvSearch.visible()
                    mEditText.gone()
                    //禁用选择Tag
                    tagSelectorAdapter.requestTagClickable(false)
                }
                //拼接的问题卡片数据的列表（问题 + 问题卡片）
                questionCardAdapter.submitList(
                    listOf(QuestionCardUI.Header(viewModel.getCurrentQuestion())) +
                            it.map(QuestionCardUI::QuestionItem)
                )

            }

            publishSuccess.observe {
                if (it) {
                    toastLong("我们已收到你的反馈")
                    finish()
                } else {
                    toast("发布失败请检查网络连接~")
                }
            }
            likeResult.observe {
                if (!it) {
                    toast("网络异常")
                }
            }
        }
    }

    private fun initView() {
        //初始化RvTagSelector
        mRvTagSelector.adapter = tagSelectorAdapter
        mRvSearch.adapter = questionCardAdapter
        mRvSearch.layoutManager = LinearLayoutManager(this)

        questionCardAdapter.setOnLikeClickListener { it ->
            viewModel.toggleLikeQuestion(it.data.id, it.data.isLike)
        }


        if (mEditText.isVisible) {
            //弹出软键盘
            //获取焦点
            mEditText.requestFocus()
            // 显示软键盘
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?)?.showSoftInput(
                mEditText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun initClick() {
        qaPublishBtnBack.setOnClickListener {
            finish()
        }
        mBtnPublish.setOnClickListener {
            if (!mEditText.isVisible) {
                //如果编辑栏已经隐藏了，则说明搜索到了相似回答，禁用发布键
                return@setOnClickListener
            }
            if (tagSelectorAdapter.getSelectedTagString().isNotBlank()
                && mEditText.text.toString().isNotBlank()
            ) {
                viewModel.setCurrentQuestion(mEditText.text.toString())
                viewModel.publishQuestion(
                    mEditText.text.toString(),
                    tagSelectorAdapter.getSelectedTagString()
                )
                //检查mEditText是否有焦点
                if (mEditText.hasFocus()) {
                    // 获取输入方法管理器
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    // 关闭软键盘
                    imm.hideSoftInputFromWindow(mEditText.windowToken, 0)
                    mEditText.clearFocus()
                }
            } else {
                toast("问题和标签不能为空")
            }
        }
    }

    fun changeBtnStyle(
        state: Boolean,
        position: Int,
        rv: RecyclerView,
    ) {
        rv.layoutManager?.findViewByPosition(position)?.let {
            it.findViewById<TextView>(R.id.qa_publish_tv_tag_item)
                ?.apply {
                    if (state) {
                        background = selectedBg
                        setTextColor(selectedTextColor)
                    } else {
                        background = defaultBg
                        setTextColor(defaultTextColor)
                    }
                }
        }
    }
}