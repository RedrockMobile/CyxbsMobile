package com.cyxbs.pages.schedule.ui.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_TODO
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.schedule.api.ScheduleTodoNavArgument
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.canSubmitScheduleMutation
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoTime
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_empty_completed
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_empty_pending
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_urgency_flag
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.time.Clock

/** 邮子清单独立导航入口；它与 SchedulePage 共用仓库，但不复用课表页面布局和导航栈。 */
@AppNav(route = NAV_SCHEDULE_TODO)
class ScheduleTodoNavEntry : AppNavEntry<ScheduleTodoNavArgument>() {

  /** 清单读写依赖精确登录账号。 */
  override fun isNeedLogin(argument: ScheduleTodoNavArgument): Boolean = true

  /** 清单页面保持单例；新的 identity 参数由组合状态消费，不叠加第二份仓库观察。 */
  override fun getContentKey(argument: ScheduleTodoNavArgument): String = "schedule_todo_singleton"

  /** 创建页面 ViewModel，并把独立导航参数交给清单页面。 */
  @Composable
  override fun Content(argument: ScheduleTodoNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel() }
    ScheduleTodoPage(argument = argument, viewModel = viewModel)
  }
}

/**
 * 邮子清单页面。
 *
 * 页面仅负责把共享 Schedule 快照投影成卡片列表；新增、编辑、完成和删除仍通过 [ScheduleMainViewModel]
 * 进入同一个仓库，因此这里的任何修改都会同步反映到独立的 SchedulePage。[onBack] 必须绑定实际入栈的
 * 导航参数；Desktop mock 使用独立参数类型时会显式传入自己的返回回调。
 */
