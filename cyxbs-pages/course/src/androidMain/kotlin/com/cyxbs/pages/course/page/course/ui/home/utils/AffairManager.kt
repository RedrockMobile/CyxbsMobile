package com.cyxbs.pages.course.page.course.ui.home.utils

import android.view.View
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.page.course.data.AffairData
import com.cyxbs.pages.course.page.course.data.toAffair
import com.cyxbs.pages.course.page.course.item.affair.AffairItem
import com.cyxbs.pages.course.page.course.item.affair.IAffairManager
import com.cyxbs.pages.course.page.course.ui.home.IHomePageFragment
import com.cyxbs.pages.course.widget.fragment.page.ICoursePage
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

/**
 * 管理课表事务的长按移动以及扩展后数据更新的逻辑
 *
 * 因为课表采取观察者模式，如果直接更新数据库，会导致课表直接更新布局，
 * 但是此时可能正在执行最后的移动动画，更新布局会导致动画出问题，
 *
 * 所以该类在长按开始时就取消课表对数据的观察，然后更新数据库中的数据，最后在动画结束时重新开启观察流
 *
 * @author 985892345
 * 2023/2/23 13:09
 */
class AffairManager(
  val fragment: IHomePageFragment,
) : IAffairManager {
  
  private var mCount = 0
  
  /**
   * 是否是简单事务，什么是简单事务请看 [IAffairManager.onChange] 注释
   */
  override fun isSingleAffair(data: AffairData): Boolean {
    val viewModel = fragment.parentViewModel
    val homePageResultMap = viewModel.homeWeekData.value ?: return false
    
    var result = 0
    outer@ for (entry in homePageResultMap) {
      // 遍历所有事务数据，从中寻找拥有相同 id 的 affair 有几个
      for (it in entry.value.affair) {
        if (it.onlyId == data.onlyId) {
          result++
        }
        if (result > 1) {
          // 如果找到的结果大于 1 就跳出循环
          break@outer
        }
      }
    }
    
    // 目前因为事务存在重复的情况，所以只有当相同的事务只有一个的时候才能移动到新位置
    return result == 1
  }
  
  override fun onStart(page: ICoursePage, affair: AffairItem, child: View) {
    incCount()
  }
  
  override fun onChange(
    page: ICoursePage,
    affair: AffairItem,
    child: View,
    oldData: AffairData,
    newData: AffairData,
  ): Boolean {
    if (oldData.onlyId == newData.onlyId) {
      val isSingleAffair = isSingleAffair(newData)
      if (isSingleAffair && oldData != newData) {
        changeAffair(oldData, newData)
      }
      return isSingleAffair
    }
    return false
  }
  
  override fun onEnd(page: ICoursePage, affair: AffairItem, child: View) {
    decCount()
  }
  
  private fun changeAffair(
    oldData: AffairData,
    newData: AffairData,
  ) {
    // 修改差分刷新时比对的数据，这个调用后也会同步修改 affair.data
    // 当然也是可以不修改的，只是如果不修改的话，会因为差分比对出数据发生改变而重新添加 item 导致出现闪动
    fragment.affairContainerProxy.replaceDataFromOldList(oldData, newData)
    incCount()
    IAffairService::class.impl()
      .updateAffair(newData.toAffair())
      .observeOn(AndroidSchedulers.mainThread())
      .doOnTerminate {
        decCount()
      }.unsafeSubscribeBy()
  }
  
  private fun incCount() {
    if (mCount == 0) {
      // 取消课表数据的观察流
      fragment.parentViewModel.cancelDataObserve()
    }
    mCount++
  }
  
  private fun decCount() {
    mCount--
    if (mCount == 0) {
      // 在移动结束后才重新开启课表数据的观察流
      fragment.parentViewModel.refreshDataObserve()
    }
  }
}