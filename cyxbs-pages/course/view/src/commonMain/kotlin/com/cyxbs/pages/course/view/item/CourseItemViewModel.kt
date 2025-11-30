package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.view.overlay.OverlapCover
import com.cyxbs.pages.course.view.overlay.createOverlapResult
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/19
 */
class CourseItemViewModel : BaseViewModel() {

  private val itemHierarchyList = mutableListOf<ItemHierarchy<*>>()

  // key 为 dateKey = page * 7 + dayOfWeek.ordinal
  // value 为 hierarchyIndex，表示需要刷新的层级
  private val refreshDateMap = HashMap<Int, ItemHierarchy<*>>()
  private val refreshDateMapSynchronized = SynchronizedObject()

  fun <Item : CourseItem> createOverlay(comparator: Comparator<Item>): ItemHierarchy<Item> {
    return ItemHierarchy(
      hierarchyIndex = itemHierarchyList.size,
      comparator = comparator,
    ).also {
      itemHierarchyList.add(it)
    }
  }

  private fun tryRefresh(dateKey: Int, itemHierarchy: ItemHierarchy<*>) {
    synchronized(refreshDateMapSynchronized) {
      if (refreshDateMap.isEmpty()) {
        // 切换到下一次消息队列中执行刷新逻辑
        viewModelScope.launch(Dispatchers.Main) {
          synchronized(refreshDateMapSynchronized) {
            refreshInternal(refreshDateMap)
            refreshDateMap.clear()
          }
        }
      }
      val old = refreshDateMap[dateKey]
      if (old == null || old.hierarchyIndex > itemHierarchy.hierarchyIndex) {
        refreshDateMap[dateKey] = itemHierarchy
      }
    }
  }

  private fun refreshInternal(dateKeyWithHierarchy: Map<Int, ItemHierarchy<*>>) {
    dateKeyWithHierarchy.forEach {
      val dateKey = it.key
      var upperOverCover = it.value.lastUpperOverCover
      for (i in it.value.hierarchyIndex .. itemHierarchyList.lastIndex) {
        val hierarchy = itemHierarchyList[i]
        hierarchy.lastUpperOverCover = hierarchy.refresh(dateKey, upperOverCover).toMutableList()
      }
    }
  }

  @Stable
  inner class ItemHierarchy<Item : CourseItem>(
    val hierarchyIndex: Int,
    val comparator: Comparator<Item>,
  ) {

    // key 为 dateKey = page * 7 + dayOfWeek.ordinal
    // value 为当天所有的 item
    private val dateItemsMap = HashMap<Int, MutableList<ItemWrapper>>()

    private val dateItemMapFlow = HashMap<Int, MutableStateFlow<List<CourseItemState>>>()

    private val dateItemsMapSynchronized = SynchronizedObject()

    // 上一次更新时的上一层的覆盖结果
    internal var lastUpperOverCover = mutableListOf<OverlapCover>()

    // 添加一个 item
    fun add(item: Item, whatTime: CourseItemWhatTime.Fixed = item.whatTime.now) {
      synchronized(dateItemsMapSynchronized) {
        val dateKey = whatTime.now.page * 7 + whatTime.now.dayOfWeek.ordinal
        val list = dateItemsMap.getOrPut(dateKey) { mutableListOf() }
        val index = getItemAfterIndex(item, list)
        list.add(index, ItemWrapper(item))
        tryRefresh(dateKey, this)
      }
    }

    // 移除一个 item
    fun remove(item: Item, whatTime: CourseItemWhatTime.Fixed = item.whatTime.now) {
      synchronized(dateItemsMapSynchronized) {
        val dateKey = whatTime.page * 7 + whatTime.dayOfWeek.ordinal
        val list = dateItemsMap[dateKey] ?: return
        val index = list.indexOfFirst { it.item === item }
        if (index < 0) return
        list.removeAt(index).clear()
        tryRefresh(dateKey, this)
      }
    }

    // 重置所有 item 数据
    fun reset(item: List<Item>) {
      synchronized(dateItemsMapSynchronized) {
        dateItemsMap.forEach { entry ->
          entry.value.forEach { itemWrapper ->
            itemWrapper.clear()
            val whatTime = itemWrapper.item.whatTime.now
            val dateKey = whatTime.page * 7 + whatTime.dayOfWeek.ordinal
            tryRefresh(dateKey, this)
          }
          entry.value.clear()
        }
        dateItemsMap.clear()
        if (item.isEmpty()) return
        val map = item.groupBy { it.whatTime.now.page * 7 + it.whatTime.now.dayOfWeek.ordinal }
        map.forEach { entry ->
          dateItemsMap.getOrPut(entry.key) { mutableListOf() }
            .addAll(entry.value.sortedWith(comparator).map {
              ItemWrapper(it)
            })
          tryRefresh(entry.key, this)
        }
      }
    }

    /**
     * @param dateKey 页面日期编号，page * 7 + dayOfWeek.ordinal
     * @param upperOverCover 上一层的覆盖
     * @return 放回当前最底层 Item 的覆盖
     */
    fun refresh(dateKey: Int, upperOverCover: MutableList<OverlapCover>): MutableList<OverlapCover> {
      synchronized(dateItemsMapSynchronized) {
        val itemList = dateItemsMap[dateKey] ?: return upperOverCover
        dateItemMapFlow.getOrPut(dateKey) {
          MutableStateFlow(emptyList())
        }.value = Snapshot.withMutableSnapshot { // 多次更新一起提交
          itemList.asReversed().map { itemWrapper ->
            itemWrapper.itemState.updateOverlap(
              itemWrapper.itemState.createOverlapResult(
                coveredList = upperOverCover,
              )
            )
            itemWrapper.itemState
          }.asReversed()
        }
        return upperOverCover
      }
    }

    fun observe(page: Int, dayOfWeek: DayOfWeek): StateFlow<List<CourseItemState>> {
      synchronized(dateItemsMapSynchronized) {
        return dateItemMapFlow.getOrPut(page * 7 + dayOfWeek.ordinal) {
          MutableStateFlow(emptyList())
        }
      }
    }

    /**
     * 控制被添加进来时的顺序，如果有重复的相同值，则是最后一位相同值的索引加 1
     * ```
     * 如：
     *             2  4  6  6  8
     * 添加 6 进来: 2  4  6  6  6  8
     *                         ↑
     * ```
     */
    private fun getItemAfterIndex(item: Item, list: List<ItemWrapper>): Int {
      var start = 0
      var end = list.size - 1
      while (start <= end) {
        val half = (start + end) ushr 1 // 算术移位，防止溢出
        val nowItem = list[half]
        if (comparator.compare(item, nowItem.item) >= 0) {
          start = half + 1
        } else {
          end = half - 1
        }
      }
      return start
    }

    private inner class ItemWrapper(
      val item: Item,
    ) {

      val itemState = CourseItemState(item)

      private val whatTime: CourseItemWhatTime = item.whatTime
      private var lastFixed = whatTime.now

      private var changeableJob: Job? = null

      init {
        if (whatTime is CourseItemWhatTime.Changeable) {
          changeableJob = appCoroutineScope.launch {
            whatTime.observe().collect {
              if (it != lastFixed) {
                remove(item, lastFixed)
                add(item, it)
                lastFixed = it
              }
            }
          }
        }
      }

      fun clear() {
        changeableJob?.cancel()
      }
    }
  }
}