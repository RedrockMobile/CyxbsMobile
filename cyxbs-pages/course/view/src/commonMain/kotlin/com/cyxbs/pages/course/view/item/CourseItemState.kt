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
 * .
 *
 * @author 985892345
 * @date 2025/5/4
 */
@Stable
class CourseItemState(
  timeline: CourseTimeline,
  overlap: CourseItemOverlap,
) {

  val item = overlap.item

  var timeline by mutableStateOf(timeline)
    private set

  var overlap by mutableStateOf(overlap)
    private set

  var realShowRange: List<CourseItemRange> by mutableStateOf(overlap.showRangeList)
    private set

  fun update(timeline: CourseTimeline, overlap: CourseItemOverlap) {
    this.timeline = timeline
    this.overlap = overlap
    Snapshot.withoutReadObservation {
      // 调用 overlap 触发器
      overlapChangeTriggers = overlapChangeTriggers.mapValues {
        it.value.onDispose()
        it.key.onChangeOverlap(overlap)
      } as LinkedHashMap<OverlapChangeTrigger, OverlapChangeTrigger.OnDisposable>
      // 调用 showRange 转换器
      realShowRange =
        showRangeTransformers.fold<ShowRangeTransformer, List<CourseItemRange>>(overlap.showRangeList) { prev, interceptor ->
          interceptor.transform(prev, overlap)
        }
    }
  }

  private val showRangeTransformers = linkedSetOf<ShowRangeTransformer>()

  // showRange 转换器，用于将 overlap 原始的 showRangeList 进行转换，最后赋值给 realShowRange
  // 在每次 overlap 更新时触发转换
  fun addShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.add(interceptor)) {
      realShowRange = interceptor.transform(realShowRange, overlap)
    }
  }

  fun removeShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.remove(interceptor)) {
      // 移除后使用转换器重新计算 realShowRange
      realShowRange =
        showRangeTransformers.fold<ShowRangeTransformer, List<CourseItemRange>>(overlap.showRangeList) { prev, now ->
          now.transform(prev, overlap)
        }
    }
  }

  private var overlapChangeTriggers = linkedMapOf<OverlapChangeTrigger, OverlapChangeTrigger.OnDisposable>()

  // overlap 更新触发器，监听 overlap 的更新用于触发一些特定的操作
  // 在每次 overlap 更新时触发
  fun addOverlapChangeTrigger(trigger: OverlapChangeTrigger) {
    val onDispose = trigger.onChangeOverlap(overlap)
    overlapChangeTriggers.put(trigger, onDispose)?.onDispose()
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
        pageContext.findItemState(cover.data.item) {
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
}