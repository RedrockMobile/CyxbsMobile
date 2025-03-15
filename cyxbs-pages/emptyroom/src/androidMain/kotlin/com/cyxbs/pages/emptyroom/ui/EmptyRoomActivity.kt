package com.cyxbs.pages.emptyroom.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.route.DISCOVER_EMPTY_ROOM
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.emptyroom.R
import com.cyxbs.pages.emptyroom.ui.adapter.EmptyRoomResultAdapter
import com.cyxbs.pages.emptyroom.ui.adapter.StringAdapter
import com.cyxbs.pages.emptyroom.ui.widget.MultiSelector
import com.cyxbs.pages.emptyroom.ui.widget.OnItemSelectedChangeListener
import com.cyxbs.pages.emptyroom.utils.ViewInitializer
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomViewModel
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomViewModel.Companion.DEFAULT
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomViewModel.Companion.ERROR
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomViewModel.Companion.FINISH
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomViewModel.Companion.LOADING
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.tabs.TabLayout
import java.util.Calendar


@KClassProvider(clazz = Activity::class, name = DISCOVER_EMPTY_ROOM)
class EmptyRoomActivity : BaseActivity(), OnItemSelectedChangeListener {

    private val viewModel by viewModels<EmptyRoomViewModel>()

    private val weekdayApi = intArrayOf(1, 2, 3, 4, 5, 6, 7)
    private val buildingApi = intArrayOf(2, 3, 4, 5, 8)
    private val sectionApi = intArrayOf(0, 1, 2, 3, 4, 5)
    private var buildingPosition = -1
    private lateinit var weekApi: IntArray

    private var resultAdapter: EmptyRoomResultAdapter? = null
    private lateinit var queryAnimator: ObjectAnimator

