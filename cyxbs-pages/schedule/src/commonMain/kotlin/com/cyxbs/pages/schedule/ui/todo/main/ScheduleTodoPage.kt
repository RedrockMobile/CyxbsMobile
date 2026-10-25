package com.cyxbs.pages.schedule.ui.todo.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.calendar.CalendarCompose
import com.cyxbs.components.view.calendar.layout.createCalendarContentOffsetMeasurePolicy
import com.cyxbs.components.view.calendar.state.rememberCalendarState
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.api.ScheduleMainNavigationRequests
import com.cyxbs.pages.schedule.data.failure.ScheduleFailureRecords
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.ui.calendar.rememberScheduleCalendarMarkedDates
import com.cyxbs.pages.schedule.ui.category.ScheduleCategoryManageNavArgument
import com.cyxbs.pages.schedule.ui.category.mergeScheduleCategories
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.main.isScheduleMainEditorEnabled
import com.cyxbs.pages.schedule.ui.model.occurrenceByIdentity
import com.cyxbs.pages.schedule.ui.model.occurrencesInRange
import com.cyxbs.pages.schedule.ui.settings.ScheduleSettingsNavArgument
import com.cyxbs.pages.schedule.ui.timeline.HourHeight
import com.cyxbs.pages.schedule.ui.timeline.ScheduleTimelinePane
import com.cyxbs.pages.schedule.ui.timeline.timelineSchedulesForDate
import com.cyxbs.pages.schedule.ui.todo.failure.ScheduleFailureNavArgument
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val TODO_DEFAULT_DURATION_MINUTES = 60
private const val TODO_MIN_INTERVAL_MINUTES = 10
private const val TODO_LAST_MINUTE_OF_DAY = 23 * 60 + 59

/**
 * 日程主页内容。
 *
 * 页面把共享 Schedule 快照投影成卡片列表或当天时间轴，两种展示共用标题栏、分组筛选和编辑会话。
 * 新增、编辑、完成和删除仍通过 [ScheduleMainViewModel] 进入同一个仓库，因此切换视图不会创建第二份
 * 数据状态。[onBack] 默认绑定正式主页导航参数；Desktop mock 使用独立参数类型时会显式传入自己的
 * 返回回调。
 */
