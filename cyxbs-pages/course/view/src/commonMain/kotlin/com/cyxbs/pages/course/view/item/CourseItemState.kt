package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.modifier.LongPressMoveState
import com.cyxbs.pages.course.view.overlay.OverlapResult
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [CourseItem] 对应的 状态，生命周期与页面进行绑定
 *
 * @author 985892345
 * @date 2025/5/4
 */
@Stable
class CourseItemState(
  val item: CourseItem,
) {

  // coursePage 页面上下文
  // 使用 Flow 明确表示他可能会发生改变
  private val _coursePageFlow: MutableStateFlow<LocalCoursePageContext?> = MutableStateFlow(null)
  val coursePageFlow: StateFlow<LocalCoursePageContext?> = _coursePageFlow

  // item 重叠数据
  var overlap: OverlapResult? by mutableStateOf(null)
    private set

  // item 真实展示区间
  // 通过转换器最后才能确定
  var realShowRange: List<MinuteTimePair> by mutableStateOf(this.overlap?.showRangeList ?: emptyList())
    private set

  // 提供给一些场景设置 item 的层级
  val zIndexState = mutableFloatStateOf(0F)

  // item 节点坐标系
  // 如果 item 被重叠完全遮挡，则可能并不存在 layoutCoordinates，或者 layoutCoordinates.isAttached = false
  // 使用 SharedFlow 是为了不去重，因为每次 item 位置改变都会回调，但 layoutCoordinates 对象不变
  val layoutCoordinates: MutableSharedFlow<LayoutCoordinates> = MutableSharedFlow(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  // 长按移动状态
  val longPressMoveState = MutableStateFlow<LongPressMoveState>(LongPressMoveState.Idle)

  fun updateCoursePage(coursePage: LocalCoursePageContext?) {
    _coursePageFlow.value = coursePage
  }

  fun updateOverlap(overlap: OverlapResult) {
    this.overlap = overlap
    // 调用 overlap 触发器
    overlapChangeTriggers = overlapChangeTriggers.mapValuesTo(LinkedHashMap()) {
      it.value.onDispose()
      it.key.onChangeOverlap(overlap)
    }
    // 调用 showRange 转换器
    val initialList: List<MinuteTimePair> = overlap.showRangeList
    realShowRange = showRangeTransformers.fold(initialList) { prev, interceptor ->
      interceptor.transform(prev, overlap)
    }
  }

  private val showRangeTransformers = linkedSetOf<ShowRangeTransformer>()

  // showRange 转换器，用于将 overlap 原始的 showRangeList 进行转换，最后赋值给 realShowRange
  // 在每次 overlap 更新时触发转换
  fun addShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.add(interceptor)) {
      val overlap = Snapshot.withoutReadObservation { overlap } ?: return
      realShowRange = Snapshot.withoutReadObservation {
        interceptor.transform(realShowRange, overlap)
      }
    }
  }

  fun removeShowRangeTransformer(interceptor: ShowRangeTransformer) {
    if (showRangeTransformers.remove(interceptor)) {
      val overlap = Snapshot.withoutReadObservation { overlap } ?: return
      // 移除后使用转换器重新计算 realShowRange
      realShowRange = Snapshot.withoutReadObservation {
        val initialList: List<MinuteTimePair> = overlap.showRangeList
        showRangeTransformers.fold(initialList) { prev, now ->
          now.transform(prev, overlap)
        }
      }
    }
  }

  private var overlapChangeTriggers =
    linkedMapOf<OverlapChangeTrigger, OverlapChangeTrigger.OnDisposable>()

  // overlap 更新触发器，监听 overlap 的更新用于触发一些特定的操作
  fun addOverlapChangeTrigger(trigger: OverlapChangeTrigger) {
    overlapChangeTriggers[trigger]?.onDispose()
    val overlap = Snapshot.withoutReadObservation { overlap } ?: return
    overlapChangeTriggers[trigger] = trigger.onChangeOverlap(overlap)
  }

  fun removeOverlapChangeTrigger(trigger: OverlapChangeTrigger) {
    overlapChangeTriggers.remove(trigger)?.onDispose()
  }


  /**
   * 配合 [OverlapChangeTrigger]、[ShowRangeTransformer] 给被覆盖 item 添加 showRange 监听器的快捷方式
   */
  class CoveredItemShowRangeTransformerTrigger(
    val transformer: ShowRangeTransformer,
  ) : OverlapChangeTrigger {
    override fun onChangeOverlap(overlap: OverlapResult): OverlapChangeTrigger.OnDisposable {
      val coveredItemStateList = mutableListOf<CourseItemState>()
      overlap.coveredItemList.fastForEach { cover ->
        val itemState = cover.result.itemState
        // 找到被覆盖的 item 添加 showRange 转换器
        itemState.addShowRangeTransformer(transformer)
        coveredItemStateList.add(itemState)
      }
      return OverlapChangeTrigger.OnDisposable {
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
    fun onChangeOverlap(overlap: OverlapResult): OnDisposable

    fun interface OnDisposable {
      fun onDispose()
    }
  }

  fun interface ShowRangeTransformer {
    fun transform(
      prevShow: List<MinuteTimePair>,
      overlap: OverlapResult,
    ): List<MinuteTimePair>
  }
}