package com.cyxbs.pages.course.page.find.ui.find.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.pages.course.R
import com.cyxbs.pages.course.page.find.ui.course.stu.FindStuCourseFragment
import com.cyxbs.pages.course.page.find.ui.course.tea.FindTeaCourseFragment
import com.cyxbs.pages.course.page.find.ui.find.fragment.FindStuFragment
import com.cyxbs.pages.course.page.find.viewmodel.activity.FindLessonViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 老的查找他人课表入口（DISCOVER_OTHER_COURSE 路由）。
 * 已迁移至 Navigation3 ([com.cyxbs.pages.course.page.find.FindCourseNavEntry])，
 * 移除 `@KClassProvider` 后此 Activity 不再被任何路由命中，仅作为历史代码保留。
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/2/8 15:40
 */
class FindLessonActivity : BaseActivity() {
  
  companion object {
  
    private const val Intent_stuNum = "需要查找的学生学号"
    private const val Intent_stuName = "需要查找的学生名字"
    private const val Intent_teaNum = "需要查找的老师学号"
    private const val Intent_teaName = "需要查找的老师名字"
    
    // 下面几个方法可通过依赖 api_course 模块使用
  
    /**
     * 通过学号查找对应学生的课表，会直接打开课表界面
     */
    fun startByStuNum(context: Context, stuNum: String) {
      context.startActivity(
        Intent(context, FindLessonActivity::class.java)
          .putExtra(Intent_stuNum, stuNum)
      )
    }
  
    /**
     * 通过名字查找对应学生，会打开查找的列表
     */
    fun startByStuName(context: Context, stuName: String) {
      context.startActivity(
        Intent(context, FindLessonActivity::class.java)
          .putExtra(Intent_stuName, stuName)
      )
    }
  
    /**
     * 通过工号查找对应老师的课表，会直接打开课表界面
     */
    fun startByTeaNum(context: Context, teaNum: String) {
      context.startActivity(
        Intent(context, FindLessonActivity::class.java)
          .putExtra(Intent_teaNum, teaNum)
      )
    }
  
    /**
     * 通过名字查找对应老师，会打开查找的列表
     */
    fun startByTeaName(context: Context, teaName: String) {
      context.startActivity(
        Intent(context, FindLessonActivity::class.java)
          .putExtra(Intent_teaName, teaName)
      )
    }
  }
  
  private val mViewModel by viewModels<FindLessonViewModel>()

  private val mTabLayout by R.id.course_tl_find_course.view<TabLayout>()
  private val mViewPager by R.id.course_vp_find_course.view<ViewPager2>()
  private val mBottomSheetView by R.id.course_bsh_find_course.view<FrameLayout>()
  private val mBottomSheet by lazyUnlock {
    BottomSheetBehavior.from(mBottomSheetView)
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.course_activity_find_course)
    initView()
    initIntent()
    initObserve()
  }

  private fun initView() {
    mViewPager.adapter = FragmentVpAdapter(this)
      .add { FindStuFragment() }
//      .add { FindTeaFragment() } // 因为 jwzx 没有给查找老师课表的接口，所以取消老师课表

    TabLayoutMediator(mTabLayout, mViewPager) { tab, position ->
      when (position) {
        0 -> tab.text = "同学课表"
        1 -> tab.text = "老师课表"
      }
    }.attach()
  
    mBottomSheet.addBottomSheetCallback(
      object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
          when (newState) {
            BottomSheetBehavior.STATE_EXPANDED -> {
              mCollapsedBackPressedCallback.isEnabled = true
            }
            BottomSheetBehavior.STATE_COLLAPSED -> {
              mCollapsedBackPressedCallback.isEnabled = false
            }
            else -> {}
          }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
          if (slideOffset >= 0) {
            mBottomSheetView.alpha = slideOffset
          }
        }
      }
    )
    
    findViewById<View>(R.id.course_ib_find_course_back).setOnSingleClickListener { finish() }
  }
  
  private fun initIntent() {
    val stuNum = intent.getStringExtra(Intent_stuNum)
    if (stuNum != null) {
      mViewModel.findLesson(FindLessonViewModel.StuNum(stuNum))
    } else {
      val stuName = intent.getStringExtra(Intent_stuName)
      if (stuName != null) {
        mViewModel.findLesson(FindLessonViewModel.StuName(stuName))
      } else {
        val teaNum = intent.getStringExtra(Intent_teaNum)
        if (teaNum != null) {
          mViewPager.setCurrentItem(1, false)
          mViewModel.findLesson(FindLessonViewModel.TeaNum(teaNum))
        } else {
          val teaName = intent.getStringExtra(Intent_teaName)
          if (teaName != null) {
            mViewPager.setCurrentItem(1, false)
            mViewModel.findLesson(FindLessonViewModel.TeaName(teaName))
          }
        }
      }
    }
  }

  private fun initObserve() {
    mViewModel.courseState.observe {
      when (it?.second) {
        true -> {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
          FindStuCourseFragment.tryReplaceOrFresh(supportFragmentManager, mBottomSheetView.id, it.first)
        }
        false -> {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
          FindTeaCourseFragment.tryReplaceOrFresh(supportFragmentManager, mBottomSheetView.id, it.first)
        }
        null -> {
          mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
      }
    }
  }
  
  /**
   * 用于拦截返回键，在 BottomSheet 未折叠时先折叠
   */
  private val mCollapsedBackPressedCallback = onBackPressedDispatcher.addCallback(enabled = false) {
    mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
  }
}