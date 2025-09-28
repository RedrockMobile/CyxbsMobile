package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import com.cyxbs.pages.course.view.overlay.CourseItemOverlap
import com.cyxbs.pages.course.view.overlay.CourseItemRange
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * [CourseItemWrapper] 对应的 状态，生命周期与页面进行绑定
 *
 * 如果需要获取的话可通过 LocalCoursePage.current.findItemState(item.weekItemKey) 得到
 *
 * @author 985892345
 * @date 2025/5/4
 */
@Stable
class CourseItemState(
  timeline: CourseTimeline,
  overlap: CourseItemOverlap,
) {

  var timeline by mutableStateOf(timeline)
    private set

  // item 重叠数据
  var overlap by mutableStateOf(overlap)
    private set

  val itemWrapper: CourseItemWrapper<*>
    get() = overlap.wrapper

  // item 真实展示区间
  // 通过转换器最后才能确定
  var realShowRange: List<CourseItemRange> by mutableStateOf(overlap.showRangeList)
    private set

  fun update(timeline: CourseTimeline, overlap: CourseItemOverlap) {
    this.timeline = timeline
    this.overlap = overlap
    // 调用 overlap 触发器
    overlapChangeTriggers = overlapChangeTriggers.mapValuesTo(LinkedHashMap()) {
      it.value.onDispose()
      it.key.onChangeOverlap(overlap)
    }
    // 调用 showRange 转换器
    realShowRange = showRangeTransformers.fold(overlap.showRangeList) { prev, interceptor ->
      interceptor.transform(prev, overlap)
    }
  }

  private val dataStoreMap = mutableMapOf<DataStore<*>, Any?>()

  // 用于保存特定的数据
  fun <T> setData(key: DataStore<T>, data: T?): T? {
    @Suppress("UNCHECKED_CAST")
    return dataStoreMap.put(key, data) as T?
  }

  fun <T> getData(key: DataStore<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return dataStoreMap[key] as T?
  }

  private val showRangeTransformers = linkedSetOf<ShowRangeTransformer>()

  // showRange 转换器，用于将 overlap 原始的 showRangeList 进行转换，最后赋值给 realShowRange
  // 在每次 overlap 更新时触发转换
  fun addShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.add(interceptor)) {
      realShowRange = Snapshot.withoutReadObservation {
        interceptor.transform(realShowRange, overlap)
      }
    }
  }

  fun removeShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.remove(interceptor)) {
      // 移除后使用转换器重新计算 realShowRange
      realShowRange = Snapshot.withoutReadObservation {
        showRangeTransformers.fold(overlap.showRangeList) { prev, now ->
          now.transform(prev, overlap)
        }
      }
    }
  }

  private var overlapChangeTriggers =
    linkedMapOf<OverlapChangeTrigger, OverlapChangeTrigger.OnDisposable>()

  // overlap 更新触发器，监听 overlap 的更新用于触发一些特定的操作
  // 在每次 overlap 更新时触发
  fun addOverlapChangeTrigger(trigger: OverlapChangeTrigger) {
    overlapChangeTriggers[trigger]?.onDispose()
    overlapChangeTriggers[trigger] = trigger.onChangeOverlap(Snapshot.withoutReadObservation { overlap })
  }

  fun removeOverlapChangeTrigger(trigger: OverlapChangeTrigger) {
    overlapChangeTriggers.remove(trigger)?.onDispose()
  }


  /**
   * 配合 [OverlapChangeTrigger] 给被覆盖 item 添加 showRange 监听器的快捷方式
   */
  class CoveredItemShowRangeTransformerTrigger(
    val pageContext: LocalCoursePageContext,
    val transformer: ShowRangeTransformer,
  ) : OverlapChangeTrigger {
    override fun onChangeOverlap(overlap: CourseItemOverlap): OverlapChangeTrigger.OnDisposable {
      val coveredItemStateList = mutableListOf<CourseItemState>()
      val findOnDisposables = overlap.coveredItemList.fastMapNotNull { cover ->
        pageContext.findItemState(cover.itemOverlap.wrapper) {
          // 找到被覆盖的 item 添加 showRange 转换器
          it.addShowRangeTransformer(transformer)
          coveredItemStateList.add(it)
        }
      }
      return OverlapChangeTrigger.OnDisposable {
        findOnDisposables.fastForEach { it.onDispose() }
        coveredItemStateList.fastForEach {
          it.removeShowRangeTransformer(transformer)
        }
      }
    }
  }


  fun interface OverlapChangeTrigger {

    /**
     * 监听 item 重叠数据的变化
     */
    fun onChangeOverlap(overlap: CourseItemOverlap): OnDisposable

    fun interface OnDisposable {
      fun onDispose()
    }
  }

  fun interface ShowRangeTransformer {
    fun transform(
      prevShow: List<CourseItemRange>,
      overlap: CourseItemOverlap,
    ): List<CourseItemRange>
  }

  // 用于以 Key-Value 的形式保存数据
  interface DataStore<T>
}