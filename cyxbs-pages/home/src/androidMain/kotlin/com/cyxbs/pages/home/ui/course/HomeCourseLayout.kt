package com.cyxbs.pages.home.ui.course

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.crash.CrashDialog
import com.cyxbs.components.base.utils.Umeng
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.asFlow
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.invisible
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.home.R
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import com.cyxbs.pages.home.ui.course.utils.CourseHeaderHelper
import com.cyxbs.pages.map.api.MapNavArgument
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.CoroutineScope
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
@SuppressLint("ViewConstructor")
class HomeCourseLayout(
  context: Context,
  val lifecycleScope: CoroutineScope,
  val bottomNavViewModel: BottomNavViewModel,
  val courseBottomSheetViewModel: CourseBottomSheetViewModel,
) : FrameLayout(context) {

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

  /**
   * 当外层 Compose 容器释放时，需要移除监听，该 View 对象也不会再被重复使用
   */
  fun onRelease() {
    mCollapsedBackPressedCallback?.remove()
  }
  
  private fun initCourse() {
    mViewHeader.setOnClickListener {
      if (mBottomSheet.isDraggable) {
        if (mBottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
      }
    }

    // 这里只能重复 replace，因为在 Compose 中 FragmentManager.findFragmentById() 将不为 null，但是 fragment 却会不显示
    (context as FragmentActivity).supportFragmentManager.commit(true) {
      replace(mFcvCourse.id, mCourseService.createHomeCourseFragment())
    }

    CourseHeaderHelper.observeHeader()
      .observeOn(AndroidSchedulers.mainThread())
      .asFlow()
      .onEach { header ->
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
                  MainNavController.navigate(MapNavArgument(header.content))
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
      }.launchIn(lifecycleScope)
  }
  
  private fun initBottomSheet() {
    mBottomSheet.addBottomSheetCallback(
      object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
          when (newState) {
            BottomSheetBehavior.STATE_EXPANDED -> {
              mViewHeader.gone()
              if (courseBottomSheetViewModel.state.value != true) {
                courseBottomSheetViewModel.state.value = true
              }
              mCollapsedBackPressedCallback?.isEnabled = true
            }
            BottomSheetBehavior.STATE_COLLAPSED -> {
              mFcvCourse.gone()
              if (courseBottomSheetViewModel.state.value != false) {
                courseBottomSheetViewModel.state.value = false
              }
              mCollapsedBackPressedCallback?.isEnabled = false
            }
            BottomSheetBehavior.STATE_HIDDEN -> {
              if (courseBottomSheetViewModel.state.value != null) {
                courseBottomSheetViewModel.state.value = null
              }
              mCollapsedBackPressedCallback?.isEnabled = false
            }
            else -> {}
          }
        }
      
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
          onBottomSheetSlide(slideOffset)
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

    courseBottomSheetViewModel.state.onEach {
      mBottomSheet.isHideable = false
      if (it == null) {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_HIDDEN) {
          mBottomSheet.isHideable = true
          mBottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }
        onBottomSheetSlide(0F)
      } else if (it) {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
        onBottomSheetSlide(1F)
        mViewHeader.gone()
        mFcvCourse.visible()
      } else {
        if (mBottomSheet.state != BottomSheetBehavior.STATE_COLLAPSED) {
          mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        onBottomSheetSlide(0F)
        mViewHeader.visible()
        mFcvCourse.gone()
      }
    }.launchIn(lifecycleScope)

    bottomNavViewModel.selectedItem.onEach {
      if (it === bottomNavViewModel.fairgroundItem) {
        mBottomSheet.isHideable = true
        mBottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
      } else if (mBottomSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
        mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        mBottomSheet.isHideable = false
      }
    }.launchIn(lifecycleScope)

    mAccountService.state.onEach {
      // 只有登录了才允许拖动课表
      mBottomSheet.isDraggable = it is AccountState.Login
    }.launchIn(lifecycleScope)
  }
  
  /**
   * 用于拦截返回键，在 BottomSheet 未折叠时先折叠
   */
  private val mCollapsedBackPressedCallback by lazyUnlock {
    findViewTreeOnBackPressedDispatcherOwner()?.let {
      it.onBackPressedDispatcher.addCallback(it) {
        mBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
      }
    }
  }

  private fun onBottomSheetSlide(slideOffset: Float) {
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
      mCourseService.setBottomSheetSlideOffset(slideOffset)

      // 底部按钮跟随课表展开而变化
      bottomNavViewModel.offsetYRadio.floatValue = slideOffset
      bottomNavViewModel.alpha.floatValue = 1 - slideOffset
    }
  }
}