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
          refreshInternal()
        }
      }
      val old = refreshDateMap[dateKey]
      if (old == null || old.hierarchyIndex > itemHierarchy.hierarchyIndex) {
        refreshDateMap[dateKey] = itemHierarchy
      }
    }
  }

  private fun refreshInternal() {
    synchronized(refreshDateMapSynchronized) {
      refreshDateMap.forEach { (dateKey, itemHierarchy) ->
        var upperOverCover = itemHierarchy.getLastUpperOverCover(dateKey).toMutableList()
        for (i in itemHierarchy.hierarchyIndex..itemHierarchyList.lastIndex) {
          val hierarchy = itemHierarchyList[i]
          upperOverCover = hierarchy.refresh(dateKey, upperOverCover)
        }
      }
      refreshDateMap.clear()
    }
  }

  @Stable
  inner class ItemHierarchy<Item : CourseItem>(
    val hierarchyIndex: Int,
    val comparator: Comparator<Item>,
  ) {

    // key 为 dateKey = page * 7 + dayOfWeek.ordinal
    private inner class DateKeyValue {
      val itemWrapperList: MutableList<ItemWrapper> = mutableListOf<ItemWrapper>()
      val itemStateListStateFlow: MutableStateFlow<List<CourseItemState>> =
        MutableStateFlow(emptyList())

      // 上一次更新时的上一层的覆盖结果
      var lastUpperOverCover: List<OverlapCover> = emptyList()
    }

    private val dateKeyValueMap = LinkedHashMap<Int, DateKeyValue>()

    private fun getDateKeyValue(dateKey: Int): DateKeyValue {
      return dateKeyValueMap.getOrPut(dateKey) { DateKeyValue() }
    }

    private val dateItemsMapSynchronized = SynchronizedObject()

    private val itemWrapperMap: HashMap<Item, ItemWrapper> = HashMap()

    private fun getItemWrapper(item: Item): ItemWrapper {
      return itemWrapperMap.getOrPut(item) { ItemWrapper(item) }
    }

    internal fun getLastUpperOverCover(dateKey: Int): List<OverlapCover> {
      return getDateKeyValue(dateKey).lastUpperOverCover
    }

    // 添加一个 item
    fun add(item: Item) {
      synchronized(dateItemsMapSynchronized) {
        if (itemWrapperMap.containsKey(item)) return
        val dateKey = item.whatTime.now.page * 7 + item.whatTime.now.dayOfWeek.ordinal
        val list = getDateKeyValue(dateKey).itemWrapperList
        val index = getItemAfterIndex(item, list)
        list.add(index, getItemWrapper(item))
        tryRefresh(dateKey, this)
      }
    }

    // 移除一个 item
    fun remove(item: Item, whatTime: CourseItemWhatTime.Fixed = item.whatTime.now) {
      synchronized(dateItemsMapSynchronized) {
        val itemWrapper = itemWrapperMap.remove(item) ?: return
        val dateKey = whatTime.page * 7 + whatTime.dayOfWeek.ordinal
        itemWrapper.clear()
        getDateKeyValue(dateKey).itemWrapperList.remove(itemWrapper)
        tryRefresh(dateKey, this)
      }
    }

    // 重置所有 item 数据
    // 支持重叠数据的自动迁移
    fun reset(item: List<Item>) {
      synchronized(dateItemsMapSynchronized) {
        if (item.isEmpty()) {
          // 如果需要清空列表，则删除所有数据
          dateKeyValueMap.forEach { (dateKey, dateKeyValue) ->
            if (dateKeyValue.itemWrapperList.isNotEmpty()) {
              tryRefresh(dateKey, this)
              dateKeyValue.itemWrapperList.forEach {
                it.clear()
                itemWrapperMap.remove(it.item)
              }
              dateKeyValue.itemWrapperList.clear()
            }
          }
        } else {
          val newItemListMap =
            item.groupByTo(LinkedHashMap()) { it.whatTime.now.page * 7 + it.whatTime.now.dayOfWeek.ordinal }
          dateKeyValueMap.forEach { (dateKey, dateKeyValue) ->
            val newItemList = newItemListMap.remove(dateKey)?.sortedWith(comparator)
            if (newItemList != null) {
              // 新旧数据都包含同一天
              // 我们尝试判断数据是否发生了变化
              var isChanged = dateKeyValue.itemWrapperList.size != newItemList.size
              if (!isChanged) {
                // 按顺序比对 item 是否一致
                for ((index, itemWrapper) in dateKeyValue.itemWrapperList.withIndex()) {
                  val newItem = newItemList[index]
                  if (itemWrapper.item != newItem) {
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
                    if (!newItemList.contains(it.item)) {
                      it.clear() // newItemSet 中不包含说明已经被移除
                      itemWrapperMap.remove(it.item)
                    }
                  }
                  dateKeyValue.itemWrapperList.clear()
                }
                // 添加新数据
                dateKeyValue.itemWrapperList.addAll(newItemList.map { getItemWrapper(it) })
                tryRefresh(dateKey, this)
              }
            } else {
              if (dateKeyValue.itemWrapperList.isNotEmpty()) {
                // 新数据中不包含当天数据，但旧数据中包含
                // 我们清空当天的旧数据
                dateKeyValue.itemWrapperList.forEach {
                  it.clear()
                  itemWrapperMap.remove(it.item)
                }
                dateKeyValue.itemWrapperList.clear()
                tryRefresh(dateKey, this)
              }
            }
          }
          newItemListMap.forEach { (dateKey, itemList) ->
            // 剩下的为新增的数据
            val newItemList = itemList.sortedWith(comparator)
            // 添加新数据
            getDateKeyValue(dateKey).itemWrapperList.addAll(newItemList.map { getItemWrapper(it) })
            tryRefresh(dateKey, this)
          }
        }
      }
    }

    /**
     * @param dateKey 页面日期编号，page * 7 + dayOfWeek.ordinal
     * @param upperOverCover 上一层的覆盖
     * @return 放回当前最底层 Item 的覆盖
     */
    internal fun refresh(
      dateKey: Int,
      upperOverCover: MutableList<OverlapCover>
    ): MutableList<OverlapCover> {
      synchronized(dateItemsMapSynchronized) {
        val dateKeyValue = getDateKeyValue(dateKey)
        dateKeyValue.lastUpperOverCover = upperOverCover.toMutableList() // 保存上一层的覆盖副本
        dateKeyValue.itemStateListStateFlow.value = Snapshot.withMutableSnapshot { // 多次更新一起提交
          dateKeyValue.itemWrapperList.asReversed().map { itemWrapper ->
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
      val dateKey = page * 7 + dayOfWeek.ordinal
      synchronized(dateItemsMapSynchronized) {
        return getDateKeyValue(dateKey).itemStateListStateFlow
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
                add(item)
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