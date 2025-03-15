package com.cyxbs.pages.course.page.course.ui.home.utils

import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import com.cyxbs.components.config.config.SchoolCalendar
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.extensions.launch
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.api.utils.getBeginLesson
import com.cyxbs.pages.course.page.course.ui.home.HomeSemesterFragment
import com.cyxbs.pages.course.page.course.ui.home.HomeWeekFragment
import com.cyxbs.pages.course.page.course.ui.home.IHomePageFragment
import com.cyxbs.pages.course.page.course.ui.home.widget.HomeTouchAffairItem
import com.cyxbs.pages.course.widget.fragment.page.CoursePageFragment
import com.cyxbs.pages.course.widget.helper.affair.CreateAffairDispatcher
import com.cyxbs.pages.course.widget.helper.affair.expose.ICreateAffairConfig
import com.cyxbs.pages.course.widget.helper.affair.expose.ITouchAffairItem
import com.cyxbs.pages.course.widget.helper.affair.expose.ITouchCallback
import com.cyxbs.pages.course.widget.internal.item.IItem
import com.cyxbs.pages.course.widget.internal.item.IItemContainer
import com.cyxbs.pages.course.widget.internal.view.course.ICourseViewGroup
import com.ndhzs.netlayout.touch.multiple.event.IPointerEvent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

/**
 * 为了整合 [HomeSemesterFragment] 和 [HomeWeekFragment] 高度相似方法的帮助类
 *
 * @author 985892345
 * 2023/2/20 21:28
 */
class PageFragmentHelper<T> where T: IHomePageFragment, T: CoursePageFragment {
  
  fun init(fragment: T, savedInstanceState: Bundle?) {
    val isFragmentRebuilt = savedInstanceState != null
    fragment.apply {
      initEntrance(isFragmentRebuilt)
      initFoldLogic(isFragmentRebuilt)
      initCreateAffair()
      initNoInternetLogic()
    }
  }
  
  /**
   * 入场动画的实现
   */
  private fun T.initEntrance(isFragmentRebuilt: Boolean) {
    // 如果是被异常重启，则不执行动画
    if (!isFragmentRebuilt) {
      /**
       * 观察第几周，因为如果是初次进入应用，会因为得不到周数而不主动翻页，所以只能观察该数据
       * 但这是因为主页课表比较特殊而采取观察，其他界面可以直接使用 *VpFragment 的 mNowWeek 变量
       */
      SchoolCalendar.observeWeekOfTerm()
        .firstElement()
        .observeOn(AndroidSchedulers.mainThread())
        .safeSubscribeBy {
          if (it == week) {
            // 判断周数，只对当前周进行动画
            EnterAnimUtils.startEnterAnim(course, parentViewModel, viewLifecycleOwner)
          }
        }
    }
  }
  
  /**
   * 中午和傍晚的默认折叠状态
   */
  private fun T.initFoldLogic(isFragmentRebuilt: Boolean) {
    if (!isFragmentRebuilt) {
      // 在初始打开时，如果存在 item 在中午或者傍晚时间段，就主动展开
      // 只有在第一次显示 Fragment 时才进行监听，后面的折叠状态会由 CourseFoldHelper 保存
      course.addItemExistListener(
        object : IItemContainer.OnItemExistListener {
          override fun onItemAddedAfter(item: IItem, view: View?) {
            if (view == null) return
            val lp = item.lp
            if (compareNoonPeriodByRow(lp.startRow) * compareNoonPeriodByRow(lp.endRow) <= 0) {
              unfoldNoon()
              course.removeItemExistListener(this) // 只需要展开一次
            }
          }
        }
      )
      course.addItemExistListener(
        object : IItemContainer.OnItemExistListener {
          override fun onItemAddedAfter(item: IItem, view: View?) {
            if (view == null) return
            val lp = item.lp
            if (compareDuskPeriodByRow(lp.startRow) * compareDuskPeriodByRow(lp.endRow) <= 0) {
              unfoldDusk()
              course.removeItemExistListener(this) // 只需要展开一次
            }
          }
        }
      )
    }
  }
  
  /**
   * 长按创建事务
   */
  private fun T.initCreateAffair() {
    val dispatcher = CreateAffairDispatcher(
      this,
      object : ICreateAffairConfig {
        override fun createTouchAffairItem(
          course: ICourseViewGroup,
          event: IPointerEvent
        ): ITouchAffairItem {
          return HomeTouchAffairItem(course.getContext()) // 支持长按移动 item 的 ITouchAffairItem
        }
      }
    )
    // 事务的点击监听
    dispatcher.setOnClickListener {
      // 打开编辑事务详细的界面
      IAffairService::class.impl()
        .startActivityForAddAffair(week, lp.weekNum - 1, getBeginLesson(lp.startRow), lp.length)
      cancelShow()
    }
    // 长按创建事务时的回调
    dispatcher.addTouchCallback(
      object : ITouchCallback {
        
        private var mIsInLongPress = false
        
        override fun onLongPressed(pointerId: Int, row: Int, column: Int) {
          mIsInLongPress = true
        }
  
        override fun onShowTouchAffairItem(course: ICourseViewGroup, item: ITouchAffairItem) {
          if (!mIsInLongPress) {
            // 没有触发长按时显示 TouchAffairItem 说明它只是普通的点击
            tryToastSingleRow()
          }
          mIsInLongPress = false
        }
        
        /**
         * 在只是单独点击时，只会生成长度为 1 的 [ITouchAffairItem]，
         * 所以需要弹个 toast 来提示用户可以长按空白区域上下移动来生成更长的事务
         * (不然可能他一直不知道怎么生成更长的事务)
         */
        private fun tryToastSingleRow() {
          val sp = course.getContext().getSp("课表长按生成事务")
          val times = sp.getInt("点击单行事务的次数", 0)
          when (times) {
            1, 5, 12 -> {
              toast("可以长按空白处上下移动添加哦~")
            }
          }
          sp.edit { putInt("点击单行事务的次数", times + 1) }
        }
      }
    )
    course.addPointerDispatcher(dispatcher)
  }
  
  /**
   * 初始化没有网络连接时的逻辑
   */
  private fun T.initNoInternetLogic() {
    if (!ILessonService.isUseLocalSaveLesson) {
      val noLessonImage = ivNoLesson.drawable
      val noLessonText = tvNoLesson.text
      launch {
        if (!NetworkUtil.isAvailableExact()) {
          ivNoLesson.setImageResource(com.cyxbs.components.config.R.drawable.config_ic_404)
          tvNoLesson.text = "联网才能查看课表哦~"
          NetworkUtil.suspendUntilAvailable() // 挂起直到网络可用
          // 网络可用了就还原设置
          ivNoLesson.setImageDrawable(noLessonImage)
          tvNoLesson.text = noLessonText
        }
      }
    }
  }
}