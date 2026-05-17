package com.cyxbs.pages.mine.page.feedback.edit.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.launchPickImages
import com.cyxbs.components.utils.extensions.registerForPickMultipleImages
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.feedback.adapter.rv.RvAdapter
import com.cyxbs.pages.mine.page.feedback.edit.viewmodel.FeedbackEditViewModel
import com.cyxbs.pages.mine.page.feedback.utils.FileUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * @Date : 2021/8/23   20:52
 * @By ysh
 * @Usage :
 * @Request : God bless my code
 **/
class FeedbackEditActivity : BaseActivity() {

    private val viewModel by viewModels<FeedbackEditViewModel>()

    private var label: String? = null

    /**
     * 注册新的图片选择器（最多3张）
     */
    private val pickImagesLauncher = registerForPickMultipleImages(3) { uris ->
        // 处理选中的图片
        if (uris.isNotEmpty()) {
            viewModel.updateSelectedImages(uris)
        }
    }

    /**
     * 初始化adapter
     */
    private val rvPicAdapter by lazy {
        RvAdapter(
            onAddClick = {
                // 启动新的图片选择器
                pickImagesLauncher.launchPickImages()
            },
            onRemoveClick = {
                viewModel.removePic(it)
            },
        )
    }

    private val etEditTitle by R.id.et_edit_title.view<EditText>()
    private val etEditDescription by R.id.et_edit_description.view<EditText>()
    private val chipGroup by R.id.chip_group.view<ChipGroup>()
    private val chipOne by R.id.chip_one.view<Chip>()
    private val chipTwo by R.id.chip_two.view<Chip>()
    private val chipThree by R.id.chip_three.view<Chip>()
    private val chipFour by R.id.chip_four.view<Chip>()
    private val rvBanner by R.id.rv_banner.view<RecyclerView>()
    private val tvTitle by R.id.tv_title.view<TextView>()
    private val btnBack by R.id.btn_back.view<View>()
    private val mineButton by R.id.mine_button.view<View>()
    private val tvTitleNum by R.id.tv_title_num.view<TextView>()
    private val tvDesNum by R.id.tv_des_num.view<TextView>()
    private val tvPicNum by R.id.tv_pic_num.view<TextView>()

    /**
     * 提供viewModel
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_feedback_edit)
        initView()
        observeData()
        initListener()
    }

    /**
     * 初始化view
     */
    private fun initView() {
        // TODO: 2021/8/23 自定义一个EditText 可实现以下功能：1.一键删除 2.选中时字体呈现不同颜色 3.超过字数限制会有提示动画
        //对两个editText进行监听初始化
        etEditDescription.addTextChangedListener(DesTextWatcher())
        etEditTitle.addTextChangedListener(TitleTextWatcher())
        //对四个chip进行初始化
        chipOne.setOnCheckedChangeListener(onCheckedChangeListener)
        chipTwo.setOnCheckedChangeListener(onCheckedChangeListener)
        chipThree.setOnCheckedChangeListener(onCheckedChangeListener)
        chipFour.setOnCheckedChangeListener(onCheckedChangeListener)
        //对rv进行初始化
        rvBanner.apply {
            adapter = rvPicAdapter
            layoutManager = GridLayoutManager(this@FeedbackEditActivity, 3)
        }
        tvTitle.text = resources.getText(R.string.mine_feedback_toolbar_title)
    }

    /**
     * 监听LiveData
     */
    @SuppressLint("SetTextI18n")
    private fun observeData() {
        viewModel.uris.observe {
            val list = if (it.size < 3) it + null else it
            rvPicAdapter.submitList(list.toMutableList())
            tvPicNum.text = "${it.size}/3"
        }
        viewModel.finishEvent.collectLaunch {
            finish()
        }
    }


    /**
     * 初始化listener
     */
    private fun initListener() {
        mineButton.setOnSingleClickListener {
            val label = label
            if (label == null) {
                toast("必须筛选一个标签")
                return@setOnSingleClickListener
            }
            //判断标题合法性
            if (etEditTitle.text.toString().isEmpty()
                ||
                etEditTitle.text.toString().replace(" ", "").isEmpty()
            ) {
                toast("标题内容不能为空哦~")
                return@setOnSingleClickListener
            }
            //判断内容合法性
            if (etEditDescription.text.toString().isEmpty()
                ||
                etEditDescription.text.toString().replace(" ", "").isEmpty()
            ) {
                toast("描述信息不能为空哦")
                return@setOnSingleClickListener
            }
            viewModel.uris.value?.let {
                if (it.isNotEmpty()) {
                    val files = it.map { FileUtils.uri2File(this@FeedbackEditActivity,it) }
                    viewModel.postFeedbackInfo(
                        productId = "1",
                        type = label,
                        title = etEditTitle.text.toString(),
                        content = etEditDescription.text.toString(),
                        file = files
                    )
                } else {
                    viewModel.postFeedbackInfo(
                        productId = "1",
                        type = label,
                        title = etEditTitle.text.toString(),
                        content = etEditDescription.text.toString(),
                        file = listOf()
                    )
                }
            }
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            label = if (checkedIds.isEmpty()) null else findViewById<Chip>(checkedIds.first()).text as String
        }

        btnBack.setOnSingleClickListener { finish() }
    }

    /**
     * 对chip是否选中的处理
     */
    private val onCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            buttonView as Chip
            if (isChecked) {
                buttonView.apply {
                    setChipStrokeColorResource(R.color.mine_edit_chip_border)
                    setTextColor(Color.parseColor("#4F4AE9"))
                }
            } else {
                buttonView.apply {
                    setChipStrokeColorResource(R.color.mine_edit_chip_border_un)
                    setTextColor(resources.getColor(R.color.mine_edit_chip_tv_un, context.theme))
                }
            }
        }

    /**
     * 内容详情的监听器
     */
    @SuppressLint("SetTextI18n")
    inner class DesTextWatcher : TextWatcher {
        init {
          tvDesNum.text = "0/200"
        }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            tvDesNum.text = "${p1 + p3}/200"
        }

        override fun afterTextChanged(p0: Editable?) {}
    }

    /**
     * 标题的监听器
     */
    @SuppressLint("SetTextI18n")
    inner class TitleTextWatcher : TextWatcher {
        init {
          tvTitleNum.text = "12"
        }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            tvTitleNum.text = (12 - p1 - p3).toString()
        }

        override fun afterTextChanged(p0: Editable?) {}
    }
}