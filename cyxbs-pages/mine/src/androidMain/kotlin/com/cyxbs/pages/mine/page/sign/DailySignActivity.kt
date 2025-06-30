package com.cyxbs.pages.mine.page.sign

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.network.model.ScoreStatus
import com.cyxbs.pages.mine.util.widget.ColorState
import com.cyxbs.pages.mine.util.widget.ImageState
import com.cyxbs.pages.mine.util.widget.SchoolCalendarPlus
import com.cyxbs.pages.mine.util.widget.Stick
import com.cyxbs.pages.mine.util.widget.WeekGenerator
import com.g985892345.provider.api.annotation.KClassProvider
import com.mredrock.cyxbs.common.ui.BaseViewModelActivity
import com.mredrock.cyxbs.common.utils.extensions.toast
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Created by zzzia on 2018/8/14.
 * origin by jay86
 * 每日签到
 */
@KClassProvider(clazz = Activity::class, name = MINE_CHECK_IN)
class DailySignActivity : BaseViewModelActivity<DailyViewModel>() {
    private val mine_daily_sign by R.id.mine_daily_sign.view<Button>()
    private val mine_daily_tv_year by R.id.mine_daily_tv_year.view<TextView>()
    private val mine_daily_tv_week by R.id.mine_daily_tv_week.view<TextView>()
    private val mine_daily_dayCount by R.id.mine_daily_dayCount.view<TextView>()
    private val mine_daily_tv_ranking_percentage by R.id.mine_daily_tv_ranking_percentage.view<TextView>()
    private val mine_daily_tv_ranking by R.id.mine_daily_tv_ranking.view<TextView>()
    private val mine_daily_tv_bubble by R.id.mine_daily_tv_bubble.view<TextView>()

    private var objectAnimator: ObjectAnimator? = null

    private val dividerResArr: Array<Stick> by lazy {
        arrayOf(findViewById(R.id.mine_daily_v_divider_mon_tue),
            findViewById(R.id.mine_daily_v_divider_tue_wed),
            findViewById(R.id.mine_daily_v_divider_wed_thurs),
            findViewById(R.id.mine_daily_v_divider_thurs_fri),
            findViewById(R.id.mine_daily_v_divider_fri_sat),
            findViewById(R.id.mine_daily_v_divider_sat_sun))
    }
    private val imageViewResArr: Array<ImageView> by lazy {
        arrayOf(findViewById(R.id.mine_daily_iv_mon),
            findViewById(R.id.mine_daily_iv_tue),
            findViewById(R.id.mine_daily_iv_wed),
            findViewById(R.id.mine_daily_iv_thurs),
            findViewById(R.id.mine_daily_iv_fri),
            findViewById(R.id.mine_daily_iv_sat),
            findViewById(R.id.mine_daily_iv_sun))
    }
    private val spaceResArr: Array<Space> by lazy {
        arrayOf(findViewById(R.id.mine_daily_space_mon),
            findViewById(R.id.mine_daily_space_tue),
            findViewById(R.id.mine_daily_space_wed),
            findViewById(R.id.mine_daily_space_thurs),
            findViewById(R.id.mine_daily_space_fri),
            findViewById(R.id.mine_daily_space_sat),
            findViewById(R.id.mine_daily_space_sun))
    }
    private val weekGenerator: WeekGenerator by lazy {
        WeekGenerator()
    }

    //通过一个标志位，来判断刷新divider的时候是否需要动画，
    //当为true时，表明用户点击了签到按钮导致的UI刷新，这时候需要动画，
    //当为false时，表明为正常的进入Activity刷新
    private var isChecking: Boolean = false

    private var enterTime: Long = 0

