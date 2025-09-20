package com.cyxbs.pages.ufield.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.ui.viewModelBy
import com.cyxbs.components.config.route.DISCOVER_MAP
import com.cyxbs.components.config.route.UFIELD_DETAIL_ENTRY
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setImageFromUrl
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.store.api.IStoreService
import com.cyxbs.pages.ufield.R
import com.cyxbs.pages.ufield.bean.RemindMode
import com.cyxbs.pages.ufield.bean.Todo
import com.cyxbs.pages.ufield.viewmodel.DetailViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.properties.Delegates

@KClassProvider(clazz = Activity::class, name = UFIELD_DETAIL_ENTRY)
class DetailActivity : BaseActivity() {
    private var id by Delegates.notNull<Int>()
    private val tvSee by R.id.ufield_tv_wantsee.view<TextView>()
    private val tvTitle by R.id.ufield_tv_title.view<TextView>()
    private val tvType by R.id.ufield_tv_type.view<TextView>()
    private val tvSum by R.id.ufield_tv_sum_wantsee.view<TextView>()
    private val tvSponsor by R.id.ufield_tv_sponsor.view<TextView>()
    private val tvCreator by R.id.ufield_tv_creator.view<TextView>()
    private val tvWay by R.id.ufield_tv_way.view<TextView>()
    private val tvPlace by R.id.ufield_tv_place.view<TextView>()
    private val tvDetail by R.id.ufield_tv_detail.view<TextView>()
    private val tvTime by R.id.ufield_tv_time.view<TextView>()
    private val ivCover by R.id.detail_iv_cover.view<ImageView>()
    private val tvStart by R.id.ufield_tv_starttime.view<TextView>()
    private val tvEnd by R.id.ufield_tv_endtime.view<TextView>()
    private val layoutMap by R.id.ufield_layout_location.view<ConstraintLayout>()
    private val ivGoing by R.id.ufield_iv_going.view<ImageView>()
    private val tvGoing by R.id.ufield_tv_going.view<TextView>()
    private val ivAdd by R.id.ufield_iv_add.view<ImageView>()
    private val tvDays by R.id.ufield_tv_days.view<TextView>()
    private val tvDay by R.id.ufield_tv_day.view<TextView>()
    private val tvHours by R.id.ufield_tv_hours.view<TextView>()
    private val tvHour by R.id.ufield_tv_hour.view<TextView>()
    private val tvMinutes by R.id.ufield_tv_minutes.view<TextView>()
    private val tvMinute by R.id.ufield_tv_minute.view<TextView>()
    private val tvSeconds by R.id.ufield_tv_seconds.view<TextView>()
    private val tvSecond by R.id.ufield_tv_second.view<TextView>()
    private val tvTimeHead by R.id.ufield_tv_timehead.view<TextView>()
    private val ivBack by R.id.ufield_iv_back.view<ImageView>()
    private val tvAddTodo by R.id.ufield_tv_addtodo.view<TextView>()
    private lateinit var layout: ConstraintLayout
    private var countDownTimer: CountDownTimer = object : CountDownTimer(1, 1000) {
        override fun onTick(p0: Long) {
        }

        override fun onFinish() {
        }
    }
    private val viewModel by viewModelBy {
        id = intent.getIntExtra("actID", 1)
        DetailViewModel(id)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        layout = findViewById(R.id.ufield_layout_wantsee)
        initView()
        viewModel.wantToSee.observe(this) {
            if (it) {
                //在将文本替换为“已想看”后，修改文本位置，使其位于中心
                tvSee.apply {
                    text = "已想看"
                    setTextColor(
                        ContextCompat.getColor(
                            this@DetailActivity,
                            R.color.uField_text_see
                        )
                    )
                    tvSee.layoutParams = getConstrainLayoutParams()
                    ivAdd.gone()
                    //上传活动进度，获取邮票
                    IStoreService::class.impl().postTask(
                        IStoreService.Task.JOIN_UFIELD,
                        "",
                        "已参加活动一次，获得50邮票"
                    )
                }
                layout.apply {
                    setBackgroundResource(R.drawable.ufield_shape_haveseen)
                    setOnClickListener(null)
                }
            } else {
                toast("想看失败")
            }
        }
        viewModel.isAdd.observe(this) {
            tvAddTodo.setTextColor(
                ContextCompat.getColor(
                    this@DetailActivity,
                    R.color.uField_text_haveadd
                )
            )
            tvAddTodo.setBackgroundResource(R.drawable.ufield_shape_haveadd)
        }
    }

