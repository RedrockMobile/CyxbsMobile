package com.cyxbs.pages.food.ui.activity

import android.animation.ObjectAnimator
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.dailog.BaseChooseDialog
import com.cyxbs.components.base.dailog.ChooseDialog
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.FOOD_ENTRY
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setImageFromUrl
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.food.R
import com.cyxbs.pages.food.ui.adapters.FoodMainRvAdapter
import com.cyxbs.pages.food.ui.view.FoodDetailDialog
import com.cyxbs.pages.food.ui.view.FoodMainDialog
import com.cyxbs.pages.food.viewmodel.FoodMainViewModel
import com.cyxbs.pages.store.api.IStoreService
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager

@KClassProvider(clazz = Activity::class, name = FOOD_ENTRY)
class FoodMainActivity : BaseActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[FoodMainViewModel::class.java] }
    private val mRvRegion by R.id.food_main_rv_canteen_region.view<RecyclerView>()
        .addInitialize {
            val flexboxLayoutManager = FlexboxLayoutManager(this@FoodMainActivity)
            flexboxLayoutManager.apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            this.layoutManager = flexboxLayoutManager
            this.overScrollMode = 2
            this.isNestedScrollingEnabled = false
        }

    private val mRvNumber by R.id.food_main_rv_canteen_number.view<RecyclerView>()
        .addInitialize {
            val flexboxLayoutManager = FlexboxLayoutManager(this@FoodMainActivity)
            flexboxLayoutManager.apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            this.layoutManager = flexboxLayoutManager
            this.overScrollMode = 2
            this.isNestedScrollingEnabled = false
        }

    private val mRvFeature by R.id.food_main_rv_canteen_feature.view<RecyclerView>()
        .addInitialize {
            val flexboxLayoutManager = FlexboxLayoutManager(this@FoodMainActivity)
            flexboxLayoutManager.apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            this.layoutManager = flexboxLayoutManager
            this.isNestedScrollingEnabled = false
            this.overScrollMode = 2
        }

    private val mImgNotification by R.id.food_main_img_notification.view<ImageView>()

    private val mBtnChange by R.id.food_main_btn_change.view<Button>()
    private val mImgBack by R.id.food_main_img_return.view<ImageView>()
    private val mBtnDetermine by R.id.food_main_btn_determine.view<Button>()
    private val mTvMealNew by R.id.food_main_tv_meal_new.view<TextView>()
    private val mTvMealOld by R.id.food_main_tv_meal_old.view<TextView>()
    private val mBtnDetail by R.id.food_main_btn_detail.view<Button>()
    private val mImgRefresh by R.id.food_main_img_refresh.view<ImageView>()
    private val mImgPicture by R.id.food_main_img_picture.view<ImageView>()
    private val mClMeal by R.id.food_main_cl_meal.view<ConstraintLayout>()
    private var dialog: FoodDetailDialog? = null

    //是否改变标签，如果标签改变了换一换需要重新请求数据
    private var changeLabel = true

    var translation: ObjectAnimator? = null

    private val foodRegionRvAdapter = FoodMainRvAdapter() { state, position ->
        changeBtnState(state, position, mRvRegion)
        viewModel.hashMapRegion.replace(viewModel.dataRegion[position], state)
    }
    private val foodNumRvAdapter = FoodMainRvAdapter() { state, position ->
        //先将所有的hashMap键值对改为false，因为是单选
        viewModel.run {
            hashMapNumber.replaceAll { _, _ ->
                false
            }
            //将所有的button状态改为false
            for (i in 0 until dataNumber.size) changeBtnState(false, i, mRvNumber)
            changeBtnState(state, position, mRvNumber)
            hashMapNumber.replace(dataNumber[position], state)
        }
    }
    private val foodFeatureRvAdapter = FoodMainRvAdapter() { state, position ->
        changeBtnState(state, position, mRvFeature)
        viewModel.apply { hashMapFeature.replace(dataFeature[position], state) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.food_activity_food_main)
        initView()
        initObserver()
    }

    private fun initObserver() {
        viewModel.foodMainBean.observe {
            mImgPicture.setImageFromUrl(it.picture)
            initRecycleViewAdapter()
        }
        viewModel.determineSuccess.observe {
            mBtnDetail.visible()
            mBtnChange.visible()
            mBtnDetermine.gone()
        }
        viewModel.foodResultBean.observe {
            changeLabel = false
            if (viewModel.foodNum < it.size) {
                mTvMealNew.text = it[viewModel.foodNum].name
                changeResult()
            } else {
                FoodMainDialog.Builder(
                    this,
                    data = ChooseDialog.DataImpl(
                        width = 255,
                        content = "如果还没找到你喜欢的美食，可以尝试多选一些关键词哦！",
                        type = BaseChooseDialog.DialogType.ONE_BUT,
                        buttonWidth = 130,
                        buttonHeight = 37,
                    )
                ).setPositiveClick {
                    this.dismiss()
                }.show()
                IStoreService::class.impl()
                    .postTask(IStoreService.Task.JOIN_FOOD,"","今日已使用美食咨询处一次，获得10邮票")
            }
        }
        viewModel.foodRefreshBean.observe {
            foodFeatureRvAdapter.submitList(viewModel.dataFeature)
            viewModel.run {
                hashMapFeature.replaceAll { t, u ->
                    false
                }
                //将所有的button状态改为false
                for (i in dataFeature.indices) changeBtnState(false, i, mRvFeature)
            }
        }
        viewModel.resultChoose.observe {
            if (!it) {
                FoodMainDialog.Builder(
                    this,
                    data = ChooseDialog.DataImpl(
                        width = 255,
                        content = "请选择标签",
                        type = BaseChooseDialog.DialogType.ONE_BUT,
                        buttonWidth = 130,
                        buttonHeight = 37,
                    )
                ).setPositiveClick {
                    this.dismiss()
                }.show()
                viewModel.resultChoose.value = true
            }
        }
        viewModel.foodPraiseBean.observe {
            dialog?.findViewById<TextView>(R.id.food_dialog_tv_praise_num)?.text =
                it.praiseNum.toString()
            //dataFoodResult是本地数据，需要对本地数据进行处理
            viewModel.dataFoodResult[viewModel.foodNum].run {
                this.praiseNum = it.praiseNum
                this.praiseIs = it.praiseIs
            }
            if (it.praiseIs) {
                dialog?.findViewById<Button>(R.id.food_dialog_detail_btn_positive)
                    ?.apply {
                        background = AppCompatResources.getDrawable(
                            context,
                            R.drawable.food_shape_btn_praise
                        )
                        text = "已点赞"
                    }
            } else {
                dialog?.findViewById<Button>(R.id.food_dialog_detail_btn_positive)
                    ?.apply {
                        background = AppCompatResources.getDrawable(
                            context,
                            R.drawable.food_shape_btn_determine
                        )
                        text = "点赞"
                    }
            }
        }
    }

    private fun initView() {
        mImgBack.setOnClickListener {
            finish()
        }
        mBtnDetermine.setOnSingleClickListener {
            //此时是未第一次点击的时候，查看详情还未显示出来
            viewModel.postFoodResult()
        }
        mBtnDetail.setOnSingleClickListener {
            viewModel.dataFoodResult[viewModel.foodNum].apply {
                dialog = FoodDetailDialog.Builder(
                    this@FoodMainActivity,
                    data = FoodDetailDialog.Data(
                        content = introduce,
                        foodName = name,
                        imageUrl = picture,
                        praiseNum = praiseNum,
                        positiveButtonBackground = if (this.praiseIs) R.drawable.food_shape_btn_praise else R.drawable.food_shape_btn_determine,
                        praiseIs,
                        width = 255,
                        height = 330,
                    )
                )
                    .setNegativeClick {
                        this.dismiss()
                    }
                    .setPositiveClick {
                        viewModel.postFoodPraise(this@apply.name)

                    }
                    .build()
                dialog!!.show()
            }
        }
        mBtnChange.setOnClickListener {
            viewModel.apply {
                if (!changeLabel) {
                    if (foodNum < dataFoodResult.size - 1) {
                        foodNum++
                        mTvMealNew.text = viewModel.dataFoodResult[foodNum].name
                        changeResult()
                    } else {
                        FoodMainDialog.Builder(
                            this@FoodMainActivity,
                            data = ChooseDialog.DataImpl(
                                width = 255,
                                content = "如果还没找到你喜欢的美食，可以尝试多选一些关键词哦！",
                                type = BaseChooseDialog.DialogType.ONE_BUT,
                                buttonWidth = 130,
                                buttonHeight = 37,
                            )
                        ).setPositiveClick {
                            this.dismiss()
                        }.show()
                    }
                } else {
                    viewModel.foodNum = 0
                    postFoodResult()
                }
            }
        }
        mImgNotification.setOnSingleClickListener {
            FoodMainDialog.Builder(
                this,
                data = ChooseDialog.DataImpl(
                    width = 255,
                    content = "美食咨询处的设置，一" +
                            "是为了帮助各位选择综合症的邮子们更好的选择自己的需要的美食，对选择综合症说拜拜！二是为" +
                            "了各位初来学校的新生学子更好的体验学校各处的美食！按照要求通过标签进行选择，" +
                            "卷卷会帮助你选择最符合要求的美食哦！",
                    type = BaseChooseDialog.DialogType.ONE_BUT,
                    buttonWidth = 130,
                    buttonHeight = 37,
                )
            ).setPositiveClick {
                this.dismiss()
            }.show()
        }
        mImgRefresh.setOnClickListener {
            viewModel.postFoodRefresh()
        }
    }

    private fun initRecycleViewAdapter() {
        viewModel.apply {
            //可多选的情况，列表为就餐区域，餐饮特征
            mRvRegion.adapter = foodRegionRvAdapter
            foodRegionRvAdapter.submitList(dataRegion)
            mRvFeature.adapter = foodFeatureRvAdapter
            foodFeatureRvAdapter.submitList(dataFeature)
            //只可单选的情况，就餐人数
            mRvNumber.adapter = foodNumRvAdapter
            foodNumRvAdapter.submitList(dataNumber)
        }
    }

    /**
     * 改变button的状态，从选中到未选中或者从未选中到选中
     * 此时的state在rv回调的时候已经取了反，此时的state为需要改变的状态
     */
    private fun changeBtnState(
        state: Boolean,
        position: Int,
        rv: RecyclerView,
    ) {
        changeLabel = true
        rv.layoutManager?.findViewByPosition(position)?.let {
            it.findViewById<Button>(R.id.food_rv_item_btn)
                ?.apply {
                    this.isSelected = state
                    if (state) {
                        background =
                            AppCompatResources.getDrawable(
                                context,
                                R.drawable.food_ic_btn_pressed
                            )
                        setTextColor(getColor(R.color.food_main))
                    } else {
                        background =
                            AppCompatResources.getDrawable(
                                context,
                                R.drawable.food_shape_btn_normal
                            )
                        setTextColor(getColor(com.cyxbs.components.config.R.color.config_alpha_level_two_font_color))
                    }
                }
        }
    }

    private fun changeResult() {
        if (translation?.isRunning == true) {
            cancelAnimator()
        }
        viewTranslation(1000)
    }

    private fun viewTranslation(
        duration: Long
    ) {
        //当换一个按钮为可见时，说明菜品位置已经有菜品了，此时
        val translation = ObjectAnimator.ofFloat(
            mClMeal,
            "translationY",
            0f,
            mClMeal.height.toFloat() / 2 - 3.dp2px
        )
        translation.duration = duration
        translation.addListener {
            //动画完成后 新旧的view交换位置
            mTvMealOld.text = mTvMealNew.text
            mTvMealNew.text = ""
            val t2 = ObjectAnimator.ofFloat(
                mClMeal,
                "translationY",
                mClMeal.y,
                0f
            )
            t2.duration = 0
            t2.start()
        }
        this.translation = translation
        translation.start()
    }

    private fun cancelAnimator() {
        translation?.cancel()
    }

}