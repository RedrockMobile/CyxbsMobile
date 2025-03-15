package com.cyxbs.pages.home.ui.course

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.crash.CrashDialog
import com.cyxbs.components.base.utils.Umeng
import com.cyxbs.components.base.utils.safeSubscribeBy
import com.cyxbs.components.config.route.COURSE_POS_TO_MAP
import com.cyxbs.components.config.route.DISCOVER_MAP
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.invisible
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.home.R
import com.cyxbs.pages.home.ui.course.utils.CourseHeaderHelper
import com.cyxbs.pages.home.ui.main.MainActivity
import com.cyxbs.pages.home.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.max

/**
 * 主页课表
 *
 * 最开始是使用的 Fragment 实现，后续在嵌入到 Compose 中时发现课表头加载会闪一下，
 * 所以就改成自定义 View 来实现减少耗时，但在自定义 View 里面这样写逻辑不是很推荐
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/14 18:56
 */
class HomeCourseLayout(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

  private val mActivity = context as MainActivity

  private val mActivityViewModel by mActivity.viewModels<MainViewModel>()

  private val mCourseService = ICourseService::class.impl()
  private val mAccountService = IAccountService::class.impl()

  init {
    addView(
      LayoutInflater.from(context)
        .inflate(R.layout.home_fragment_course, this, false)
    )
  }

  private val mFcvCourse: FragmentContainerView = findViewById(R.id.main_fcv_course)
  private val mViewHeader: View = findViewById(R.id.main_view_course_header)

  private val mTvHeaderState: TextView = findViewById(R.id.main_tv_course_header_state)
  private val mTvHeaderTitle: TextView = findViewById(R.id.main_tv_course_header_title)

  private val mTvHeaderTime: TextView = findViewById(R.id.main_tv_course_header_time)
  private val mTvHeaderPlace: TextView = findViewById(R.id.main_tv_course_header_place)
  private val mTvHeaderContent: TextView = findViewById(R.id.main_tv_course_header_content)
  private val mTvHeaderHint: TextView = findViewById(R.id.main_tv_course_header_hint)

  private val mBottomSheet = BottomSheetBehavior.from(findViewById(R.id.main_view_course_bottom_sheet))

  init {
    initCourse()
    initBottomSheet()
  }
  
  private fun initCourse() {
    mViewHeader.setOnClickListener {
      if (mBottomSheet.isDraggable) {
        if (mBottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
      }
    }

    if (mActivity.supportFragmentManager.findFragmentById(mFcvCourse.id) == null) {
      mActivity.supportFragmentManager.commit(true) {
        replace(mFcvCourse.id, mCourseService.createHomeCourseFragment())
      }
    }

    val oldBottomSheetIsExpand = mActivityViewModel.courseBottomSheetExpand.value
    if (oldBottomSheetIsExpand != true) {
      // 如果 value 之前值为 true，则说明已经展开，只能在没有展开时才允许设置透明度
      mCourseService.setCourseVpAlpha(0F)
      mCourseService.setHeaderAlpha(0F)
    } else {
      mViewHeader.gone()
    }
  
    CourseHeaderHelper.observeHeader()
      .observeOn(AndroidSchedulers.mainThread())
      .safeSubscribeBy(mActivity) { header ->
        when (header) {
          is CourseHeaderHelper.HintHeader -> {
            mTvHeaderState.invisible()
            mTvHeaderTitle.invisible()
            mTvHeaderTime.invisible()
            mTvHeaderPlace.invisible()
            mTvHeaderContent.invisible()
            mTvHeaderHint.visible()
            val throwable = header.throwable
            if (throwable == null) {
              mTvHeaderHint.text = header.hint
            } else {
              if (mTvHeaderHint.text.isEmpty()) {
                mTvHeaderHint.text = "发生异常，长按显示"
              }
              mTvHeaderHint.setOnLongClickListener {
                CrashDialog.Builder(throwable).show()
                true
              }
            }
          }
          is CourseHeaderHelper.ShowHeader -> {
            mTvHeaderState.visible()
            mTvHeaderTitle.visible()
            mTvHeaderTime.visible()
            mTvHeaderHint.invisible()
            mTvHeaderHint.setOnLongClickListener(null)
            mTvHeaderState.text = header.state
            mTvHeaderTitle.text = header.title
            mTvHeaderTime.text = header.time
            when (header.item) {
              is CourseHeaderHelper.LessonItem -> {
                mTvHeaderContent.invisible()
                mTvHeaderPlace.visible()
                mTvHeaderPlace.text = header.content
                mTvHeaderPlace.setOnSingleClickListener {
                  // 跳转至地图界面
                  startActivity(DISCOVER_MAP) {
                    putExtra(COURSE_POS_TO_MAP, header.content)
                  }
                }
                mTvHeaderTitle.setOnSingleClickListener {
                  mCourseService.openBottomSheetDialogByLesson(context, header.item.lesson)
                  // Umeng 埋点统计
                  Umeng.sendEvent(Umeng.Event.CourseDetail(true))
                }
              }
              is CourseHeaderHelper.AffairItem -> {
                mTvHeaderContent.visible()
                mTvHeaderPlace.invisible()
                mTvHeaderContent.text = header.content
                mTvHeaderTitle.setOnSingleClickListener {
                  mCourseService.openBottomSheetDialogByAffair(context, header.item.affair)
                }
              }
            }
          }
        }
      }
  }
  
  private fun initBottomSheet() {
    mBottomSheet.addBottomSheetCallback(
      object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
          when (newState) {
            BottomSheetBehavior.STATE_EXPANDED -> {
              mViewHeader.gone()
              if (mActivityViewModel.courseBottomSheetExpand.value != true) {
                mActivityViewModel.courseBottomSheetExpand.value = true
              }
              mCollapsedBackPressedCallback.isEnabled = true
            }
            BottomSheetBehavior.STATE_COLLAPSED -> {
              mFcvCourse.gone()
              if (mActivityViewModel.courseBottomSheetExpand.value != false) {
                mActivityViewModel.courseBottomSheetExpand.value = false
              }
              mCollapsedBackPressedCallback.isEnabled = false
            }
            BottomSheetBehavior.STATE_HIDDEN -> {
              if (mActivityViewModel.courseBottomSheetExpand.value != null) {
                mActivityViewModel.courseBottomSheetExpand.value = null
              }
              mCollapsedBackPressedCallback.isEnabled = false
            }
            else -> {}
          }
        }
      
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
          if (slideOffset >= 0) {
            /*
            * 展开时：
            * slideOffset：0.0 --------> 1.0
            * 课表主体:     0.0 --------> 1.0
            * 课表头部:     0.0 -> 0.0 -> 1.0
            * 主界面头部:   1.0 -> 0.0 -> 0.0
            *
            * 折叠时：
            * slideOffset：1.0 --------> 0.0
            * 课表主体:     1.0 --------> 0.0
            * 课表头部:     1.0 -> 0.0 -> 0.0
            * 主界面头部:   0.0 -> 0.0 -> 1.0
            * */
            mCourseService.setCourseVpAlpha(slideOffset)
            mCourseService.setHeaderAlpha(max(slideOffset * 2 - 1, 0F))
            mViewHeader.alpha = max(1 - slideOffset * 2, 0F)
            mViewHeader.visible()
            mFcvCourse.visible()
            mActivityViewModel.courseBottomSheetOffset.value = slideOffset
            mCourseService.setBottomSheetSlideOffset(slideOffset)
          }
        }
      }
    )
    
    // 数据埋点操作。如果你想监听 BottomSheet，请写其他地方！！！
    mBottomSheet.addBottomSheetCallback(
      object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
          if (newState == BottomSheetBehavior.STATE_EXPANDED) {
            // Umeng 统计课表显示
            Umeng.sendEvent(Umeng.Event.CourseShow)
          }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }
      }
    )
    
    mActivityViewModel.courseBottomSheetExpand.observe(mActivity) {
      mBottomSheet.isHideable = false
      if (it == null) {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_HIDDEN) {
          mBottomSheet.isHideable = true
          mBottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }
      } else if (it) {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
      } else {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_COLLAPSED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
      }
    }
    mAccountService.userInfo.onEach {
      // 只有登录了才允许拖动课表
      mBottomSheet.isDraggable = it != null
    }.launchIn(mActivity.lifecycleScope)
  }
  
  /**
   * 用于拦截返回键，在 BottomSheet 未折叠时先折叠
   */
  private val mCollapsedBackPressedCallback by lazyUnlock {
    mActivity.onBackPressedDispatcher.addCallback(mActivity) {
      mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }
  }
}