    private fun getConstrainLayoutParams(): ConstraintLayout.LayoutParams {
        val layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.startToStart = ConstraintSet.PARENT_ID
        layoutParams.endToEnd = ConstraintSet.PARENT_ID
        layoutParams.topToTop = ConstraintSet.PARENT_ID
        layoutParams.bottomToBottom = ConstraintSet.PARENT_ID
        layoutParams.horizontalBias = 0.5f
        layoutParams.verticalBias = 0.5f
        return layoutParams
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        layoutMap.setOnClickListener {
            startActivity(DISCOVER_MAP)
        }

        ivBack.setOnClickListener {
            finishAfterTransition()
        }
        viewModel.detailData.observe(this) {
            tvTitle.text = it.activityTitle
            when (it.activityType) {
                "culture" -> tvType.text = "文娱活动"
                "sports" -> tvType.text = "体育活动"
                "education" -> tvType.text = "教育活动"
            }
            tvSum.text = "${it.activityWatchNumber}人想看"
            tvSponsor.text = it.activityOrganizer
            tvCreator.text = it.activityCreator
            tvWay.text = it.activityRegistrationType
            tvPlace.text = it.activityPlace
            tvDetail.text = it.activityDetail
            ivCover.setImageFromUrl(it.activityCoverUrl)
            tvStart.text = trans(it.activityStartAt)
            tvEnd.text = trans(it.activityEndAt)
            if (it.activityState == "reviewing") {
                setStatusText("活动未审核")
            } else if (it.activityState == "rejected") {
                setStatusText("活动未通过")
            } else {
                if (it.ended) {
                    tvGoing.text = "已结束"
                    ivGoing.setImageResource(R.drawable.ufield_ic_finished)
                    tvTime.text = "活动已结束"
                    tvDays.gone()
                    tvHours.gone()
                    tvMinutes.gone()
                    tvSeconds.gone()
                    layout.gone()
                    tvAddTodo.gone()
                } else {
                    startDownTimer(it.activityStartAt)
                }
            }
            if (it.wantToWatch) {
                //在将文本替换为“已想看”后，修改文本位置，使其位于中心
                tvSee.text = "已想看"
                tvSee.setTextColor(ContextCompat.getColor(this, R.color.uField_text_see))
                tvSee.layoutParams = getConstrainLayoutParams()
                layout.setBackgroundResource(R.drawable.ufield_shape_haveseen)
                ivAdd.gone()
            } else {
                layout.setOnClickListener {
                    viewModel.wantToSee(id)
                }
            }
            if (it.isAdd) {
                tvAddTodo.setTextColor(
                    ContextCompat.getColor(
                        this@DetailActivity,
                        R.color.uField_text_haveadd
                    )
                )
                tvAddTodo.setBackgroundResource(R.drawable.ufield_shape_haveadd)
            } else {
                tvAddTodo.setOnClickListener {
                    tvAddTodo.setTextColor(
                        ContextCompat.getColor(
                            this@DetailActivity,
                            R.color.uField_text_haveadd
                        )
                    )
                    tvAddTodo.setBackgroundResource(R.drawable.ufield_shape_haveadd)
                    viewModel.addTodo(createTodo())
                    viewModel.isAdd(id)
                }
            }
        }
    }

    private fun startDownTimer(startTime: Long) {
        // 活动开始时间戳和结束时间戳（以秒为单位）

        val currentTimeInSeconds = System.currentTimeMillis() / 1000

        // 判断活动状态
        if (currentTimeInSeconds < startTime) {
            // 距离开始的剩余时间
            val remainingTimeInSeconds = startTime - currentTimeInSeconds

            // 创建并启动倒计时
            countDownTimer =
                object : CountDownTimer(remainingTimeInSeconds * 1000, 1000) {
                    @SuppressLint("UseCompatLoadingForDrawables")
                    override fun onTick(millisUntilFinished: Long) {
                        val days =
                            floor(millisUntilFinished / (1000 * 60 * 60 * 24.0))
                                .toLong()
                        val hours =
                            floor((millisUntilFinished % (1000 * 60 * 60 * 24.0)) / (1000 * 60 * 60.0))
                                .toLong()
                        val minutes =
                            floor((millisUntilFinished % (1000 * 60 * 60.0)) / (1000 * 60.0))
                                .toLong()
                        val seconds =
                            floor((millisUntilFinished % (1000 * 60.0)) / 1000)
                                .toLong()
                        tvTimeHead.text = "距离开始还有"
                        tvDay.text = "天"
                        tvHour.text = "小时"
                        tvMinute.text = "分"
                        tvSecond.text = "秒"
                        tvDays.text = "$days"
                        tvHours.text = "$hours"
                        tvMinutes.text = "$minutes"
                        tvSeconds.text = "$seconds"
                    }

                    override fun onFinish() {
                        // 倒计时结束处理逻辑
                        tvTime.text = "活动开始"
                        tvDays.gone()
                        tvHours.gone()
                        tvMinutes.gone()
                        tvSeconds.gone()
                    }
                }

            countDownTimer.start()
        } else {
            tvTime.text = "活动进行中"
            tvDays.gone()
            tvHours.gone()
            tvMinutes.gone()
            tvSeconds.gone()
        }
    }

    private fun setStatusText(text: String) {
        layout.gone()
        tvGoing.gone()
        ivGoing.gone()
        tvTime.text = text
        tvDays.gone()
        tvHours.gone()
        tvMinutes.gone()
        tvSeconds.gone()
        layout.gone()
    }

    private fun createTodo(): Todo {
        val notifyDatetime = viewModel.detailData.value?.let { otherTrans(it.activityStartAt) }?:""
        val todo = Todo(
            System.currentTimeMillis() / 1000,
            tvTitle.text.toString(),
            tvPlace.text.toString(),
            0,
            RemindMode(0, arrayListOf(), arrayListOf(), arrayListOf(), notifyDatetime),
            System.currentTimeMillis(),
            "other",
            0,
            notifyDatetime
        )
        return todo
    }

    private fun trans(timestampInSeconds: Long): String {
        val date = Date(timestampInSeconds * 1000L)

        val format = SimpleDateFormat("yyyy年MM月dd日HH点mm分", Locale.getDefault())
        return format.format(date)
    }

    private fun otherTrans(timestampInSeconds: Long): String {
        val date = Date(timestampInSeconds * 1000L)
        val format = SimpleDateFormat("yyyy年MM月dd日HH:mm", Locale.getDefault())
        return format.format(date)
    }
}

