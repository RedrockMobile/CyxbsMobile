package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.course.view.overlay.OverlapCover
import com.cyxbs.pages.course.view.overlay.createOverlapResult
import com.cyxbs.pages.course.view.page.LocalCoursePage
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
  private val refreshDateSet = HashSet<Int>()
  private val refreshDateMapSynchronized = SynchronizedObject()

  fun <Item : CourseItem> createOverlay(): ItemHierarchy<Item> {
    return ItemHierarchy<Item>().also {
      itemHierarchyList.add(it)
    }
  }

  private fun tryRefresh(dateKey: Int) {
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
        itemHierarchyList.forEach {
          it.refresh(dateKey, upperOverCover)
        }
      }
      refreshDateSet.clear()
    }
  }

  @Stable
  inner class ItemHierarchy<Item : CourseItem> {

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

    private fun getItemWrapper(item: ItemHierarchyWhatTime<Item>): ItemWrapper {
      return itemWrapperMap.getOrPut(item) { ItemWrapper(item) }
    }

    // 添加一个 item
    fun add(whatTime: ItemHierarchyWhatTime<Item>) {
      synchronized(dateItemsMapSynchronized) {
        if (itemWrapperMap.containsKey(whatTime)) return
        itemWrapperMap[whatTime] = ItemWrapper(whatTime)
        val fixed = whatTime.now.value
        if (fixed.page < 0) return // 负数表示展示不添加进 itemWrapperList
        val dateKey = fixed.page * 7 + fixed.dayOfWeek.ordinal
        val itemWrapperList = getDateKeyValue(dateKey).itemWrapperList
        val index = getIndex(whatTime, itemWrapperList) // 保证有序插入
        itemWrapperList.add(index, whatTime)
        tryRefresh(dateKey)
      }
    }

    // 移除一个 item
    fun remove(
      whatTime: ItemHierarchyWhatTime<Item>,
      fixed: CourseItemWhatTime.Fixed = whatTime.now.value
    ) {
      synchronized(dateItemsMapSynchronized) {
        val itemWrapper = itemWrapperMap.remove(whatTime) ?: return
        itemWrapper.onClear()
        if (fixed.page < 0) return // 负数表示展示未添加进 itemWrapperList
        val dateKey = fixed.page * 7 + fixed.dayOfWeek.ordinal
        getDateKeyValue(dateKey).itemWrapperList.remove(whatTime)
        tryRefresh(dateKey)
      }
    }

    // 重置所有 item 数据
    // 支持相等数据的自动迁移
    fun reset(whatTimeList: List<ItemHierarchyWhatTime<Item>>) {
      synchronized(dateItemsMapSynchronized) {
        if (whatTimeList.isEmpty()) {
          // 如果需要清空列表，则删除所有数据
          dateKeyValueMap.forEach { (dateKey, dateKeyValue) ->
            if (dateKeyValue.itemWrapperList.isNotEmpty()) {
              tryRefresh(dateKey)
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
                newItemList.forEach { getItemWrapper(it) }
                dateKeyValue.itemWrapperList.addAll(newItemList)
                tryRefresh(dateKey)
              }
            } else {
              if (dateKeyValue.itemWrapperList.isNotEmpty()) {
                // 新数据中不包含当天数据，但旧数据中包含
                // 我们清空当天的旧数据
                dateKeyValue.itemWrapperList.forEach {
                  itemWrapperMap.remove(it)?.onClear()
                }
                dateKeyValue.itemWrapperList.clear()
                tryRefresh(dateKey)
              }
            }
          }
          newItemListMap.forEach { (dateKey, itemList) ->
            // 剩下的为新增的 dateKey 对应数据
            val newItemList = itemList.apply { sort() } // 排序
            // 添加新数据
            newItemList.forEach { getItemWrapper(it) }
            if (dateKey >= 0) { // < 0 则延后添加进 itemWrapperList
              getDateKeyValue(dateKey).itemWrapperList.addAll(newItemList)
              tryRefresh(dateKey)
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
          dateKeyValue.itemWrapperList.asReversed().map { whatTime ->
            val itemWrapper = getItemWrapper(whatTime)
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

    @Composable
    fun CoursePageItemListContent() {
      DayOfWeek.entries.forEach {
        DayOfWeekCompose(it)
      }
    }

    @Composable
    private fun DayOfWeekCompose(
      dayOfWeek: DayOfWeek,
    ) {
      val pageContext = LocalCoursePage.current
      val overlayResultList by observe(pageContext.page, dayOfWeek).collectAsState()
      overlayResultList.fastForEach { itemState ->
        key(itemState) {
          CompositionLocalProvider(LocalCourseItemState provides itemState) {
            itemState.updateCoursePage(pageContext)
            itemState.item.CourseItemContent()
          }
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
    ) {

      // 绑定在 viewModelScope 之下的子协程作用域
      private val coroutineScope = CoroutineScope(
        viewModelScope.coroutineContext
            + SupervisorJob(viewModelScope.coroutineContext[Job])
      )

      val itemState = CourseItemState(whatTime.createItem(coroutineScope))

      init {
        var lastFixed = whatTime.now.value
        whatTime.now.onEach {
          if (it != lastFixed) {
            remove(whatTime, lastFixed)
            add(whatTime)
            lastFixed = it
          }
        }.launchIn(coroutineScope)
      }

      fun onClear() {
        coroutineScope.cancel()
      }
    }
  }
}


abstract class ItemHierarchyWhatTime<Item : CourseItem> : CourseItemWhatTime, Comparable<ItemHierarchyWhatTime<Item>> {
  abstract override val now: MutableStateFlow<CourseItemWhatTime.Fixed>
  abstract fun createItem(coroutineScope: CoroutineScope): Item
  abstract override fun equals(other: Any?): Boolean
  abstract override fun hashCode(): Int
}