package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.overlay.OverlapCover
import com.cyxbs.pages.course.view.overlay.createOverlapResult
import com.cyxbs.pages.course.view.page.LocalCoursePage
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DayOfWeek

/**
 * 课表 item 层级，用于控制 item 重叠关系
 * - [add]: 添加一个 item
 * - [remove]: 移除一个 item
 * - [replace]: 替换 item 的 fixed
 * - [reset]: 重置所有 item
 * - [CoursePageItemListContent]: 展示 item
 *
 * @author 985892345
 * @date 2025/12/27
 */
@Stable
class CourseItemHierarchy<Item : CourseItem> {

  private var courseItemViewModel: CourseItemViewModel? = null
  private val whatTimeListWhenNoCourseItemViewModel = mutableListOf<ItemHierarchyWhatTime<Item>>()

  // 绑定 CourseItemViewModel
  fun bindCourseItemViewModel(courseItemViewModel: CourseItemViewModel) {
    require(this.courseItemViewModel == null) {
      "不允许重新绑定 CourseItemViewModel，old: ${this.courseItemViewModel}, new: $courseItemViewModel"
    }
    this.courseItemViewModel = courseItemViewModel
    reset(whatTimeListWhenNoCourseItemViewModel)
    whatTimeListWhenNoCourseItemViewModel.clear()
  }

  // key 为 dateKey = page * 7 + dayOfWeek.ordinal
  private inner class DateKeyValue {
    val itemWrapperList: MutableList<ItemHierarchyWhatTime<Item>> = mutableListOf()
    val itemStateListStateFlow: MutableStateFlow<List<CourseItemState>> =
      MutableStateFlow(emptyList())
  }

  private val dateKeyValueMap = LinkedHashMap<Int, DateKeyValue>()

  private fun getDateKeyValue(dateKey: Int): DateKeyValue {
    return dateKeyValueMap.getOrPut(dateKey) { DateKeyValue() }
  }

  private val dateItemsMapSynchronized = SynchronizedObject()

  private val itemWrapperMap: HashMap<ItemHierarchyWhatTime<Item>, ItemWrapper> = HashMap()


  // 添加一个 item
  fun add(whatTime: ItemHierarchyWhatTime<Item>) {
    synchronized(dateItemsMapSynchronized) {
      val courseItemViewModel = courseItemViewModel
      if (courseItemViewModel == null) {
        whatTimeListWhenNoCourseItemViewModel.add(whatTime)
        return
      }
      if (itemWrapperMap.containsKey(whatTime)) return
      itemWrapperMap[whatTime] = ItemWrapper(whatTime, courseItemViewModel)
      val fixed = whatTime.now.value
      if (fixed.page < 0) return // 负数表示展示不添加进 itemWrapperList
      val dateKey = fixed.page * 7 + fixed.dayOfWeek.ordinal
      val itemWrapperList = getDateKeyValue(dateKey).itemWrapperList
      val index = getIndex(whatTime, itemWrapperList) // 保证有序插入
      itemWrapperList.add(index, whatTime)
      courseItemViewModel.tryRefresh(dateKey)
    }
  }

  // 移除一个 item
  fun remove(
    whatTime: ItemHierarchyWhatTime<Item>,
    fixed: CourseItemWhatTime.Fixed = whatTime.now.value
  ) {
    synchronized(dateItemsMapSynchronized) {
      if (whatTimeListWhenNoCourseItemViewModel.remove(whatTime)) {
        return
      }
      val itemWrapper = itemWrapperMap.remove(whatTime) ?: return
      itemWrapper.onClear()
      if (fixed.page < 0) return // 负数表示展示未添加进 itemWrapperList
      val dateKey = fixed.page * 7 + fixed.dayOfWeek.ordinal
      getDateKeyValue(dateKey).itemWrapperList.remove(whatTime)
      courseItemViewModel?.tryRefresh(dateKey)
    }
  }