    override fun onResume() {
        super.onResume()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_QD_HOMEPAGE)
        }
    }

    override fun onStop() {
        super.onStop()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_QD,duration,enterTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTransparent(window)
        setContentView(R.layout.mine_activity_daily_sign)
        common_toolbar.initWithSplitLine("", false)
        initView()
        initData()
    }

    private fun setTransparent(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    /**
     * 需要处理属性动画内存泄露的问题
     */
    override fun onDestroy() {
        objectAnimator?.let {
            if (it.isRunning) {
                it.cancel()
            }
        }
        super.onDestroy()
    }

    //ViewModel观察和网络请求
    private fun initData() {
        viewModel.loadAllData()
        viewModel.status.observe(this, Observer {
            refreshUI(it)
        })
        viewModel.isInVacation.observe(this, Observer {
            if (it == true) {
                toast("寒暑假不可签到")
            }
        })
    }

    private fun initView() {
        mine_daily_sign.setOnClickListener { checkIn() }
    }

    /**
     * 刷新签到页面
     */
    @SuppressLint("SetTextI18n")
    private fun refreshUI(scoreStatus: ScoreStatus) {
        //后端的规则是低位字节代表星期一，高位字节代表星期日，这里做一个翻转处理
        val weekState = scoreStatus.weekInfo.map(Character::getNumericValue).reversed()
        weekGenerator.weekSignStateArr = weekState
        val schoolCalendarPlus = SchoolCalendarPlus()
        val pair = schoolCalendarPlus.getYearPair()
        mine_daily_tv_year.text = "${pair.first}-${pair.second}"
        val isFirstSemester = if (schoolCalendarPlus.isFirstSemester()) "秋" else "春"
        val dayOfTerm = schoolCalendarPlus.dayOfTerm
        if (dayOfTerm < 0) {
            mine_daily_tv_week.text = "距离${isFirstSemester}学期开学还有${abs(dayOfTerm)}天"
        } else {
            mine_daily_tv_week.text = "${isFirstSemester}学期第${schoolCalendarPlus.getChineseWeek()}周"
        }
        mine_daily_dayCount.text = "已连续打卡${scoreStatus.serialDays}天"
        mine_daily_tv_ranking_percentage.text = "超过${scoreStatus.percent}的邮子"
        if (scoreStatus.canCheckIn && scoreStatus.isChecked) {
            mine_daily_tv_ranking.text = "今日第${scoreStatus.rank}位打卡"
        } else if (scoreStatus.canCheckIn && !scoreStatus.isChecked) {
            mine_daily_tv_ranking.text = "还没有打卡哦"
        } else {
            mine_daily_tv_ranking.text = "寒暑假不可签到呢(●'ᴗ'σ)σணღ*"
        }
        if (scoreStatus.isChecked or !scoreStatus.canCheckIn) {
            mine_daily_sign.apply {
                isClickable = false
                background = ResourcesCompat.getDrawable(resources, R.drawable.mine_bg_round_corner_grey, null)
                setTextColor(ContextCompat.getColor(context, com.mredrock.cyxbs.common.R.color.common_grey_button_text))
                text = if (scoreStatus.canCheckIn) "已签到" else "签到"
            }
        } else {
            mine_daily_sign.apply {
                isClickable = true
                background = ResourcesCompat.getDrawable(resources, com.mredrock.cyxbs.common.R.drawable.common_dialog_btn_positive_blue, null)
                setTextColor(ContextCompat.getColor(context, com.mredrock.cyxbs.common.R.color.common_white_font_color))
                text = "签到"
            }
        }
        changeWeekImage()
        paintDivider(weekGenerator.getDividerColorArr())
        val today = weekGenerator.getToday()
        today.let {
            val centerX = spaceResArr[it].x
            mine_daily_tv_bubble.x = centerX - mine_daily_tv_bubble.width / 2
            mine_daily_tv_bubble.text = "${weekGenerator.getTodayScore()}积分"
        }
    }

    private fun checkIn() {
        isChecking = true
        viewModel.checkIn()
    }

    /**
     * 设置签到的七个圆点的Image
     * 圆点有三种状态：蓝色，钻石，灰色
     */
    private fun changeWeekImage() {
        val state = weekGenerator.getWeekImageStateArr()
        for (i in 0..6) {
            when {
                state[i] == ImageState.IMAGE_BLUE -> {
                    imageViewResArr[i].setImageResource(R.drawable.mine_shape_circle_src_activity_sign)
                }
                state[i] == ImageState.IMAGE_DIAMOND -> {
                    imageViewResArr[i].setImageResource(R.drawable.mine_ic_sign_diamond)
                }
                else -> {
                    imageViewResArr[i].setImageResource(R.drawable.mine_shape_circle_src_activity_sign_grey)
                }
            }
        }
    }


    //修改divider颜色,如果isChecking == true 那么说明是用户点击了签到导致的UI刷新，此时相应的divider需要有一段动画
    private fun paintDivider(dividerColorArr: Array<ColorState>) {
        weekGenerator.let {
            for (i in 0..5) {
                if (i == it.getToday() && isChecking && dividerColorArr[i] == ColorState.COLOR_BLUE) {
                    isChecking = false
                    val index = it.getToday()
                    if (index > 5) return
                    setDividerColor(index, ColorState.COLOR_BLUE)
                    val animator = ObjectAnimator.ofFloat(dividerResArr[index], "progress", 0f, 1f)
                    //先让objectAnimator保存animator的引用，方便退出Activity时cancel掉阻止内存泄漏
                    objectAnimator = animator

                    animator.duration = 1000
                    animator.interpolator = AccelerateDecelerateInterpolator()
                    animator.start()
                } else {
                    setDividerColor(i, dividerColorArr[i])
                    dividerResArr[i].progress = 1f
                }

            }
        }

    }

    private fun setDividerColor(i: Int, color: ColorState) {
        when (color) {
            ColorState.COLOR_GREY -> {
                dividerResArr[i].color.color = ContextCompat.getColor(this, com.mredrock.cyxbs.common.R.color.common_mine_sign_divider_grey)
            }
            ColorState.COLOR_BLUE -> {
                dividerResArr[i].color.color = ContextCompat.getColor(this, com.mredrock.cyxbs.common.R.color.common_mine_sign_divider_blue)
            }
            else -> {
                dividerResArr[i].color.color = ContextCompat.getColor(this, com.mredrock.cyxbs.common.R.color.common_mine_sign_divider_blue_light)
            }
        }
    }
}
