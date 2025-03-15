package com.cyxbs.pages.affair.ui.fragment

import android.Manifest
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.cyxbs.pages.affair.R
import com.cyxbs.pages.affair.bean.RemindMode
import com.cyxbs.pages.affair.bean.Todo
import com.cyxbs.pages.affair.ui.adapter.AffairDurationAdapter
import com.cyxbs.pages.affair.ui.adapter.TitleCandidateAdapter
import com.cyxbs.pages.affair.ui.adapter.data.AffairTimeData
import com.cyxbs.pages.affair.ui.adapter.data.AffairWeekData
import com.cyxbs.pages.affair.ui.adapter.data.toAtWhatTime
import com.cyxbs.pages.affair.ui.fragment.utils.AffairPageManager
import com.cyxbs.pages.affair.ui.viewmodel.activity.AffairViewModel
import com.cyxbs.pages.affair.ui.viewmodel.fragment.AddAffairViewModel
import com.cyxbs.pages.affair.ui.dialog.RemindSelectDialog
import com.cyxbs.pages.course.api.utils.getStartRow
import com.cyxbs.pages.course.api.utils.getStartTimeMinute
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.*
import java.util.Calendar

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/6/3 15:12
 */
class AddAffairFragment : BaseFragment(R.layout.affair_fragment_add_affair) {

    companion object {
        fun newInstance(
            week: Int,
            day: Int,
            beginLesson: Int,
            period: Int
        ): AddAffairFragment {
            return AddAffairFragment().apply {
                arguments = bundleOf(
                    this::mAffairWeek.name to week,
                    this::mAffairDay.name to day,
                    this::mAffairBeginLesson.name to beginLesson,
                    this::mAffairPeriod.name to period
                )
            }
        }
    }

    private val mAffairWeek by arguments<Int>()
    private val mAffairDay by arguments<Int>()
    private val mAffairBeginLesson by arguments<Int>()
    private val mAffairPeriod by arguments<Int>()

    private val mViewModel by viewModels<AddAffairViewModel>()

    private val mActivityViewModel by activityViewModels<AffairViewModel>()

    private val mEditText by R.id.affair_et_add_affair.view<EditText>()
    private val mTvRemind by R.id.affair_tv_add_affair_remind.view<TextView>()
    private val mTvAddTodo by R.id.affair_tv_add_affair_addTodo.view<TextView>()

    private val mRvTitleCandidate by R.id.affair_rv_add_affair_title_candidate.view<RecyclerView>()
    private val mRvTitleCandidateAdapter = TitleCandidateAdapter()

    private val mRvDuration by R.id.affair_rv_add_affair_duration.view<RecyclerView>()
    private val mRvDurationAdapter = AffairDurationAdapter()

    private var mRemindMinute = 0 // 提醒时间的分钟数，用于临时保存后进行网络请求

    private val mPageManager = AffairPageManager(this)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRv()
        initListener()
        initObserve()
    }

    private fun initRv() {
        mRvTitleCandidate.adapter = mRvTitleCandidateAdapter
            .setClickListener {
                val index = mEditText.selectionStart // 得到光标位置
                val text = mEditText.text
                text.insert(index, it)
            }
        mRvTitleCandidate.layoutManager =
            FlexboxLayoutManager(requireContext(), FlexDirection.ROW, FlexWrap.WRAP)

        mRvDuration.adapter = mRvDurationAdapter
        mRvDuration.layoutManager =
            FlexboxLayoutManager(requireContext(), FlexDirection.ROW, FlexWrap.WRAP)

        mRvDurationAdapter.submitList(
            listOf(
                AffairWeekData(mAffairWeek),
                AffairTimeData(mAffairDay, mAffairBeginLesson, mAffairPeriod)
            )
        )
    }

    private fun initListener() {
        mEditText.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(p0: TextView?, p1: Int, p2: KeyEvent?): Boolean {
                return if (p1 == EditorInfo.IME_ACTION_NEXT) {
                    mActivityViewModel.clickNextBtn()
                    return true
                } else false
            }
        })
        mTvRemind.setOnSingleClickListener {
            val dialog = RemindSelectDialog(requireActivity()) { text, minute ->
                // 获取添加进手机日历的权限
                doPermissionAction(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                ) {
                    reason = "设置提醒需要访问您的日历哦~"
                    doAfterGranted {
                        mTvRemind.text = text
                        mRemindMinute = minute
                    }
                    doAfterRefused {
                        "申请权限被拒绝".toast()
                    }
                }
            }
            dialog.show()
        }
        mTvAddTodo.setOnSingleClickListener {
            if (mTvAddTodo.text == "加入待办") {
                mTvAddTodo.text = "取消待办"
            } else {
                mTvAddTodo.text = "加入待办"
            }
        }
    }

    private fun initObserve() {
        mViewModel.titleCandidates.observe {
            mRvTitleCandidateAdapter.submitList(it)
        }

        mActivityViewModel.clickAffect.collectLaunch {
            if (mPageManager.isEndPage()) {
                mViewModel.addAffair(
                    mRemindMinute,
                    mPageManager.getTitle(),
                    mPageManager.getContent(),
                    mRvDurationAdapter.currentList.toAtWhatTime(),
                )
                if (mTvAddTodo.text == "取消待办") {
                    mViewModel.addTodo(translationToTodo())
                }
                requireActivity().finish()
            } else {
                mPageManager.loadNextPage()
            }
        }
    }

    private fun translationToTodo() = Todo(
        System.currentTimeMillis() / 1000,
        mPageManager.getTitle(),
        mPageManager.getContent(),
        0,
        RemindMode(0, arrayListOf(), arrayListOf(), arrayListOf(), translationToCalendar()),
        System.currentTimeMillis(),
        "other",
        0,
        translationToCalendar()
    )

    // 将时间转换为日历
    private fun translationToCalendar(): String {
        val firstMonDay = SchoolCalendar.getFirstMonDayTimestamp() ?: return ""
        val whatTime = mRvDurationAdapter.currentList.toAtWhatTime()
        val startMinute = getStartTimeMinute(getStartRow(whatTime[0].beginLesson))
        val time = whatTime[0].week.map {
            (Calendar.getInstance()).apply {
                timeInMillis = firstMonDay
                add(Calendar.DATE, whatTime[0].day + (it - 1) * 7)
                add(Calendar.MINUTE, startMinute)
            }
        }
        val year = time[0].get(Calendar.YEAR)
        val month = time[0].get(Calendar.MONTH).plus(1)
        val day = time[0].get(Calendar.DAY_OF_MONTH)
        val hour = time[0].get(Calendar.HOUR_OF_DAY)
        val minute = time[0].get(Calendar.MINUTE)
        return "${year}年${month}月${day}日${hour}:${minute}"
    }
}