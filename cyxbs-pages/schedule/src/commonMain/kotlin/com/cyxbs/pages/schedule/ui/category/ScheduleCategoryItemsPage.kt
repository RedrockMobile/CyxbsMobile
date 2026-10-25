package com.cyxbs.pages.schedule.ui.category

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.ui.main.isScheduleMainEditorEnabled
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleExistingItemEditor
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleListPageHeader
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleManageHeaderAction
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoItemUi
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoManageBar
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoSectionTitle
import com.cyxbs.pages.schedule.ui.todo.main.loadScheduleTodoPinnedIds
import com.cyxbs.pages.schedule.ui.todo.main.saveScheduleTodoPinnedIds
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * 展示全部日程，或一个分组引用的所有日程。
 *
 * 页面复用清单卡片、编辑弹窗和批量底栏；这里只负责完整数据投影以及“移出当前分组”这一页内操作。
 */
@Composable
internal fun ScheduleCategoryItemsPage(
  argument: ScheduleCategoryItemsNavArgument,
  viewModel: ScheduleMainViewModel,
  onBack: () -> Unit,
) {
  val colors = LocalAppColors.current
  val snapshot by viewModel.snapshot.collectAsState()
  val manageMode by viewModel.isManageMode.collectAsState()
  val selectedIds by viewModel.selectedIds.collectAsState()
  val categoryId = remember(argument.categoryId) { argument.categoryId?.let(::CategoryId) }
  val settings = accountSettings
  var pinnedIds by remember(settings.stuNum) { mutableStateOf(loadScheduleTodoPinnedIds(settings)) }
  var editingItem by remember { mutableStateOf<ScheduleTodoItemUi?>(null) }
  var selectedTab by remember { mutableStateOf(ScheduleCategoryItemTab.TODO) }
  val todoListState = rememberLazyListState()
  val scheduleListState = rememberLazyListState()
  val viewerTimeZone = remember { TimeZone.currentSystemDefault() }
  val categoryName = categoryId?.let { id ->
    snapshot.categories.firstOrNull { it.id == id }?.name
  } ?: argument.categoryName
  val projection = remember(snapshot, categoryId, pinnedIds, viewerTimeZone) {
    projectScheduleCategoryItems(snapshot, categoryId, Clock.System.now(), viewerTimeZone, pinnedIds)
  }
  val visibleListState = when (selectedTab) {
    ScheduleCategoryItemTab.TODO -> todoListState
    ScheduleCategoryItemTab.SCHEDULE -> scheduleListState
  }
  val visibleItems = when (selectedTab) {
    ScheduleCategoryItemTab.TODO -> projection.todos.active + projection.todos.completed
    ScheduleCategoryItemTab.SCHEDULE -> projection.schedules.upcoming + projection.schedules.expired
  }
  val visibleIds = remember(visibleItems) { visibleItems.map { it.schedule.id } }
  val editorEnabled = isScheduleMainEditorEnabled(viewModel.mutationMode)

  LaunchedEffect(Unit) { viewModel.initialize() }
  LaunchedEffect(snapshot, editingItem?.schedule?.id) {
    val current = editingItem ?: return@LaunchedEffect
    editingItem = (projection.todos.active + projection.todos.completed +
      projection.schedules.upcoming + projection.schedules.expired)
      .firstOrNull { it.schedule.id == current.schedule.id }
  }
  DisposableEffect(viewModel) {
    onDispose(viewModel::exitManageMode)
  }

  val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
  NavigationBackHandler(
    state = backEventState,
    isBackEnabled = manageMode,
    onBackCompleted = viewModel::exitManageMode,
  )
  val handleBack: () -> Unit = {
    if (manageMode) viewModel.exitManageMode() else onBack()
  }

  Box(Modifier.fillMaxSize().background(colors.bottomBg)) {
    Column(Modifier.fillMaxSize()) {
      ScheduleListPageHeader(title = categoryName, onBack = handleBack) {
        if (editorEnabled) {
          ScheduleManageHeaderAction(
            manageMode = manageMode,
            onEnter = viewModel::enterManageMode,
            onDone = viewModel::exitManageMode,
          )
        }
      }
      Text(
        text = if (categoryId == null) "全部待办与普通日程" else "当前分组所有日程",
        color = colors.tvLv2,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 10.dp),
      )
      ScheduleCategoryItemTabs(
        selected = selectedTab,
        onSelect = { tab ->
          if (selectedTab != tab) {
            selectedTab = tab
            viewModel.clearSelection()
          }
        },
      )
      if (visibleItems.isEmpty()) {
        Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
          Text(
            text = if (selectedTab == ScheduleCategoryItemTab.TODO) {
              if (categoryId == null) "暂无待办" else "当前分组暂无待办"
            } else {
              if (categoryId == null) "暂无普通日程" else "当前分组暂无普通日程"
            },
            color = colors.tvLv2,
            fontSize = 14.sp,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().navigationBarsPadding(),
          state = visibleListState,
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (manageMode) 92.dp else 24.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val activeItems = if (selectedTab == ScheduleCategoryItemTab.TODO) {
            projection.todos.active
          } else {
            projection.schedules.upcoming
          }
          if (activeItems.isNotEmpty()) {
            item(key = "${selectedTab.name}-active-title") {
              ScheduleTodoSectionTitle(
                text = if (selectedTab == ScheduleCategoryItemTab.TODO) "未完成" else "未过期",
                allSelected = if (manageMode) {
                  activeItems.all { it.schedule.id in selectedIds }
                } else null,
                onToggleSelectAll = {
                  viewModel.toggleSelectSection(activeItems.map { it.schedule.id })
                },
              )
            }
            items(activeItems, key = ScheduleTodoItemUi::key) { item ->
              ScheduleCategoryItemCard(
                modifier = Modifier.animateItem(
                  fadeInSpec = tween(180),
                  placementSpec = tween(320),
                  fadeOutSpec = tween(160),
                ),
                item = item,
                viewModel = viewModel,
                manageMode = manageMode,
                selected = item.schedule.id in selectedIds,
                pinned = item.schedule.id in pinnedIds,
                onOpen = { editingItem = item },
                onTogglePin = {
                  pinnedIds = if (item.schedule.id in pinnedIds) {
                    pinnedIds.filterNot { it == item.schedule.id }
                  } else {
                    listOf(item.schedule.id) + pinnedIds
                  }
                  saveScheduleTodoPinnedIds(settings, pinnedIds)
                },
              )
            }
          }
          if (selectedTab == ScheduleCategoryItemTab.TODO && projection.todos.completed.isNotEmpty()) {
            item(key = "${selectedTab.name}-completed-title") {
              ScheduleTodoSectionTitle(
                text = "已完成",
                allSelected = if (manageMode) {
                  projection.todos.completed.all { it.schedule.id in selectedIds }
                } else null,
                onToggleSelectAll = {
                  viewModel.toggleSelectSection(projection.todos.completed.map { it.schedule.id })
                },
              )
            }
            items(projection.todos.completed, key = ScheduleTodoItemUi::key) { item ->
              ScheduleCategoryItemCard(
                modifier = Modifier.animateItem(
                  fadeInSpec = tween(180),
                  placementSpec = tween(320),
                  fadeOutSpec = tween(160),
                ),
                item = item,
                viewModel = viewModel,
                manageMode = manageMode,
                selected = item.schedule.id in selectedIds,
                pinned = item.schedule.id in pinnedIds,
                onOpen = { editingItem = item },
                onTogglePin = {
                  pinnedIds = if (item.schedule.id in pinnedIds) {
                    pinnedIds.filterNot { it == item.schedule.id }
                  } else {
                    listOf(item.schedule.id) + pinnedIds
                  }
                  saveScheduleTodoPinnedIds(settings, pinnedIds)
                },
              )
            }
          }
          if (selectedTab == ScheduleCategoryItemTab.SCHEDULE && projection.schedules.expired.isNotEmpty()) {
            item(key = "schedule-expired-title") {
              ScheduleTodoSectionTitle(
                text = "已过期",
                allSelected = if (manageMode) {
                  projection.schedules.expired.all { it.schedule.id in selectedIds }
                } else null,
                onToggleSelectAll = {
                  viewModel.toggleSelectSection(projection.schedules.expired.map { it.schedule.id })
                },
              )
            }
            items(projection.schedules.expired, key = ScheduleTodoItemUi::key) { item ->
              ScheduleCategoryItemCard(
                modifier = Modifier.animateItem(
                  fadeInSpec = tween(180),
                  placementSpec = tween(320),
                  fadeOutSpec = tween(160),
                ),
                item = item,
                viewModel = viewModel,
                manageMode = manageMode,
                selected = item.schedule.id in selectedIds,
                pinned = false,
                onOpen = { editingItem = item },
                onTogglePin = {},
              )
            }
          }
        }
      }
    }

    if (manageMode) {
      ScheduleTodoManageBar(
        selectedCount = selectedIds.size,
        totalCount = visibleIds.size,
        middleActionLabel = if (categoryId == null) null else "移出分组",
        onSelectAll = { viewModel.selectAll(visibleIds) },
        onMiddleAction = categoryId?.let { id ->
          { viewModel.removeSelectedFromCategory(id) }
        },
        onDelete = viewModel::batchDelete,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }

  editingItem?.let { item ->
    Window(dismissOnBackPress = null) {
      ScheduleExistingItemEditor(item, viewModel) { editingItem = null }
    }
  }
}