@Composable
fun ScheduleTodoPage(
  argument: ScheduleMainNavArgument,
  viewModel: ScheduleMainViewModel,
  onBack: () -> Unit = argument::popBackStack,
) {
  val colors = LocalAppColors.current
  val snapshot by viewModel.snapshot.collectAsState()
  val manageMode by viewModel.isManageMode.collectAsState()
  val selectedIds by viewModel.selectedIds.collectAsState()
  val editorEnabled = isScheduleMainEditorEnabled(viewModel.mutationMode)
  val viewerTimeZone = remember { TimeZone.currentSystemDefault() }
  val projection = remember(snapshot, viewerTimeZone) {
    projectScheduleTodo(snapshot, Clock.System.now(), viewerTimeZone)
  }
  val currentAccountSettings = accountSettings
  val failureRecordsFlow = remember(currentAccountSettings.stuNum) {
    currentAccountSettings.stuNum?.let(ScheduleFailureRecords::observe)
      ?: MutableStateFlow(emptyList())
  }
  val failureRecords by failureRecordsFlow.collectAsState()
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val visibleCategories = remember(snapshot.categories) {
    mergeScheduleCategories(snapshot.categories)
  }
  val calendarState = rememberCalendarState(
    initialClickDate = TodayNoEffect,
    endDate = TodayNoEffect.plusYears(8).lastDate,
  )
  val clickDate = calendarState.clickDate

  var showCreateEditor by remember { mutableStateOf(false) }
  var createTiming by remember { mutableStateOf<ScheduleTiming.Timed?>(null) }
  var editingIdentity by remember { mutableStateOf<Pair<ScheduleId, RecurrenceId?>?>(null) }
  var timelineEditingIdentity by remember {
    mutableStateOf<Pair<ScheduleId, RecurrenceId?>?>(null)
  }
  var selectedCategoryId by remember(currentAccountSettings.stuNum) {
    mutableStateOf<CategoryId?>(null)
  }
  var viewMode by remember(currentAccountSettings.stuNum) {
    mutableStateOf(loadScheduleTodoViewMode(currentAccountSettings))
  }
  // 置顶只保存在当前账号 Settings，不进入 Schedule 协议；切号后 remember 会加载对应账号的数据。
  var pinnedIds by remember(currentAccountSettings.stuNum) {
    mutableStateOf(loadScheduleTodoPinnedIds(currentAccountSettings))
  }
  val calendarLinkedScheduleIds = remember(snapshot.schedules) {
    snapshot.schedules.filter { it.linkedToCourse }.mapTo(mutableSetOf()) { it.id }
  }
  var deepLinkConsumed by remember(argument.scheduleId, argument.recurrenceId) {
    mutableStateOf(false)
  }
  var highlightedItemKey by remember(argument.scheduleId, argument.recurrenceId) {
    mutableStateOf<String?>(null)
  }
  var deepLinkReplayVersion by remember(argument.scheduleId, argument.recurrenceId) {
    mutableIntStateOf(0)
  }

  // 批量管理是页面内临时状态：系统返回、返回手势与 ESC 应先收起管理态，不能直接退出清单页。
  val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
  NavigationBackHandler(
    state = backEventState,
    isBackEnabled = manageMode,
    onBackCompleted = viewModel::exitManageMode,
  )
  val handleBack: () -> Unit = {
    if (manageMode) viewModel.exitManageMode() else onBack()
  }

  LaunchedEffect(Unit) {
    viewModel.initialize()
  }
  LaunchedEffect(argument) {
    ScheduleMainNavigationRequests.requests.collect { request ->
      if (request == argument) {
        // 相同 deeplink 不会重复压栈；重置消费态后复用下方定位与高亮流程。
        highlightedItemKey = null
        deepLinkConsumed = false
        deepLinkReplayVersion++
      }
    }
  }
  LaunchedEffect(editorEnabled) {
    if (!editorEnabled) {
      showCreateEditor = false
      editingIdentity = null
      timelineEditingIdentity = null
      viewModel.exitManageMode()
    }
  }
  LaunchedEffect(
    snapshot.accountId,
    snapshot.status,
    snapshot.schedules,
    currentAccountSettings.stuNum,
    pinnedIds,
  ) {
    // 初始化前的空快照不能用于清理；切号过渡期也只允许匹配账号的可信快照修改 Settings。
    if (snapshot.status is ScheduleRepositoryStatus.Loading ||
      snapshot.status is ScheduleRepositoryStatus.Corrupted ||
      snapshot.accountId != currentAccountSettings.stuNum
    ) return@LaunchedEffect
    val existingIds = snapshot.schedules.mapTo(hashSetOf()) { it.id }
    val retainedIds = pinnedIds.filter { it in existingIds }
    if (retainedIds != pinnedIds) {
      pinnedIds = retainedIds
      saveScheduleTodoPinnedIds(currentAccountSettings, retainedIds)
    }
  }
  LaunchedEffect(visibleCategories, selectedCategoryId) {
    // 分类可能被远端删除；失效筛选自动回到“全部”，避免页面看起来像数据被清空。
    if (selectedCategoryId != null && visibleCategories.none { it.id == selectedCategoryId }) {
      selectedCategoryId = null
    }
  }
  val editingItem = remember(projection, editingIdentity) {
    val identity = editingIdentity
    if (identity == null) null else (projection.pending + projection.completed).firstOrNull {
      it.schedule.id == identity.first && it.occurrence.recurrenceId == identity.second
    }
  }
  LaunchedEffect(editingIdentity, editingItem) {
    // 条目被删除或同步结果使其不再可见时关闭编辑器，避免同 identity 将来重建后意外重新弹出。
    if (editingIdentity != null && editingItem == null) editingIdentity = null
  }
  val timelineEditingOccurrence = remember(snapshot, timelineEditingIdentity) {
    timelineEditingIdentity?.let { identity ->
      snapshot.occurrenceByIdentity(identity.first, identity.second)
    }
  }
  val timelineEditingSchedule = remember(snapshot.schedules, timelineEditingIdentity) {
    timelineEditingIdentity?.let { identity ->
      snapshot.schedules.firstOrNull { it.id == identity.first }
    }
  }
  LaunchedEffect(timelineEditingIdentity, timelineEditingOccurrence, timelineEditingSchedule) {
    // 删除系列或取消所点实例后及时关闭；普通更新则由 identity 重新解析，保持详情内容实时刷新。
    if (timelineEditingIdentity != null &&
      (timelineEditingOccurrence == null || timelineEditingSchedule == null)
    ) {
      timelineEditingIdentity = null
    }
  }
  val filteredPending = remember(projection.pending, selectedCategoryId) {
    projection.pending.filter { selectedCategoryId == null || it.schedule.categoryId == selectedCategoryId }
  }
  val filteredCompleted = remember(projection.completed, selectedCategoryId) {
    projection.completed.filter { selectedCategoryId == null || it.schedule.categoryId == selectedCategoryId }
  }
  val pending = remember(filteredPending, pinnedIds) {
    sortScheduleTodoPending(filteredPending, pinnedIds)
  }
  // 已完成列表按完成事实排序，不再让端上置顶干预历史顺序。
  val completed = filteredCompleted
  val timelineVisibleOccurrences = remember(snapshot, clickDate, selectedCategoryId) {
    snapshot.occurrencesInRange(
      MinuteTimeDate(clickDate, 0, 0),
      MinuteTimeDate(clickDate.plusDays(1), 0, 0),
    ).filter { occurrence ->
      selectedCategoryId == null || occurrence.categoryId == selectedCategoryId
    }
  }
  val timelineDayEvents = remember(timelineVisibleOccurrences, clickDate) {
    timelineSchedulesForDate(timelineVisibleOccurrences, clickDate)
  }
  val timelineScrollState = rememberScrollState()
  val calendarMarkerSnapshot = remember(snapshot, selectedCategoryId) {
    if (selectedCategoryId == null) {
      snapshot
    } else {
      val schedules = snapshot.schedules.filter { it.categoryId == selectedCategoryId }
      val scheduleIds = schedules.mapTo(mutableSetOf()) { it.id }
      snapshot.copy(
        schedules = schedules,
        occurrenceAdjustments = snapshot.occurrenceAdjustments.filter { it.scheduleId in scheduleIds },
      )
    }
  }
  // 分类筛选后只标记该分类实际可见的日程，避免点击带圆点日期后时间轴却为空。
  val calendarMarkedDates = rememberScheduleCalendarMarkedDates(calendarMarkerSnapshot, calendarState)
  val density = LocalDensity.current
  LaunchedEffect(Unit) {
    val currentHour = Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val target = with(density) { (HourHeight * (currentHour - 1).coerceAtLeast(0)).toPx() }
    timelineScrollState.scrollTo(target.toInt())
  }
  LaunchedEffect(
    argument.scheduleId,
    argument.recurrenceId,
    selectedCategoryId,
    pending,
    completed,
    snapshot.schedules,
    deepLinkReplayVersion,
  ) {
    if (deepLinkConsumed) return@LaunchedEffect
    val scheduleId = argument.scheduleId ?: return@LaunchedEffect
    if (selectedCategoryId != null) {
      // Feed 入口必须能看到目标；先回到“全部”，下一次组合再按最终排序计算列表索引。
      selectedCategoryId = null
      return@LaunchedEffect
    }
    fun ScheduleTodoItemUi.matchesTarget(): Boolean =
      schedule.id == scheduleId &&
          (argument.recurrenceId == null || occurrence.recurrenceId == argument.recurrenceId)

    val pendingIndex = pending.indexOfFirst(ScheduleTodoItemUi::matchesTarget)
    val completedIndex = completed.indexOfFirst(ScheduleTodoItemUi::matchesTarget)
    val pendingEntryCount = if (pending.isEmpty()) 1 else pending.size
    val target = when {
      pendingIndex >= 0 -> pending[pendingIndex] to (1 + pendingIndex)
      completedIndex >= 0 -> completed[completedIndex] to (pendingEntryCount + 2 + completedIndex)
      else -> null
    }
    if (target != null) {
      val (item, absoluteIndex) = target
      highlightedItemKey = item.key
      deepLinkConsumed = true
      // 保留目标前一行作为分区上下文；首张未完成事项同时保留“未完成”标题。
      listState.scrollToItem((absoluteIndex - 1).coerceAtLeast(0))
      delay(730)
      if (highlightedItemKey == item.key) highlightedItemKey = null
    } else if (snapshot.schedules.any { it.id == scheduleId }) {
      // 系列存在但该 occurrence 不在当前展示窗口时不循环尝试，保持普通清单页。
      deepLinkConsumed = true
    }
  }
  val visibleUrgentCount = remember(filteredPending) {
    filteredPending.count { it.isDueSoon || it.isOverdue }
  }
  var displayedUrgentCount by remember { mutableStateOf(visibleUrgentCount.coerceAtLeast(1)) }
  LaunchedEffect(visibleUrgentCount) {
    // 退场动画期间继续展示最后一个有效数量，避免文字先闪成“你有 0 项”再消失。
    if (visibleUrgentCount > 0) displayedUrgentCount = visibleUrgentCount
  }
  val urgentListTopPadding by animateDpAsState(
    targetValue = if (visibleUrgentCount > 0) 13.dp else 16.dp,
    animationSpec = tween(durationMillis = 220),
    label = "scheduleTodoUrgentListTopPadding",
  )

  /**
   * 将指定事项移动到置顶队列头部；重复点击同一事项也会把它提升为最新置顶项。
   *
   * [revealPendingTop] 只用于卡片侧滑操作。稳定 key 会让 LazyColumn 尽量保留原视口，主动滚动才能让
   * 用户立即看到事项已经移动到“未完成”区域顶部。
   */
  fun pinSchedules(scheduleIds: List<ScheduleId>, revealPendingTop: Boolean) {
    val orderedIds = scheduleIds.distinct()
    if (orderedIds.isEmpty()) return
    pinnedIds = orderedIds + pinnedIds.filterNot { it in orderedIds }
    saveScheduleTodoPinnedIds(currentAccountSettings, pinnedIds)
    if (revealPendingTop) {
      // 索引 0 是“未完成”标题；滚到标题即可同时露出新的首张置顶卡片，不能把分区标题滚出视口。
      coroutineScope.launch { listState.animateScrollToItem(0) }
    }
  }

  /** 切换单个事项的端上置顶状态；取消置顶只恢复普通排序，不改变日程业务数据。 */
  fun togglePinnedSchedule(scheduleId: ScheduleId, revealPendingTop: Boolean) {
    if (scheduleId in pinnedIds) {
      pinnedIds = pinnedIds.filterNot { it == scheduleId }
      saveScheduleTodoPinnedIds(currentAccountSettings, pinnedIds)
    } else {
      pinSchedules(listOf(scheduleId), revealPendingTop)
    }
  }

  /** 按系列切换持久化课表投射；重复系列的所有实例共用一次选择。 */
  fun toggleCalendarLink(scheduleId: ScheduleId) {
    viewModel.toggleCourseProjection(scheduleId) { selected ->
      toast(if (selected) "已关联到课表" else "已取消关联到课表")
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      // 背景绘制到系统导航栏后方；各底部交互组件分别消费安全区，避免底栏阴影落在导航按钮上。
      .background(colors.bottomBg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      ScheduleTodoHeader(
        manageMode = manageMode,
        failureCount = failureRecords.size,
        viewMode = viewMode,
        onBack = handleBack,
        onFailures = { ScheduleFailureNavArgument.navigate() },
        onToggleViewMode = {
          viewMode = if (viewMode == ScheduleTodoViewMode.LIST) {
            ScheduleTodoViewMode.TIMELINE
          } else {
            ScheduleTodoViewMode.LIST
          }
          saveScheduleTodoViewMode(currentAccountSettings, viewMode)
        },
        onSettings = { ScheduleSettingsNavArgument.navigate() },
        onManageDone = viewModel::exitManageMode,
      )
      ScheduleTodoSyncStatus(snapshot.status, viewModel.mutationMode)
      ScheduleTodoCategoryFilterBar(
        categories = visibleCategories,
        selectedCategoryId = selectedCategoryId,
        onSelect = { selectedCategoryId = it },
        onManageCategories = { ScheduleCategoryManageNavArgument.navigate() },
      )
      if (viewMode == ScheduleTodoViewMode.LIST) {
        AnimatedVisibility(
          visible = visibleUrgentCount > 0,
          enter = fadeIn(tween(durationMillis = 180)) + expandVertically(
            animationSpec = tween(durationMillis = 220),
            expandFrom = Alignment.Top,
          ),
          exit = fadeOut(tween(durationMillis = 160)) + shrinkVertically(
            animationSpec = tween(durationMillis = 220),
            shrinkTowards = Alignment.Top,
          ),
        ) {
          ScheduleTodoUrgentBanner(displayedUrgentCount)
        }
        LazyColumn(
          // 列表视口止于系统导航栏上方，滚动中的卡片不会绘制到导航按钮背后。
          modifier = Modifier.fillMaxSize().navigationBarsPadding(),
          state = listState,
          contentPadding = PaddingValues(
            start = 16.dp,
            // 提示条存在时，从其底部到“未完成”文字顶部保持设计稿的 17dp：13dp + 标题自身 4dp。
            top = urgentListTopPadding,
            end = 16.dp,
            bottom = if (manageMode) 92.dp else 88.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item(key = "pending-title") {
            ScheduleTodoSectionTitle(
              text = "未完成",
              allSelected = if (manageMode && pending.isNotEmpty()) {
                pending.all { it.schedule.id in selectedIds }
              } else null,
              onToggleSelectAll = {
                viewModel.toggleSelectSection(pending.map { it.schedule.id })
              },
            )
          }
          if (pending.isEmpty()) {
            item(key = "pending-empty") {
              ScheduleTodoEmptyCard(completed = false)
            }
          } else {
            items(pending, key = ScheduleTodoItemUi::key) { item ->
              ScheduleTodoCard(
                modifier = Modifier.animateItem(
                  fadeInSpec = tween(durationMillis = 180),
                  placementSpec = tween(durationMillis = 320),
                  fadeOutSpec = tween(durationMillis = 160),
                ),
                item = item,
                highlighted = item.key == highlightedItemKey,
                isPinned = item.schedule.id in pinnedIds,
                manageMode = manageMode,
                selected = item.schedule.id in selectedIds,
                onSelect = { viewModel.toggleSelect(item.schedule.id) },
                onLongPress = {
                  if (editorEnabled && !manageMode) {
                    viewModel.enterManageMode()
                    viewModel.toggleSelect(item.schedule.id)
                  }
                },
                onOpen = {
                  showCreateEditor = false
                  timelineEditingIdentity = null
                  editingIdentity = item.schedule.id to item.occurrence.recurrenceId
                },
                onComplete = {
                  viewModel.completeSchedule(
                    item.schedule.id,
                    item.occurrence.recurrenceId,
                    completed = true,
                  )
                },
                onTogglePin = {
                  togglePinnedSchedule(item.schedule.id, revealPendingTop = true)
                },
                isLinkedToCalendar = item.schedule.id in calendarLinkedScheduleIds,
                onToggleCalendarLink = { toggleCalendarLink(item.schedule.id) },
                onDelete = {
                  viewModel.deleteScheduleScoped(
                    item.schedule.id,
                    if (item.occurrence.recurrenceId == null) EditScope.ALL else EditScope.THIS_ONLY,
                    item.occurrence.recurrenceId,
                  )
                },
              )
            }
          }

          item(key = "completed-title") {
            ScheduleTodoSectionTitle(
              text = "已完成",
              allSelected = if (manageMode && completed.isNotEmpty()) {
                completed.all { it.schedule.id in selectedIds }
              } else null,
              onToggleSelectAll = {
                viewModel.toggleSelectSection(completed.map { it.schedule.id })
              },
            )
          }
          if (completed.isEmpty()) {
            item(key = "completed-empty") {
              ScheduleTodoEmptyCard(completed = true)
            }
          } else {
            items(completed, key = ScheduleTodoItemUi::key) { item ->
              ScheduleTodoCard(
                modifier = Modifier.animateItem(
                  fadeInSpec = tween(durationMillis = 180),
                  placementSpec = tween(durationMillis = 320),
                  fadeOutSpec = tween(durationMillis = 160),
                ),
                item = item,
                highlighted = item.key == highlightedItemKey,
                isPinned = item.schedule.id in pinnedIds,
                manageMode = manageMode,
                selected = item.schedule.id in selectedIds,
                onSelect = { viewModel.toggleSelect(item.schedule.id) },
                onLongPress = {
                  if (editorEnabled && !manageMode) {
                    viewModel.enterManageMode()
                    viewModel.toggleSelect(item.schedule.id)
                  }
                },
                onOpen = {
                  showCreateEditor = false
                  timelineEditingIdentity = null
                  editingIdentity = item.schedule.id to item.occurrence.recurrenceId
                },
                onComplete = {
                  viewModel.completeSchedule(
                    item.schedule.id,
                    item.occurrence.recurrenceId,
                    completed = false,
                  )
                },
                onTogglePin = {
                  togglePinnedSchedule(item.schedule.id, revealPendingTop = false)
                },
                isLinkedToCalendar = item.schedule.id in calendarLinkedScheduleIds,
                onToggleCalendarLink = { toggleCalendarLink(item.schedule.id) },
                onDelete = {
                  viewModel.deleteScheduleScoped(
                    item.schedule.id,
                    if (item.occurrence.recurrenceId == null) EditScope.ALL else EditScope.THIS_ONLY,
                    item.occurrence.recurrenceId,
                  )
                },
              )
            }
          }
        }
      } else {
        CalendarCompose(
          modifier = Modifier
            .weight(1f)
            .background(colors.bottomBg)
            .navigationBarsPadding(),
          state = calendarState,
          dateHasIndicator = { it in calendarMarkedDates },
        ) {
          ScheduleTimelinePane(
            modifier = Modifier.layout(calendarState.createCalendarContentOffsetMeasurePolicy()),
            timed = timelineDayEvents,
            categories = visibleCategories,
            scrollState = timelineScrollState,
            onScheduleClick = { occurrence ->
              if (!editorEnabled) return@ScheduleTimelinePane
              showCreateEditor = false
              editingIdentity = null
              timelineEditingIdentity = occurrence.scheduleId to occurrence.recurrenceId
            },
          )
        }
      }
    }

    if (editorEnabled && !manageMode) {
      FloatingActionButton(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(end = 26.dp, bottom = 54.dp)
          .size(50.dp),
        onClick = {
          editingIdentity = null
          timelineEditingIdentity = null
          // 每次点击时重新读取当前时间，避免页面驻留后仍使用首次组合时的旧时间。
          createTiming = defaultTodoCreationTiming(Clock.System.now(), viewerTimeZone)
          showCreateEditor = true
        },
        backgroundColor = ScheduleTodoAccentColor,
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = "新建事项",
          tint = ScheduleTodoAddIconColor,
          // Rounded.Add 的路径只占 24dp 画布中间 14dp，31dp 画布对应设计稿可见 18dp。
          modifier = Modifier.size(31.dp),
        )
      }
    }

    if (manageMode) {
      val selectedPinTargets = (pending + completed)
        .map { it.schedule.id }
        .distinct()
        .filter { it in selectedIds }
      val shouldUnpinSelected = selectedPinTargets.isNotEmpty() &&
          selectedPinTargets.all { it in pinnedIds }
      ScheduleTodoManageBar(
        selectedCount = selectedIds.size,
        totalCount = (pending + completed).map { it.schedule.id }.distinct().size,
        middleActionLabel = if (shouldUnpinSelected) "取消置顶" else "置顶",
        onSelectAll = {
          viewModel.selectAll((pending + completed).map { it.schedule.id }.distinct())
        },
        onDelete = viewModel::batchDelete,
        onMiddleAction = {
          if (shouldUnpinSelected) {
            // 置顶只属于当前账号 Settings；批量取消时保持其他未选中事项的顺序不变。
            pinnedIds = pinnedIds.filterNot { it in selectedPinTargets }
            saveScheduleTodoPinnedIds(currentAccountSettings, pinnedIds)
          } else {
            pinSchedules(
              scheduleIds = selectedPinTargets,
              revealPendingTop = false,
            )
          }
          viewModel.exitManageMode()
        },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  if (editorEnabled &&
    (showCreateEditor || editingItem != null || timelineEditingOccurrence != null)
  ) {
    // Window 包住完整编辑流程，范围选择和未保存确认与主 BottomSheet 共用同一窗口层级。
    Window(dismissOnBackPress = null) {
      Box(modifier = Modifier.fillMaxSize()) {
        EditScheduleDialog(
          show = showCreateEditor,
          creationTiming = createTiming,
          categoryRepository = viewModel.repository,
          showCourseRelation = true,
          onDismiss = { showCreateEditor = false },
          onConfirm = { state, _, newCategory ->
            viewModel.saveSchedule(
              state,
              EditScope.ALL,
              null,
              newCategory,
            )
            showCreateEditor = false
          },
        )
        editingItem?.let { item ->
          ScheduleExistingItemEditor(
            item = item,
            viewModel = viewModel,
            onDismiss = { editingIdentity = null },
          )
        }
        val timelineOccurrence = timelineEditingOccurrence
        val timelineSchedule = timelineEditingSchedule
        if (timelineOccurrence != null && timelineSchedule != null) {
          EditScheduleDialog(
            show = true,
            editSchedule = timelineSchedule,
            editOccurrence = timelineOccurrence.toDomainOccurrence(),
            recurrenceId = timelineOccurrence.recurrenceId,
            categoryRepository = viewModel.repository,
            showCourseRelation = true,
            onDismiss = { timelineEditingIdentity = null },
            onConfirm = { state, scope, newCategory ->
              viewModel.saveSchedule(
                state,
                scope,
                timelineOccurrence.recurrenceId,
                newCategory,
              )
            },
            onDelete = { scope ->
              viewModel.deleteScheduleScoped(
                timelineSchedule.id,
                scope,
                timelineOccurrence.recurrenceId,
              )
              timelineEditingIdentity = null
            },
            onToggleCompleted = timelineSchedule.todoState?.let {
              { completed ->
                viewModel.completeSchedule(
                  timelineSchedule.id,
                  timelineOccurrence.recurrenceId,
                  completed,
                )
              }
            },
          )
        }
      }
    }
  }
}

/**
 * 生成清单页新增事项的当天默认时间段。
 *
 * 正常情况下从 [now] 所在分钟开始持续一小时；时间段不跨日，结束最晚为当天 23:59。
 * 若当前时间已经晚于 23:49，则把开始时间回收到 23:49，以继续满足编辑器至少 10 分钟的输入约束。
 * [timeZone] 同时决定“当天”和最终写入的时间区，调用方应传页面当前使用的系统时区。
 */
internal fun defaultTodoCreationTiming(
  now: Instant,
  timeZone: TimeZone,
): ScheduleTiming.Timed {
  val localNow = now.toLocalDateTime(timeZone)
  val currentMinute = localNow.hour * 60 + localNow.minute
  val latestStartMinute = TODO_LAST_MINUTE_OF_DAY - TODO_MIN_INTERVAL_MINUTES
  val startMinute = currentMinute.coerceAtMost(latestStartMinute)
  val endMinute = (startMinute + TODO_DEFAULT_DURATION_MINUTES).coerceAtMost(TODO_LAST_MINUTE_OF_DAY)
  return ScheduleTiming.Timed(
    start = MinuteTimeDate(
      localNow.year,
      localNow.date.month.number,
      localNow.day,
      startMinute / 60,
      startMinute % 60,
    ),
    durationMinutes = endMinute - startMinute,
    timeZoneId = timeZone.id,
  )
}
