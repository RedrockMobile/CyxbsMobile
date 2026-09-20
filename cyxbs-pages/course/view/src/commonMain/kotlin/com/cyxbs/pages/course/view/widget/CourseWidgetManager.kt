package com.cyxbs.pages.course.view.widget

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.decoration.CourseWidgetSnapshotContributor
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseWidgetRenderProvider
import com.cyxbs.pages.course.view.timeline.data.FixedTimelineData
import com.cyxbs.pages.course.view.timeline.data.LessonTimelineData
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineMark
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * 负责从课表绘制状态生成并发布桌面小组件快照。
 *
 * 该类只读取 [courseFrame] 和 [decorations] 已经完成的布局、层级及重叠结果，不参与 Decoration
 * 的挂载、刷新和生命周期调度；发布失败也不会反向影响课表刷新。
 */
internal class CourseWidgetManager(
  private val courseFrame: AbstractCourseFrame,
  private val decorations: List<CoursePageDecoration<*>>,
  private val coroutineScope: CoroutineScope,
  private val snapshotPublisher: suspend (CourseWidgetSnapshot) -> Unit,
) : AutoCloseable {

  private companion object {
    /** 单次 Widget 跳转最多携带的重叠项数量，避免 Intent 参数过长。 */
    const val MAX_OVERLAP_ITEM_COUNT = 16
    const val DEFAULT_TIMELINE_BEGIN_MINUTE = 8 * 60
    const val DEFAULT_TIMELINE_END_MINUTE = 22 * 60 + 30
    const val TIMELINE_MARK_INTERVAL_MINUTE = 2 * 60
    // 与 RoundedShadowItemModifier 使用的 AppColor.topBg / AppDarkColor.topBg 保持一致。
    const val LIGHT_CONTAINER_ARGB = 0xFFFFFFFFL
    const val DARK_CONTAINER_ARGB = 0xFF2D2D2DL
  }

  private var snapshotPublishJob: Job? = null
  private val beginDatePublishJob: Job

  init {
    // 即使首次所有 Decoration 都是空列表，也要覆盖旧账号或旧学期的小组件快照。
    requestSnapshotPublish()
    // 开学日期变化只影响快照元数据，不一定触发 Item 刷新，因此需要独立发布。
    beginDatePublishJob = courseFrame.beginDate
      .onEach { requestSnapshotPublish() }
      .launchIn(coroutineScope)
  }

  /**
   * 等待同一批课表刷新稳定后再发布一次快照。
   *
   * 重复调用会取消上一次待发布任务，避免把不同层级的中间裁剪状态持久化给小组件。
   */
  fun requestSnapshotPublish() {
    snapshotPublishJob?.cancel()
    snapshotPublishJob = coroutineScope.launch {
      delay(200.milliseconds)
      try {
        snapshotPublisher(createSnapshot())
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Throwable) {
        // 小组件持久化失败不得影响主页课表；后续数据变化会再次触发发布。
      }
    }
  }

  /** 取消尚未完成的发布以及开学日期监听；调用方仍负责取消传入的协程作用域。 */
  override fun close() {
    snapshotPublishJob?.cancel()
    snapshotPublishJob = null
    beginDatePublishJob.cancel()
  }

  /**
   * 汇总所有 Item，连同绘制层和最终重叠裁剪结果导出完整的 1..maxWeek 快照。
   *
   * 完全覆盖项仍会保留，供小组件在可见轨道不足时绘制叠卡；这里只接受实现
   * [CourseWidgetRenderProvider] 的 Item，因此不感知课程、关联人或日程等业务类型。
   */
  private fun createSnapshot(): CourseWidgetSnapshot {
    val weeks = (1..courseFrame.maxWeek).map { week ->
      val hierarchyItems = DayOfWeek.entries.flatMap { day ->
        decorations.flatMapIndexed { renderLayer, decoration ->
          decoration.itemHierarchy.observe(week, day).value.mapNotNull { itemState ->
            val provider = itemState.item as? CourseWidgetRenderProvider ?: return@mapNotNull null
            val visibleRanges = itemState.overlap?.showRangeList.orEmpty().map { range ->
              CourseWidgetVisibleRange(
                beginMinute = range.first.minuteOfDay,
                endMinute = range.second.minuteOfDay,
                beginRatio = courseFrame.timeline.calculateInitialWeightRatio(range.first),
                endRatio = courseFrame.timeline.calculateInitialWeightRatio(range.second),
              )
            }
            provider.createWidgetRenderItem(itemState, visibleRanges)?.let { renderItem ->
              val fixed = itemState.item.whatTime.now.value
              renderItem.withContainer(renderLayer).copy(
                // 使用稳定初始权重，不能把用户临时展开时间段后的比例持久化。
                beginRatio = courseFrame.timeline.calculateInitialWeightRatio(fixed.beginTime),
                endRatio = courseFrame.timeline.calculateInitialWeightRatio(fixed.finalTime),
                action = renderItem.action.copy(
                  overlapItemIds = collectOverlapItemIds(itemState, provider.widgetItemId),
                ),
              )
            }
          }
        }
      }
      val contributedItems = decorations.flatMapIndexed { renderLayer, decoration ->
        (decoration as? CourseWidgetSnapshotContributor)
          ?.createWidgetSnapshotItems(week)
          .orEmpty()
          .map { it.withContainer(renderLayer) }
      }
      CourseWidgetWeekSnapshot(
        week = week,
        items = hierarchyItems + contributedItems,
      )
    }
    val timelineRange = TimelineRange(
      beginMinute = DEFAULT_TIMELINE_BEGIN_MINUTE,
      endMinute = DEFAULT_TIMELINE_END_MINUTE,
    )
    return CourseWidgetSnapshot(
      generatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
      firstWeekBeginEpochDays = courseFrame.beginDate.value?.weekBeginDate?.toEpochDays(),
      maxWeek = courseFrame.maxWeek,
      timelineBeginMinute = timelineRange.beginMinute,
      timelineEndMinute = timelineRange.endMinute,
      timelineMarks = createTimelineMarks(timelineRange),
      oversizedTimelineSections = createOversizedTimelineSections(),
      weeks = weeks,
    )
  }

  /** 补齐课表 Item 共用的圆角底卡颜色，并保留 Decoration 决定的绘制层级。 */
  private fun CourseWidgetRenderItem.withContainer(renderLayer: Int) = copy(
    renderLayer = renderLayer,
    lightStyle = lightStyle.copy(containerArgb = LIGHT_CONTAINER_ARGB),
    darkStyle = darkStyle.copy(containerArgb = DARK_CONTAINER_ARGB),
  )

  /** 在线性时间轴上生成等距双小时刻度，小组件只消费文字和比例。 */
  private fun createTimelineMarks(range: TimelineRange): List<CourseWidgetTimelineMark> =
    (range.beginMinute until range.endMinute step TIMELINE_MARK_INTERVAL_MINUTE).map { minute ->
      CourseWidgetTimelineMark(
        label = (minute / 60).toString(),
        ratio = (minute - range.beginMinute).toFloat() / (range.endMinute - range.beginMinute),
      )
    }

  /**
   * 把课表时间轴投影为大号小组件使用的纵向分段。
   *
   * 原始分段的起止时间与权重会完整保留，使小组件无需理解早晨、午间或课节等业务类型。
   */
  private fun createOversizedTimelineSections(): List<CourseWidgetTimelineSection> =
    courseFrame.timeline.data.mapIndexed { index, section ->
      when (section) {
        is MutableTimelineData -> CourseWidgetTimelineSection(
          id = "mutable-${index}-${section.startTime.minuteOfDay}-${section.endTime.minuteOfDay}",
          collapsedLabel = section.text,
          expandedLabel = section.optionText,
          beginMinute = section.startTime.minuteOfDay,
          endMinute = section.endTime.minuteOfDay,
          collapsedWeight = section.initialWeight,
          expandedWeight = section.maxWeight,
          expandable = true,
        )

        is LessonTimelineData -> CourseWidgetTimelineSection(
          id = "lesson-${section.lesson}",
          collapsedLabel = section.lesson.toString(),
          expandedLabel = section.optionText,
          beginMinute = section.startTime.minuteOfDay,
          endMinute = section.endTime.minuteOfDay,
          collapsedWeight = section.initialWeight,
          expandedWeight = section.initialWeight,
          expandable = false,
        )

        is FixedTimelineData -> CourseWidgetTimelineSection(
          id = "fixed-${index}-${section.startTime.minuteOfDay}-${section.endTime.minuteOfDay}",
          collapsedLabel = section.text,
          expandedLabel = section.optionText,
          beginMinute = section.startTime.minuteOfDay,
          endMinute = section.endTime.minuteOfDay,
          collapsedWeight = section.initialWeight,
          expandedWeight = section.initialWeight,
          expandable = false,
        )
      }
    }

  /**
   * 递归收集与根条目实际时间相交的覆盖条目 ID。
   *
   * 保留重叠结果的遍历顺序并限制总量，避免小组件点击参数无限膨胀。
   */
  private fun collectOverlapItemIds(
    rootItemState: CourseItemState,
    rootItemId: String,
  ): List<String> {
    val ids = LinkedHashSet<String>()
    val rootFixed = rootItemState.item.whatTime.now.value

    fun collect(overlap: com.cyxbs.pages.course.view.overlay.OverlapResult) {
      overlap.coveredItemList.forEach { cover ->
        val state = cover.result.itemState
        val fixed = state.item.whatTime.now.value
        if (fixed.beginTime < rootFixed.finalTime && fixed.finalTime > rootFixed.beginTime) {
          (state.item as? CourseWidgetRenderProvider)?.widgetDialogItemId?.let(ids::add)
        }
        if (ids.size < MAX_OVERLAP_ITEM_COUNT) collect(cover.result)
      }
    }

    rootItemState.overlap?.let(::collect)
    ids.remove(rootItemId)
    return ids.take(MAX_OVERLAP_ITEM_COUNT)
  }

  /** 普通小组件线性时间轴范围；结束分钟保持开区间语义。 */
  private data class TimelineRange(
    val beginMinute: Int,
    val endMinute: Int,
  )
}
