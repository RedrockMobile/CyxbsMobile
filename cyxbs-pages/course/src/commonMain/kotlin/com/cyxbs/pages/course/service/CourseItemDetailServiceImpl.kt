package com.cyxbs.pages.course.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.course.api.CourseItemDetailRequest
import com.cyxbs.pages.course.api.ICourseItemDetailService
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.api.courseItemDetailId
import com.cyxbs.pages.course.api.scheduleCourseItemDetailId
import com.cyxbs.pages.course.home.dialog.LessonBottomSheetDialog
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetContent
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetDialog
import com.cyxbs.pages.course.view.dialog.CourseBottomSheetScope
import com.cyxbs.pages.course.view.dialog.rememberCourseBottomSheetDialogState
import com.cyxbs.pages.schedule.api.IScheduleOccurrenceService
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.seconds

/**
 * 课程详情的外部入口实现。
 *
 * 该服务只接收不透明 ID，并在每次打开时从课程缓存和 Schedule 本地数据流重新解析；Widget 快照中的
 * 标题、地点和时间不会进入详情内容，从而避免桌面缓存过期后展示旧数据。
 */
@ImplProvider(clazz = ICourseItemDetailService::class)
object CourseItemDetailServiceImpl : ICourseItemDetailService {

  /** 冷启动时等待账号和关联人缓存恢复，避免把初始化中的空列表误判为条目已删除。 */
  private val ResolveGracePeriod = 1.seconds

  private val accountService = IAccountService::class.impl()
  private val lessonService = ILessonService2::class.impl()
  private val linkService = ILinkService2::class.impl()
  private val scheduleService = IScheduleOccurrenceService::class.impl()

  /** 查询请求中的根条目与重叠条目，并复用课表当前的统一 BottomSheet 宿主。 */
  @Composable
  override fun CourseItemDetailDialog(
    request: CourseItemDetailRequest,
    onDismiss: () -> Unit,
  ) {
    val contents by remember(request) {
      observeContents(request)
    }.collectAsState(initial = null)
    val state = rememberCourseBottomSheetDialogState(onDismissed = onDismiss)

    LaunchedEffect(contents) {
      val current = contents ?: return@LaunchedEffect
      if (current.isEmpty()) {
        // linkService.state 与账号服务在冷启动时可能先发布空状态；新数据到达会取消本次 Effect。
        delay(ResolveGracePeriod)
        onDismiss()
      } else {
        state.showDialog(current)
      }
    }
    CourseBottomSheetDialog(state)
  }

  /** 合并本人课程、关联课程和指定教学周的日程，并严格按请求 ID 顺序生成 Pager 内容。 */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeContents(
    request: CourseItemDetailRequest,
  ): Flow<List<CourseBottomSheetContent>> {
    val selfLessons = accountService.stuNumFlow.flatMapLatest(::observeLessons)
    val linkedLessons = linkService.state.flatMapLatest { link ->
      observeLessons(link.linkNum.takeIf { link.isNotNull() })
    }
    val schedules = SchoolCalendar.observeFirstMonDayNullable().flatMapLatest { firstMonday ->
      if (firstMonday == null || request.week < 1) {
        flowOf(emptyList())
      } else {
        val weekBegin = firstMonday.plusWeeks(request.week - 1)
        scheduleService.observeLinkedOccurrencesInRange(
          startInclusive = MinuteTimeDate(weekBegin, hour = 0, minute = 0),
          endExclusive = MinuteTimeDate(weekBegin.plusWeeks(1), hour = 0, minute = 0),
        )
      }
    }
    return combine(selfLessons, linkedLessons, schedules) { self, linked, schedule ->
      resolveContents(
        request = request,
        selfLessons = self,
        linkedLessons = linked,
        schedules = schedule,
      )
    }.distinctUntilChanged()
  }

  /**
   * 观察指定学号课程；无账号时立即提供空列表，有账号时先发磁盘缓存再接收后续实时刷新。
   */
  private fun observeLessons(stuNum: String?): Flow<List<LessonByWeeks>> {
    val validStuNum = stuNum?.takeIf(String::isNotBlank) ?: return flowOf(emptyList())
    val cached = lessonService.getCacheLesson(validStuNum)?.data.orEmpty()
    return lessonService.observeLesson(validStuNum)
      .onStart { emit(cached) }
      .distinctUntilChanged()
  }

  /** 将三个实时数据源投影为入口可展示内容；找不到的过期 ID 会被忽略。 */
  private fun resolveContents(
    request: CourseItemDetailRequest,
    selfLessons: List<LessonByWeeks>,
    linkedLessons: List<LessonByWeeks>,
    schedules: List<ScheduleOccurrenceView>,
  ): List<CourseBottomSheetContent> {
    val contentById = buildMap<String, CourseBottomSheetContent> {
      selfLessons.asSequence()
        .filter { request.week in it.week }
        .forEach { lesson ->
          val id = lesson.courseItemDetailId(isLinked = false)
          put(id, LessonDetailContent(id, lesson, isLinked = false))
        }
      linkedLessons.asSequence()
        .filter { request.week in it.week }
        .forEach { lesson ->
          val id = lesson.courseItemDetailId(isLinked = true)
          put(id, LessonDetailContent(id, lesson, isLinked = true))
        }
      schedules.forEach { occurrence ->
        val id = scheduleCourseItemDetailId(occurrence.identity)
        put(id, ScheduleDetailContent(id, occurrence))
      }
    }
    val root = contentById[request.itemId] ?: return emptyList()
    return buildList {
      val seen = HashSet<String>()
      add(root)
      seen.add(request.itemId)
      request.overlapItemIds.asSequence().forEach { id ->
        if (seen.add(id)) contentById[id]?.let(::add)
      }
    }
  }

  /** 课程详情内容；本人课程和关联课程共用既有设计，仅控制关联入口图标。 */
  private data class LessonDetailContent(
    override val contentKey: String,
    val lesson: LessonByWeeks,
    val isLinked: Boolean,
  ) : CourseBottomSheetContent {

    @Composable
    override fun CourseBottomSheetDialogContent(scope: CourseBottomSheetScope) {
      LessonBottomSheetDialog(lesson = lesson, enableShowLinkIcon = isLinked)
    }
  }

  /** 日程详情内容；编辑、关闭拦截和顶层确认弹窗均通过最小宿主能力回传。 */
  private data class ScheduleDetailContent(
    override val contentKey: String,
    val occurrence: ScheduleOccurrenceView,
  ) : CourseBottomSheetContent {

    @Composable
    override fun CourseBottomSheetDialogContent(scope: CourseBottomSheetScope) {
      scheduleService.ScheduleDetailContent(
        occurrence = occurrence,
        embeddedInHost = true,
        onDismiss = scope::dismissDialogAnimated,
        onEditModeChanged = { isEditing ->
          if (isEditing) scope.lockCurrentPage()
        },
        onDismissRequestChanged = scope::updateDismissRequestGate,
        onWindowOverlayContentChanged = scope::updateWindowOverlayContent,
      )
    }
  }
}