    private val mTlBuilding by R.id.tl_building.view<TabLayout>()
    private val mMultiSelectorSection by R.id.multi_selector_section.view<MultiSelector>()
    private val mMultiSelectorWeek by R.id.multi_selector_week.view<MultiSelector>()
    private val mMultiSelectorWeekday by R.id.multi_selector_weekday.view<MultiSelector>()
    private val mIvQuerying by R.id.iv_querying.view<ImageView>()
    private val mRvResult by R.id.rv_result.view<RecyclerView>()
    private val mIbEmptyroomBack by R.id.ib_emptyroom_back.view<ImageButton>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.emptyroom_activity_empty_room)
        init()
    }

    private fun init() {
        initObserver()
        initData()
        initSelectors()
        initQueryingAnimator()
        initRv()
        initTab()
        mIbEmptyroomBack.setOnSingleClickListener {
            finish()
        }
    }

    private fun initTab() {
        mTlBuilding.apply {
            addTab(mTlBuilding.newTab().setText("二教"), false)
            addTab(mTlBuilding.newTab().setText("三教"), false)
            addTab(mTlBuilding.newTab().setText("四教"), false)
            addTab(mTlBuilding.newTab().setText("五教"), false)
            addTab(mTlBuilding.newTab().setText("八教"), false)
        }
        mTlBuilding.addOnTabSelectedListener(object : TabLayout.BaseOnTabSelectedListener<TabLayout.Tab> {
            override fun onTabReselected(p0: TabLayout.Tab?) {

            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
                if (p0 != null) {
                    p0.customView = null
                }
            }

            override fun onTabSelected(p0: TabLayout.Tab?) {
                if (p0 != null) {
                    buildingPosition = p0.position
                    val textView = TextView(applicationContext)
                    textView.paint.isFakeBoldText = true

                    val drawable: Drawable? = ContextCompat.getDrawable(this@EmptyRoomActivity, R.drawable.emptyroom_shape_query_item)
                    textView.background = drawable
                    textView.text = p0.text
                    textView.setTextColor(Color.parseColor("#112C54"))
                    textView.gravity = Gravity.CENTER
//                    textView.setPadding(dip(15),dip(3),dip(15),dip(3))
//                    textView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT)
                    textView.height = 26.dp2px
                    textView.width = 59.dp2px
                    p0.customView = textView
                    onItemSelectedChange()
                }
            }

        })

    }


    private fun initObserver() {
        viewModel.rooms.observe(this, Observer {
            it ?: return@Observer
            mIvQuerying.gone()
            mRvResult.visible()
            queryAnimator.cancel()
            if (resultAdapter == null) {
                resultAdapter = EmptyRoomResultAdapter(it.toMutableList(), this@EmptyRoomActivity)
                mRvResult.adapter = resultAdapter
            } else {
                resultAdapter?.updateData(it)
            }
        })
        viewModel.status.observe(this, Observer {
            it ?: return@Observer
            when (it) {
                DEFAULT -> {
                    mIvQuerying.gone()
                    mRvResult.gone()
                }
                LOADING -> {
                    mIvQuerying.visible()
                    queryAnimator.start()
                    mRvResult.gone()
                }
                FINISH -> {
                    mIvQuerying.gone()
                    mRvResult.visible()
                    queryAnimator.cancel()
                }
                ERROR -> {
                    mIvQuerying.gone()
                    mRvResult.gone()
                    "抱歉，数据获取失败".toast()
                }
            }
        })
    }

    private fun initData() {
//      在未登录情况时，无法获取正确的当前周值，默认指向第一周
        var week = SchoolCalendar.getWeekOfTerm() ?: 0
        val temp = mMultiSelectorWeek.getDisplayValues<String>()
        val list = ArrayList(temp)
        //删除"整学期"
        list.removeAt(0)
        //修正week值
        week = if (week > list.size || week < 0) 0 else week - 1
        repeat(week) { list.removeAt(0) }
        mMultiSelectorWeek.setDisplayValues(list)
        weekApi = IntArray(list.size)
        repeat(list.size) { weekApi[it] = ++week }
    }


    private fun initSelectors() {
        initSelector(mMultiSelectorWeek, weekApi, 0, 0)
        initSelector(mMultiSelectorWeekday, weekdayApi, (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7, 1)
        initSelector(mMultiSelectorSection, sectionApi, -1, 3, middle = 1.dp2px)
        mMultiSelectorSection.setMinSelectedNum(1)
    }

    private fun initSelector(selector: MultiSelector, values: IntArray, defaultSelected: Int, tag: Int, isFullUp: Boolean = false, itemNumber: Int = 0, canScroll: Boolean = true, @Px head: Int = 0, @Px middle: Int = 0, @Px tail: Int = 0) {
        val initializer = ViewInitializer.Builder(this)
                .horizontalLinearLayoutManager(canScroll)
                .gap(head.dp2px, middle.dp2px, tail.dp2px)
                .stringAdapter(selector, object : StringAdapter.LayoutWrapper() {
                    override val layoutId: Int
                        get() = R.layout.emptyroom_recycle_item_query_option

                    override val textViewId: Int
                        get() = R.id.tv_text

                    override fun onBindView(textView: TextView, displayValue: String, selected: Boolean, position: Int) {
                        super.onBindView(textView, displayValue, selected, position)
                        val drawable = if (selected) ContextCompat.getDrawable(this@EmptyRoomActivity, R.drawable.emptyroom_shape_query_item) else null
//                        var color =  1
                        if (selected) {
                            textView.setTextColor(ContextCompat.getColor(this@EmptyRoomActivity, R.color.emptyroom_selected))
                            textView.paint.isFakeBoldText = true
                        } else {
                            textView.setTextColor(ContextCompat.getColor(this@EmptyRoomActivity, com.cyxbs.components.config.R.color.config_level_two_font_color))
                            textView.paint.isFakeBoldText = false
                        }
                        textView.gravity = Gravity.CENTER
                        textView.background = drawable
                        textView.height = 26.dp2px
                    }
                }, isFullUp, itemNumber).build()
        selector.apply {
            setValues(values)
            if (defaultSelected >= 0) setSelected(defaultSelected, true)
            setViewInitializer(initializer)
            setOnItemSelectedChangeListener(this@EmptyRoomActivity)
            setTag(tag)
        }
    }


    private fun initQueryingAnimator() {
        queryAnimator = ObjectAnimator.ofFloat(mIvQuerying, "rotation", 0f, 360f)
                .apply {
                    duration = 500
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
    }

    private fun initRv() {
        mRvResult.layoutManager = LinearLayoutManager(this@EmptyRoomActivity)
                .apply { orientation = LinearLayoutManager.VERTICAL }
    }

    private fun query() {
        val week = mMultiSelectorWeek.getSelectedValues()[0]
        val weekday = mMultiSelectorWeekday.getSelectedValues()[0]
        val building = buildingApi[buildingPosition]
        val section = mMultiSelectorSection.getSelectedValues()
        val res = section.map { it + 1 }
        viewModel.getData(week, weekday, building, res)
    }


    override fun onDestroy() {
        if (queryAnimator.isRunning) {
            queryAnimator.cancel()
        }
        super.onDestroy()
    }


    override fun onItemClickListener() = Unit

    override fun onItemSelectedChange() {
        if (mMultiSelectorSection.selectedSize() == 0) {
            viewModel.status.value = DEFAULT
        }
        if (buildingPosition != -1 && mMultiSelectorSection.selectedSize() > 0) {
            query()
        } else if (resultAdapter != null) {
            resultAdapter?.apply {
                data.clear()
                notifyDataSetChanged()
            }
        }
    }
}