@Composable
fun ScheduleTodoPage(
  argument: ScheduleTodoNavArgument,
  viewModel: ScheduleMainViewModel,
  onBack: () -> Unit = argument::popBackStack,
) {
  val colors = LocalAppColors.current
  val snapshot by viewModel.snapshot.collectAsState()
  val manageMode by viewModel.isManageMode.collectAsState()
  val selectedIds by viewModel.selectedIds.collectAsState()
  val editorEnabled = viewModel.mutationMode.canSubmitScheduleMutation()
  val viewerTimeZone = remember { TimeZone.currentSystemDefault() }
  val projection = remember(snapshot, viewerTimeZone) {
    projectScheduleTodo(snapshot, Clock.System.now(), viewerTimeZone)
  }
  val currentAccountSettings = accountSettings
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()
  val visibleCategories = remember(snapshot.categories) {
    mergeScheduleTodoCategories(snapshot.categories)
  }

  var showCreateEditor by remember { mutableStateOf(false) }
  var editingIdentity by remember { mutableStateOf<Pair<ScheduleId, RecurrenceId?>?>(null) }
  var selectedCategoryId by remember(currentAccountSettings.stuNum) {
    mutableStateOf<CategoryId?>(null)
  }
  // 置顶只保存在当前账号 Settings，不进入 Schedule v2 协议；切号后 remember 会加载对应账号的数据。
  var pinnedIds by remember(currentAccountSettings.stuNum) {
    mutableStateOf(loadScheduleTodoPinnedIds(currentAccountSettings))
  }
  // 真实关联链路尚未实现；当前只保留页面会话内的视觉选择，不能写入 Schedule 或误当作同步事实。
  var calendarLinkedKeys by remember(snapshot.accountId) { mutableStateOf<Set<String>>(emptySet()) }
  var deepLinkConsumed by remember(argument.scheduleId, argument.recurrenceId) {
    mutableStateOf(false)
  }

  LaunchedEffect(Unit) {
    viewModel.initialize()
  }
  LaunchedEffect(editorEnabled) {
    if (!editorEnabled) {
      showCreateEditor = false
      editingIdentity = null
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
  LaunchedEffect(argument.scheduleId, argument.recurrenceId, projection, editorEnabled) {
    if (!editorEnabled || deepLinkConsumed) return@LaunchedEffect
    val scheduleId = argument.scheduleId ?: return@LaunchedEffect
    val item = (projection.pending + projection.completed).firstOrNull {
      it.schedule.id == scheduleId &&
          (argument.recurrenceId == null || it.occurrence.recurrenceId == argument.recurrenceId)
    }
    if (item != null) {
      editingIdentity = item.schedule.id to item.occurrence.recurrenceId
      deepLinkConsumed = true
    } else if (snapshot.schedules.any { it.id == scheduleId }) {
      deepLinkConsumed = true
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

  /** 切换尚未接入业务链路的课表关联视觉状态，并明确告知用户当前只是功能预览。 */
  fun toggleCalendarLink(itemKey: String) {
    val shouldLink = itemKey !in calendarLinkedKeys
    calendarLinkedKeys = if (shouldLink) {
      calendarLinkedKeys + itemKey
    } else {
      calendarLinkedKeys - itemKey
    }
    toast(
      if (shouldLink) "已标记关联到课表，功能将在后续开放"
      else "已取消关联标记"
    )
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
        editorEnabled = editorEnabled,
        onBack = onBack,
        onManage = {
          if (manageMode) viewModel.exitManageMode() else viewModel.enterManageMode()
        },
      )
      ScheduleTodoSyncStatus(snapshot.status, viewModel.mutationMode)
      ScheduleTodoCategoryFilterBar(
        categories = visibleCategories,
        selectedCategoryId = selectedCategoryId,
        onSelect = { selectedCategoryId = it },
      )
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
          ScheduleTodoSectionTitle("未完成")
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
              isPinned = item.schedule.id in pinnedIds,
              manageMode = manageMode,
              selected = item.schedule.id in selectedIds,
              onSelect = { viewModel.toggleSelect(item.schedule.id) },
              onOpen = {
                showCreateEditor = false
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
              isLinkedToCalendar = item.key in calendarLinkedKeys,
              onToggleCalendarLink = { toggleCalendarLink(item.key) },
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
          ScheduleTodoSectionTitle("已完成")
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
              isPinned = item.schedule.id in pinnedIds,
              manageMode = manageMode,
              selected = item.schedule.id in selectedIds,
              onSelect = { viewModel.toggleSelect(item.schedule.id) },
              onOpen = {
                showCreateEditor = false
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
              isLinkedToCalendar = item.key in calendarLinkedKeys,
              onToggleCalendarLink = { toggleCalendarLink(item.key) },
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
    }

    if (editorEnabled && !manageMode) {
      FloatingActionButton(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(end = 24.dp, bottom = 28.dp)
          .size(50.dp),
        onClick = {
          editingIdentity = null
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
        shouldUnpinSelected = shouldUnpinSelected,
        onSelectAll = {
          viewModel.selectAll((pending + completed).map { it.schedule.id }.distinct())
        },
        onDelete = viewModel::batchDelete,
        onPin = {
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

  if (editorEnabled) {
    EditScheduleDialog(
      show = showCreateEditor,
      categories = visibleCategories,
      onDismiss = { showCreateEditor = false },
      onConfirm = { state, _ ->
        viewModel.saveSchedule(
          state,
          EditScope.ALL,
          null,
          findMissingScheduleTodoDefaultCategory(state.categoryId, snapshot.categories),
        )
        showCreateEditor = false
      },
    )
    editingItem?.let { item ->
      EditScheduleDialog(
        show = true,
        editSchedule = item.schedule,
        editOccurrence = item.occurrence.toDomainOccurrence(),
        recurrenceId = item.occurrence.recurrenceId,
        categories = visibleCategories,
        onDismiss = { editingIdentity = null },
        onConfirm = { state, scope ->
          viewModel.saveSchedule(
            state,
            scope,
            item.occurrence.recurrenceId,
            findMissingScheduleTodoDefaultCategory(state.categoryId, snapshot.categories),
          )
          editingIdentity = null
        },
        onDelete = { scope ->
          viewModel.deleteScheduleScoped(
            item.schedule.id,
            scope,
            item.occurrence.recurrenceId,
          )
          editingIdentity = null
        },
      )
    }
  }
}

/**
 * 顶部分类筛选直接展示 Schedule v2 当前账号的真实 Category。
 *
 * null 表示聚合入口“全部”；其余项使用 Category identity 过滤，重命名分类不会丢失当前选择。
 * 横向滚动承接任意数量的自定义分类，不再假设固定存在“学习、生活、其他”。
 */
@Composable
private fun ScheduleTodoCategoryFilterBar(
  categories: List<ScheduleCategory>,
  selectedCategoryId: CategoryId?,
  onSelect: (CategoryId?) -> Unit,
) {
  val colors = LocalAppColors.current
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    contentPadding = PaddingValues(
      start = 16.dp,
      top = 16.dp,
      end = 16.dp,
      bottom = 13.dp,
    ),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(
      items = listOf<Pair<CategoryId?, String>>(null to "全部") +
        categories.map { it.id to it.name },
    ) { (categoryId, label) ->
      val isSelected = categoryId == selectedCategoryId
      Surface(
        color = if (isSelected) ScheduleTodoAccentColor else Color.Transparent,
        contentColor = if (isSelected) {
          if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1
        } else {
          colors.tvLv3
        },
        // 设计稿的分类选中块左下角为直角，其余三个角保留胶囊式圆角。
        shape = RoundedCornerShape(
          topStart = 16.dp,
          topEnd = 20.dp,
          bottomEnd = 20.dp,
          bottomStart = 0.dp,
        ),
        modifier = Modifier
          .height(32.dp)
          .clickableNoIndicator { onSelect(categoryId) },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.7.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }
    }
  }
}

private const val SCHEDULE_TODO_PINNED_IDS_KEY = "schedule_todo_pinned_ids"

/**
 * 从当前账号 Settings 恢复清单置顶顺序。
 *
 * 旧值损坏时直接移除并回到空列表，不能让非核心的展示偏好阻断清单页面启动。
 */
private fun loadScheduleTodoPinnedIds(settings: AccountSettings): List<ScheduleId> {
  val json = settings.getStringOrNull(SCHEDULE_TODO_PINNED_IDS_KEY) ?: return emptyList()
  return runCatching {
    defaultJson.decodeFromString<List<String>>(json)
      .map { requireNotNull(ScheduleId.parseOrNull(it)) }
      .distinct()
  }.onFailure {
    settings.remove(SCHEDULE_TODO_PINNED_IDS_KEY)
  }.getOrDefault(emptyList())
}

/** 将清单置顶顺序立即写入当前账号 Settings；该字段只用于端上排序，不参与任何网络请求。 */
private fun saveScheduleTodoPinnedIds(settings: AccountSettings, pinnedIds: List<ScheduleId>) {
  if (pinnedIds.isEmpty()) {
    settings.remove(SCHEDULE_TODO_PINNED_IDS_KEY)
    return
  }
  settings.putString(
    SCHEDULE_TODO_PINNED_IDS_KEY,
    defaultJson.encodeToString<List<String>>(pinnedIds.map { it.value }),
  )
}

/** 将清单投影恢复成编辑弹窗需要的实例值；所有字段均来自同一有效 occurrence。 */
internal fun com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence.toDomainOccurrence(): ScheduleOccurrence =
  ScheduleOccurrence(
    scheduleId = scheduleId,
    recurrenceId = recurrenceId,
    timing = timing,
    title = title,
    description = description,
    categoryId = categoryId,
    reminders = reminders,
    status = status,
    isOverridden = isOverridden,
  )

/** 顶部栏保留 Figma 的返回、标题和批量管理结构，但颜色完全来自应用主题。 */
@Composable
private fun ScheduleTodoHeader(
  manageMode: Boolean,
  editorEnabled: Boolean,
  onBack: () -> Unit,
  onManage: () -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.bottomBg,
    // 页面以 edge-to-edge 方式绘制；只由标题栏消费顶部安全区，避免标题进入状态栏或列表重复留白。
    modifier = Modifier.statusBarsPadding(),
  ) {
    Column(modifier = Modifier.padding(top = 13.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(37.dp)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .width(22.dp)
            .height(37.dp)
            .clickableNoIndicator(onClick = onBack),
          contentAlignment = Alignment.CenterStart,
        ) {
          Icon(
            painter = painterResource(ConfigRes.configIcBack()),
            contentDescription = "返回",
            tint = colors.tvLv1,
            modifier = Modifier.width(9.dp).height(19.dp),
          )
        }
        Text(
          text = "邮子清单",
          color = colors.tvLv1,
          fontSize = 21.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.05.sp,
          modifier = Modifier.weight(1f),
        )
        if (manageMode) {
          Surface(
            color = ScheduleTodoAccentColor,
            contentColor = if (MaterialTheme.colors.isLight) {
              ScheduleTodoHeaderOnAccentColor
            } else {
              colors.tvLv1
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.clickableNoIndicator(onClick = onManage),
          ) {
            Text(
              text = "完成",
              fontSize = 16.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.8.sp,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
          }
        } else {
          TextButton(onClick = onManage, enabled = editorEnabled) {
            Text(
              text = "批量管理",
              color = if (editorEnabled) colors.tvLv1 else colors.tvLv1.copy(alpha = 0.35f),
              fontSize = 16.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.8.sp,
            )
          }
        }
      }
      // Figma 标题栏底部使用 tvLv4 的 10% 透明度细线，深色主题也由同一 token 自适应。
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(colors.tvLv4.copy(alpha = 0.1f)),
      )
    }
  }
}

/** 顶部提醒条只显示当前临期/超期事实，不承担同步或重试入口。 */
@Composable
private fun ScheduleTodoUrgentBanner(count: Int) {
  val colors = LocalAppColors.current
  val bannerTextColor = if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1
  Surface(
    color = ScheduleTodoAccentColor,
    contentColor = bannerTextColor,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 34.dp, bottomEnd = 34.dp),
    modifier = Modifier
      .fillMaxWidth()
      // 上方留白负责与分类栏分隔；下方距离由列表 contentPadding 统一控制，避免两段间距叠加。
      .padding(start = 16.dp, top = 8.dp, end = 16.dp)
      .height(52.dp),
  ) {
    Box(contentAlignment = Alignment.CenterStart) {
      Text(
        text = buildAnnotatedString {
          append("你有 ")
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(count.toString()) }
          append(" 项待办即将到期或已超期")
        },
        color = if (MaterialTheme.colors.isLight) ScheduleTodoOnAccentColor else colors.tvLv1,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.48.sp,
        lineHeight = 25.sp,
        modifier = Modifier.padding(horizontal = 18.dp),
      )
    }
  }
}

@Composable
private fun ScheduleTodoSectionTitle(text: String) {
  val colors = LocalAppColors.current
  Text(
    text = text,
    color = colors.tvLv3,
    fontSize = 20.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
  )
}

/**
 * 按 Figma 分别展示未完成、已完成为空时的原始矢量插画与提示语。
 *
 * [completed] 决定选用哪一张插画；Compose Resources 会按当前主题自动加载 drawable 或
 * drawable-dark 中的同名资源，因此深色稿不经过运行时猜色或透明度变换。
 */
@Composable
private fun ScheduleTodoEmptyCard(completed: Boolean) {
  val colors = LocalAppColors.current
  val artwork = if (completed) {
    Res.drawable.schedule_ic_todo_empty_completed
  } else {
    Res.drawable.schedule_ic_todo_empty_pending
  }
  val artworkSize = if (completed) 179.dp to 100.dp else 151.dp to 111.dp
  val message = if (completed) {
    "还没有已完成事项哦，期待你的好消息！"
  } else {
    "还没有待做事项哦，快去添加吧！"
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Figma 中两个空态都占据 200dp，使下一分区标题不会因插画内容较矮而提前上移。
      .height(200.dp)
      .padding(top = if (completed) 31.dp else 27.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = painterResource(artwork),
      contentDescription = message,
      modifier = Modifier.size(width = artworkSize.first, height = artworkSize.second),
    )
    // 两张稿件的插画与说明文字均保留 16dp 间距。
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = message,
      color = colors.tvLv3.copy(alpha = if (MaterialTheme.colors.isLight) 0.72f else 0.62f),
      fontSize = 12.sp,
      letterSpacing = 0.3.sp,
    )
  }
}

/** 将提醒偏移量转换为清单卡片使用的完整提示语。 */
private fun formatTodoReminder(offsetMinutes: Int): String? = when {
  offsetMinutes < 0 -> null
  offsetMinutes == 0 -> "准时提醒"
  offsetMinutes % 60 == 0 -> "提前${offsetMinutes / 60}小时提醒"
  else -> "提前${offsetMinutes}分钟提醒"
}

/** 清单卡片展示标题、时间点/时间段和备注，不再显示“关联课表”图标。 */
@Composable
private fun ScheduleTodoCard(
  modifier: Modifier = Modifier,
  item: ScheduleTodoItemUi,
  isPinned: Boolean,
  manageMode: Boolean,
  selected: Boolean,
  onSelect: () -> Unit,
  onOpen: () -> Unit,
  onComplete: () -> Unit,
  onTogglePin: () -> Unit,
  isLinkedToCalendar: Boolean,
  onToggleCalendarLink: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  val completed = item.occurrence.status == OccurrenceStatus.COMPLETED
  val pinIcon = ConfigRes.configIcPin()
  val deleteIcon = ConfigRes.configIcDelete()
  val restoreIcon = ConfigRes.configIcRestore()
  val hasUrgencyBadge = item.isOverdue || item.isDueSoon
  val reminderText = item.occurrence.reminders
    .mapNotNull { reminder -> formatTodoReminder(reminder.offsetMinutes) }
    .joinToString(separator = "、")
    .takeIf(String::isNotEmpty)
  val timeIcon = rememberIcAddtodoTime()
  val cardShape = RoundedCornerShape(16.dp)
  val actionWidth = 110.dp
  val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
  var dragOffsetPx by remember(item.key) { mutableFloatStateOf(0f) }
  var settleAnimation by remember(item.key) { mutableStateOf<Job?>(null) }
  val cardCoroutineScope = rememberCoroutineScope()

  /** 松手后把卡片平滑吸附到收起或完全展开位置；新拖动会取消尚未结束的旧动画。 */
  fun settleSwipe(targetOffsetPx: Float) {
    settleAnimation?.cancel()
    settleAnimation = cardCoroutineScope.launch {
      animate(
        initialValue = dragOffsetPx,
        targetValue = targetOffsetPx,
        animationSpec = tween(durationMillis = 180),
      ) { value, _ ->
        dragOffsetPx = value
      }
    }
  }

  LaunchedEffect(manageMode) {
    if (manageMode) settleSwipe(0f)
  }

  Box(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .width(actionWidth)
        .height(28.dp)
        .padding(end = 13.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ScheduleTodoSwipeAction(
        icon = if (completed) restoreIcon else pinIcon,
        contentDescription = when {
          completed -> "恢复未完成"
          isPinned -> "取消置顶"
          else -> "置顶"
        },
        backgroundColor = if (completed) {
          ScheduleTodoRestoreActionBackgroundColor
        } else {
          ScheduleTodoPinActionBackgroundColor
        },
        tint = if (completed) {
          ScheduleTodoRestoreActionTintColor
        } else {
          ScheduleTodoPinActionTintColor
        },
        showCancelMark = isPinned && !completed,
        onClick = {
          settleSwipe(0f)
          if (completed) onComplete() else onTogglePin()
        },
      )
      ScheduleTodoSwipeAction(
        icon = deleteIcon,
        contentDescription = "删除",
        backgroundColor = ScheduleTodoDeleteActionBackgroundColor,
        tint = ScheduleTodoDeleteActionTintColor,
        onClick = {
          settleSwipe(0f)
          onDelete()
        },
      )
    }

    Surface(
      color = if (MaterialTheme.colors.isLight) ScheduleTodoCardContainerColor else colors.middleBg,
      shape = cardShape,
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
        .pointerInput(item.key, manageMode) {
          if (!manageMode) {
            detectHorizontalDragGestures(
              onDragStart = {
                settleAnimation?.cancel()
                settleAnimation = null
              },
              onHorizontalDrag = { change, amount ->
                change.consume()
                dragOffsetPx = (dragOffsetPx + amount).coerceIn(-actionWidthPx, 0f)
              },
              onDragEnd = {
                settleSwipe(
                  if (dragOffsetPx <= -actionWidthPx / 2f) -actionWidthPx else 0f
                )
              },
              onDragCancel = { settleSwipe(0f) },
            )
          }
        }
        .clip(cardShape)
        .clickable {
          when {
            manageMode -> onSelect()
            dragOffsetPx != 0f -> settleSwipe(0f)
            else -> onOpen()
          }
        },
    ) {
      Box {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                // 圆圈和方框始终占用相同宽度，切换批量模式时标题不会左右跳动。
                .size(24.dp)
                .clickableNoIndicator { if (manageMode) onSelect() else onComplete() },
              contentAlignment = Alignment.Center,
            ) {
              if (manageMode && selected) {
                ScheduleTodoCheckedBox()
              } else {
                Icon(
                  imageVector = when {
                    manageMode -> Icons.Outlined.CheckBoxOutlineBlank
                    completed -> Icons.Outlined.CheckCircle
                    else -> Icons.Outlined.RadioButtonUnchecked
                  },
                  contentDescription = when {
                    manageMode -> "选择"
                    completed -> "恢复未完成"
                    else -> "标记完成"
                  },
                  tint = when {
                    // 批量方框只表达选中状态，不能继承事项的完成色。
                    manageMode -> if (MaterialTheme.colors.isLight) {
                      ScheduleTodoPendingIndicatorColor
                    } else {
                      colors.tvLv3.copy(alpha = 0.46f)
                    }
                    // Figma 的完成圆圈使用“完成色”，与批量选择主色承担不同语义。
                    completed -> if (MaterialTheme.colors.isLight) {
                      ScheduleTodoCompletedIndicatorColor
                    } else {
                      colors.tvLv3.copy(alpha = 0.46f)
                    }
                    else -> if (MaterialTheme.colors.isLight) {
                      ScheduleTodoPendingIndicatorColor
                    } else {
                      colors.tvLv3.copy(alpha = 0.46f)
                    }
                  },
                  modifier = Modifier.size(if (completed && !manageMode) 22.dp else 24.dp),
                )
              }
            }
            // 图标画布增至 24dp 后同步收窄间距，标题起点仍保持在卡片左侧 52dp。
            Spacer(modifier = Modifier.width(12.dp))
            Row(
              modifier = Modifier
                .weight(1f)
                .padding(end = if (hasUrgencyBadge) 64.dp else 0.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = item.occurrence.title,
                color = if (completed) colors.tvLv3.copy(alpha = 0.3f) else colors.tvLv3,
                fontSize = 18.sp,
                fontWeight = if (item.isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .weight(1f, fill = false)
                  .wrapContentHeight()
                  .drawWithContent {
                    drawContent()
                    if (completed) {
                      // Figma 浅色稿使用独立的 2px、主文字色 10% 透明度横线，不能复用字体装饰线。
                      drawLine(
                        color = colors.tvLv3.copy(alpha = 0.1f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                      )
                    }
                  },
              )
              if (isPinned) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  painter = painterResource(pinIcon),
                  contentDescription = "已置顶",
                  tint = ScheduleTodoPinActionTintColor,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.size(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ScheduleTodoInfoPill(
              text = item.timeText,
              icon = timeIcon,
              modifier = Modifier.weight(1f, fill = false),
            )
            reminderText?.let { text ->
              ScheduleTodoInfoPill(text = text)
            }
            ScheduleTodoCalendarLinkButton(
              selected = isLinkedToCalendar,
              onClick = onToggleCalendarLink,
            )
          }
          if (item.occurrence.description.isNotBlank()) {
            Spacer(modifier = Modifier.size(7.dp))
            ScheduleTodoInfoPill("备注：${item.occurrence.description}")
          }
        }
        ScheduleTodoUrgencyBadge(
          item = item,
          modifier = Modifier.align(Alignment.TopEnd),
        )
      }
    }
  }
}

/**
 * 批量管理的选中方框。
 *
 * 外框保持 Figma 的 18dp 实心尺寸，内部勾使用独立的较短路径，避免 Material CheckBox 默认勾过大。
 */
@Composable
private fun ScheduleTodoCheckedBox() {
  Canvas(
    modifier = Modifier
      .size(24.dp)
      .semantics { contentDescription = "取消选择" },
  ) {
    val boxInset = 3.dp.toPx()
    drawRoundRect(
      color = ScheduleTodoAccentColor,
      topLeft = Offset(boxInset, boxInset),
      size = Size(18.dp.toPx(), 18.dp.toPx()),
      cornerRadius = CornerRadius(2.dp.toPx()),
    )
    val strokeWidth = 1.6.dp.toPx()
    val checkMiddle = Offset(10.6.dp.toPx(), 14.1.dp.toPx())
    drawLine(
      color = ScheduleTodoOnAccentColor,
      start = Offset(8.2.dp.toPx(), 11.8.dp.toPx()),
      end = checkMiddle,
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = ScheduleTodoOnAccentColor,
      start = checkMiddle,
      end = Offset(15.9.dp.toPx(), 9.1.dp.toPx()),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }
}

/**
 * 左滑操作使用 Figma 的 28dp 小按钮。
 *
 * [icon] 是由设计稿原始路径转换的 Vector Drawable；[backgroundColor] 已包含设计稿要求的透明度，
 * 这里不再二次覆盖 alpha。[showCancelMark] 会在 pin 上叠加反向斜线，明确表示取消置顶。
 */
@Composable
private fun ScheduleTodoSwipeAction(
  icon: DrawableResource,
  contentDescription: String,
  backgroundColor: Color,
  tint: Color,
  showCancelMark: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.size(28.dp).clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
          .size(28.dp)
          .scheduleTodoCancelPinMark(showCancelMark, tint),
      )
    }
  }
}

/** 把临期或超期状态固定覆盖在卡片右上角，尺寸和异形圆角与 Figma 标签保持一致。 */
@Composable
private fun ScheduleTodoUrgencyBadge(
  item: ScheduleTodoItemUi,
  modifier: Modifier = Modifier,
) {
  val label = when {
    item.isOverdue -> "已超期"
    item.isDueSoon -> "临期"
    else -> return
  }
  val color = if (item.isOverdue) ScheduleTodoOverdueColor else ScheduleTodoDueSoonColor
  val containerColor = if (item.isOverdue) {
    ScheduleTodoOverdueContainerColor
  } else {
    ScheduleTodoDueSoonContainerColor
  }
  Surface(
    color = containerColor,
    contentColor = color,
    shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 16.dp),
    modifier = modifier.size(
      width = if (item.isOverdue) 85.dp else 75.dp,
      height = 30.dp,
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(Res.drawable.schedule_ic_todo_urgency_flag),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.7.sp,
        maxLines = 1,
      )
    }
  }
}

/**
 * 卡片时间、提醒和备注共用原有信息胶囊。
 *
 * [icon] 仅用于时间项复用编辑弹窗的自绘时间图标；其余背景、文字和间距保持卡片原样。
 */
@Composable
private fun ScheduleTodoInfoPill(
  text: String,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val contentColor = if (MaterialTheme.colors.isLight) {
    ScheduleTodoInfoContentColor
  } else {
    colors.tvLv3.copy(alpha = 0.6f)
  }
  Surface(
    color = if (MaterialTheme.colors.isLight) {
      ScheduleTodoInfoContainerColor
    } else {
      colors.negative.copy(alpha = 0.55f)
    },
    contentColor = contentColor,
    shape = RoundedCornerShape(5.dp),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      icon?.let {
        Icon(
          imageVector = it,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(15.dp),
        )
      }
      Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.65.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * “关联到课表”临时视觉开关。
 *
 * 选中态只改变页面内存状态，尚不写仓库或请求后端；真实关联能力接入后应由权威数据替换 [selected]。
 */
@Composable
private fun ScheduleTodoCalendarLinkButton(
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  val backgroundColor = if (selected) {
    ScheduleTodoCalendarLinkSelectedColor
  } else if (MaterialTheme.colors.isLight) {
    ScheduleTodoInfoContainerColor
  } else {
    colors.negative.copy(alpha = 0.55f)
  }
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.size(28.dp).clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(ConfigRes.configIcCalendarSync()),
        contentDescription = if (selected) "取消关联到课表" else "关联到课表",
        tint = if (selected) ScheduleTodoOnAccentColor else ScheduleTodoInfoContentColor,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Figma 清单批量管理底栏。
 *
 * 全选和删除继续作用于去重后的 ScheduleId；置顶只调整当前页面的展示优先级，不写入远端协议。
 */
@Composable
private fun ScheduleTodoManageBar(
  selectedCount: Int,
  totalCount: Int,
  shouldUnpinSelected: Boolean,
  onSelectAll: () -> Unit,
  onPin: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Surface(
    color = if (MaterialTheme.colors.isLight) ScheduleTodoCardContainerColor else colors.topBg,
    elevation = 8.dp,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    // 外层背景延伸到系统导航栏；只有内部按钮行上移到安全区内。
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .height(80.dp)
        .padding(horizontal = 35.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ScheduleTodoManageButton(
        label = "全选",
        backgroundColor = if (MaterialTheme.colors.isLight) {
          ScheduleTodoSelectAllContainerColor
        } else {
          colors.negative
        },
        contentColor = colors.tvLv3,
        enabled = totalCount > 0,
        onClick = onSelectAll,
      )
      ScheduleTodoManageButton(
        label = if (shouldUnpinSelected) "取消置顶" else "置顶",
        backgroundColor = ScheduleTodoAccentColor.copy(alpha = 0.9f),
        contentColor = if (MaterialTheme.colors.isLight) ScheduleTodoOnAccentColor else colors.tvLv1,
        enabled = selectedCount > 0,
        onClick = onPin,
      )
      ScheduleTodoManageButton(
        label = "删除",
        backgroundColor = ScheduleTodoOverdueContainerColor,
        contentColor = ScheduleTodoOverdueColor,
        enabled = selectedCount > 0,
        onClick = onDelete,
      )
    }
  }
}

/** 批量管理使用固定尺寸文字按钮，避免图标与设计稿的三等分操作产生视觉偏差。 */
@Composable
private fun ScheduleTodoManageButton(
  label: String,
  backgroundColor: Color,
  contentColor: Color,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = backgroundColor.copy(alpha = if (enabled) 1f else 0.42f),
    contentColor = contentColor.copy(alpha = if (enabled) 1f else 0.42f),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .size(width = 92.dp, height = 38.dp)
      .clickableNoIndicator(enabled = enabled, onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
      )
    }
  }
}

/**
 * 展示会直接影响当前操作的仓库状态。
 *
 * 远端暂不可用时仍允许 local-first 编辑，因此这里保持静默；底层继续保留失败状态与待同步数据。
 */
@Composable
private fun ScheduleTodoSyncStatus(
  status: ScheduleRepositoryStatus,
  mutationMode: ScheduleRepositoryMutationMode,
) {
  val colors = LocalAppColors.current
  when {
    mutationMode == ScheduleRepositoryMutationMode.READ_ONLY -> {
      Surface(color = MaterialTheme.colors.error.copy(alpha = 0.1f)) {
        Text(
          text = "当前没有可编辑的登录账号，仅可查看事项。",
          color = MaterialTheme.colors.error,
          fontSize = 12.sp,
          modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
      }
    }

    status == ScheduleRepositoryStatus.Loading -> {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = colors.positive,
      )
    }

    status is ScheduleRepositoryStatus.Corrupted -> {
      Surface(color = MaterialTheme.colors.error.copy(alpha = 0.1f)) {
        Text(
          text = "日程数据暂时无法读取。",
          color = MaterialTheme.colors.error,
          fontSize = 12.sp,
          modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
      }
    }

    else -> Unit
  }
}