  // 替换 item 的 fixed
  fun replace(
    whatTime: ItemHierarchyWhatTime<Item>,
    oldFixed: CourseItemWhatTime.Fixed,
    newFixed: CourseItemWhatTime.Fixed,
  ) {
    synchronized(dateItemsMapSynchronized) {
      if (whatTimeListWhenNoCourseItemViewModel.contains(whatTime)) return // 还未添加进 itemWrapperList
      if (!itemWrapperMap.containsKey(whatTime)) return
      if (oldFixed.page >= 0) {
        val oldDateKey = oldFixed.page * 7 + oldFixed.dayOfWeek.ordinal
        getDateKeyValue(oldDateKey).itemWrapperList.remove(whatTime)
        courseItemViewModel?.tryRefresh(oldDateKey)
      }
      if (newFixed.page < 0) return // 负数表示展示不添加进 itemWrapperList
      val newDateKey = newFixed.page * 7 + newFixed.dayOfWeek.ordinal
      val itemWrapperList = getDateKeyValue(newDateKey).itemWrapperList
      val index = getIndex(whatTime, itemWrapperList) // 保证有序插入
      itemWrapperList.add(index, whatTime)
      courseItemViewModel?.tryRefresh(newDateKey)
    }
  }

  // 重置所有 item 数据
  // 支持相等数据的自动迁移
  fun reset(whatTimeList: List<ItemHierarchyWhatTime<Item>>) {
    synchronized(dateItemsMapSynchronized) {
      val courseItemViewModel = courseItemViewModel
      if (courseItemViewModel == null) {
        whatTimeListWhenNoCourseItemViewModel.clear()
        whatTimeListWhenNoCourseItemViewModel.addAll(whatTimeList)
        return
      }
      if (whatTimeList.isEmpty()) {
        // 如果需要清空列表，则删除所有数据
        dateKeyValueMap.forEach { (dateKey, dateKeyValue) ->
          if (dateKeyValue.itemWrapperList.isNotEmpty()) {
            courseItemViewModel.tryRefresh(dateKey)
            dateKeyValue.itemWrapperList.forEach {
              itemWrapperMap.remove(it)?.onClear()
            }
            dateKeyValue.itemWrapperList.clear()
          }
        }
      } else {
        val newItemListMap = whatTimeList.groupByTo(LinkedHashMap()) {
          val fixed = it.now.value
          fixed.page * 7 + fixed.dayOfWeek.ordinal
        }
        dateKeyValueMap.forEach { (dateKey, dateKeyValue) ->
          val newItemList = newItemListMap.remove(dateKey)?.apply { sort() }  // 排序
          if (newItemList != null) {
            // 新旧数据都包含同一天
            // 我们尝试判断数据是否发生了变化
            var isChanged = dateKeyValue.itemWrapperList.size != newItemList.size
            if (!isChanged) {
              // 按顺序比对 item 是否一致
              for ((index, itemWrapper) in dateKeyValue.itemWrapperList.withIndex()) {
                val newItem = newItemList[index]
                if (itemWrapper != newItem) {
                  isChanged = true
                  break
                }
              }
            }
            if (isChanged) {
              // 新旧数据对不上
              if (dateKeyValue.itemWrapperList.isNotEmpty()) {
                // 移除旧数据
                dateKeyValue.itemWrapperList.forEach {
                  if (!newItemList.contains(it)) {
                    // newItemSet 中不包含说明已经被移除
                    itemWrapperMap.remove(it)?.onClear()
                  }
                }
                dateKeyValue.itemWrapperList.clear()
              }
              // 添加新数据
              newItemList.forEach { itemWrapperMap.getOrPut(it) { ItemWrapper(it, courseItemViewModel) } }
              dateKeyValue.itemWrapperList.addAll(newItemList)
              courseItemViewModel.tryRefresh(dateKey)
            }
          } else {
            if (dateKeyValue.itemWrapperList.isNotEmpty()) {
              // 新数据中不包含当天数据，但旧数据中包含
              // 我们清空当天的旧数据
              dateKeyValue.itemWrapperList.forEach {
                itemWrapperMap.remove(it)?.onClear()
              }
              dateKeyValue.itemWrapperList.clear()
              courseItemViewModel.tryRefresh(dateKey)
            }
          }
        }
        newItemListMap.forEach { (dateKey, itemList) ->
          // 剩下的为新增的 dateKey 对应数据
          val newItemList = itemList.apply { sort() } // 排序
          // 添加新数据
          newItemList.forEach { itemWrapperMap.getOrPut(it) { ItemWrapper(it, courseItemViewModel) } }
          if (dateKey >= 0) { // < 0 则延后添加进 itemWrapperList
            getDateKeyValue(dateKey).itemWrapperList.addAll(newItemList) // newItemList 已排序
            courseItemViewModel.tryRefresh(dateKey)
          }
        }
      }
    }
  }

