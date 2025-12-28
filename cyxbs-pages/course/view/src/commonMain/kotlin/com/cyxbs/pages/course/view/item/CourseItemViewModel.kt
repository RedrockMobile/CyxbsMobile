package com.cyxbs.pages.course.view.item

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.course.view.overlay.OverlapCover
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 管理课表中的所有 item，配合 [CourseItemHierarchy] 统一刷新重叠区间
 *
 * @author 985892345
 * @date 2025/10/19
 */
class CourseItemViewModel(
  vararg val itemHierarchy: CourseItemHierarchy<*>
) : BaseViewModel() {

  // key 为 dateKey = page * 7 + dayOfWeek.ordinal
  // value 为 hierarchyIndex，表示需要刷新的层级
  private val refreshDateSet = HashSet<Int>()
  private val refreshDateMapSynchronized = SynchronizedObject()

  init {
    itemHierarchy.forEach {
      it.bindCourseItemViewModel(this)
    }
  }

  fun tryRefresh(dateKey: Int) {
    synchronized(refreshDateMapSynchronized) {
      if (refreshDateSet.isEmpty()) {
        // 切换到下一次消息队列中执行刷新逻辑
        viewModelScope.launch(Dispatchers.Main) {
          refreshInternal()
        }
      }
      refreshDateSet.add(dateKey)
    }
  }

  private fun refreshInternal() {
    synchronized(refreshDateMapSynchronized) {
      refreshDateSet.forEach { dateKey ->
        val upperOverCover = mutableListOf<OverlapCover>()
        itemHierarchy.forEach {
          it.refresh(dateKey, upperOverCover)
        }
      }
      refreshDateSet.clear()
    }
  }
}