  /**
   * @param dateKey 页面日期编号，page * 7 + dayOfWeek.ordinal
   * @param upperOverCover 上一层的覆盖
   */
  internal fun refresh(
    dateKey: Int,
    upperOverCover: MutableList<OverlapCover>
  ) {
    synchronized(dateItemsMapSynchronized) {
      val dateKeyValue = getDateKeyValue(dateKey)
      dateKeyValue.itemStateListStateFlow.value = Snapshot.withMutableSnapshot { // 多次更新一起提交
        dateKeyValue.itemWrapperList.asReversed().mapNotNull { whatTime ->
          val itemWrapper = itemWrapperMap[whatTime] ?: return@mapNotNull null
          itemWrapper.itemState.updateOverlap(
            itemWrapper.itemState.createOverlapResult(
              coveredList = upperOverCover,
            )
          )
          itemWrapper.itemState
        }.asReversed()
      }
    }
  }

  fun observe(page: Int, dayOfWeek: DayOfWeek): StateFlow<List<CourseItemState>> {
    val dateKey = page * 7 + dayOfWeek.ordinal
    synchronized(dateItemsMapSynchronized) {
      return getDateKeyValue(dateKey).itemStateListStateFlow
    }
  }

  // 显示所有 item
  @Composable
  fun CoursePageItemListContent() {
    val pageContext = LocalCoursePage.current
    val courseItemStateListState = remember { mutableStateOf(emptyList<CourseItemState>()) }
    courseItemStateListState.value.fastForEach { itemState ->
      key(itemState) {
        CompositionLocalProvider(LocalCourseItemState provides itemState) {
          itemState.updateCoursePage(pageContext)
          itemState.item.CourseItemContent()
        }
      }
    }
    LaunchedEffect(pageContext.page) {
      // 这里不能以 DayOfWeek 维度来拆分成多个子 Compose 函数
      // 必须要整合所有 DayOfWeek 到一个 state 里面 (courseItemStateListState)
      // 原因在于分开后 DayOfWeek 的每次遍历都会认为是一个新的 group
      // 如果一个 item 从 周一 移动到 周二，则会认为是 周一 group 下的 item 被移除，周二 group 下新增了 item (即使两次 item 是同一个对象)
      // 使用了 key(item) 来判断也是如此，因为 key 是在每个 group 下的判断
      // 结论：每次循环都会生成一个新的 group，如果想让 item 能正常的移动，就需要在同一个 group 下，即只能用一层循环
      combine(
        DayOfWeek.entries.map {
          observe(pageContext.page, it)
        }
      ) {
        courseItemStateListState.value = it.toList().flatten()
      }.launchIn(this)
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
  private fun getIndex(
    item: ItemHierarchyWhatTime<Item>,
    list: List<ItemHierarchyWhatTime<Item>>
  ): Int {
    var start = 0
    var end = list.size - 1
    while (start <= end) {
      val half = (start + end) ushr 1 // 算术移位，防止溢出
      val nowItem = list[half]
      if (item >= nowItem) {
        start = half + 1
      } else {
        end = half - 1
      }
    }
    return start
  }

  private inner class ItemWrapper(
    val whatTime: ItemHierarchyWhatTime<Item>,
    val courseItemViewModel: CourseItemViewModel,
  ) {

    // 绑定在 viewModelScope 之下的子协程作用域
    private val coroutineScope = CoroutineScope(
      courseItemViewModel.viewModelScope.coroutineContext
          + SupervisorJob(courseItemViewModel.viewModelScope.coroutineContext[Job])
    )

    val item = whatTime.createItem(coroutineScope)

    val itemState = CourseItemState(item)

    init {
      item.itemState = itemState
      var lastFixed = whatTime.now.value
      whatTime.now.onEach {
        if (it != lastFixed) {
          replace(whatTime, lastFixed, it)
          lastFixed = it
        }
      }.launchIn(coroutineScope)
    }

    fun onClear() {
      coroutineScope.cancel()
    }
  }
}


abstract class ItemHierarchyWhatTime<Item : CourseItem> : CourseItemWhatTime, Comparable<ItemHierarchyWhatTime<Item>> {
  abstract override val now: MutableStateFlow<CourseItemWhatTime.Fixed>
  abstract fun createItem(coroutineScope: CoroutineScope): Item
  abstract override fun equals(other: Any?): Boolean
  abstract override fun hashCode(): Int
}