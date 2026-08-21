package com.cyxbs.pages.schedule.ui.todo.figma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_TODO_DETAIL
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.api.ScheduleTodoDetailNavArgument
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.domain.repository.canSubmitScheduleMutation
import com.cyxbs.pages.schedule.ui.edit.EditScheduleModelState
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.edit.RepeatFreqOption
import com.cyxbs.pages.schedule.ui.edit.rememberEditScheduleModelState
import com.cyxbs.pages.schedule.ui.edit.area.EditScheduleCalendarArea
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoItemUi
import com.cyxbs.pages.schedule.ui.todo.projectScheduleTodo
import com.cyxbs.pages.schedule.ui.todo.toDomainOccurrence
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

/**
 * Figma 版待办详情的独立预览入口。
 *
 * 正式清单已改回共用 [com.cyxbs.pages.schedule.ui.edit.EditScheduleDialog]；这里完整保留设计稿方案，
 * 便于后续单独比对或继续试验，但清单列表不会再导航到此页面。
 */
@AppNav(route = NAV_SCHEDULE_TODO_DETAIL)
class ScheduleTodoDetailNavEntry : AppNavEntry<ScheduleTodoDetailNavArgument>() {

  /** 待办详情包含真实账号数据与修改能力，因此必须经过登录门禁。 */
  override fun isNeedLogin(argument: ScheduleTodoDetailNavArgument): Boolean = true

  /** 同一待办实例复用导航内容，避免重复入栈时创建两份详情观察。 */
  override fun getContentKey(argument: ScheduleTodoDetailNavArgument): String =
    "schedule_todo_detail_${argument.scheduleId}_${argument.recurrenceId}"

  /** 创建详情页 ViewModel，并由导航参数定位实时仓库快照中的精确实例。 */
  @Composable
  override fun Content(argument: ScheduleTodoDetailNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel() }
    ScheduleTodoDetailRoute(
      scheduleId = argument.scheduleId,
      recurrenceId = argument.recurrenceId,
      viewModel = viewModel,
      onBack = argument::popBackStack,
    )
  }
}

/**
 * 独立详情路由的数据装配层。
 *
 * 导航参数只携带 identity；页面初始化后从共享仓库重新投影最新值，保存成功则弹出当前详情路由。
 */
@Composable
internal fun ScheduleTodoDetailRoute(
  scheduleId: ScheduleId,
  recurrenceId: RecurrenceId?,
  viewModel: ScheduleMainViewModel,
  onBack: () -> Unit,
) {
  val snapshot by viewModel.snapshot.collectAsState()
  val viewerTimeZone = remember { TimeZone.currentSystemDefault() }
  val projection = remember(snapshot, viewerTimeZone) {
    projectScheduleTodo(snapshot, Clock.System.now(), viewerTimeZone)
  }
  val item = remember(projection, scheduleId, recurrenceId) {
    (projection.pending + projection.completed).firstOrNull {
      it.schedule.id == scheduleId &&
        (recurrenceId == null || it.occurrence.recurrenceId == recurrenceId)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.initialize()
  }

  if (item == null) {
    ScheduleTodoDetailUnavailable(
      loading = snapshot.status == ScheduleRepositoryStatus.Loading,
      onBack = onBack,
    )
    return
  }

  ScheduleTodoDetailPage(
    item = item,
    categories = snapshot.categories,
    editorEnabled = viewModel.mutationMode.canSubmitScheduleMutation(),
    onBack = onBack,
    onSyncToSchedule = {
      // Todo 与课表共用同一个 Schedule identity；这里跳转到同一资源的课表编辑入口，不复制数据。
      ScheduleMainNavArgument(item.schedule.id, item.occurrence.recurrenceId).navigate()
    },
    onSave = { state ->
      val recurrenceId = item.occurrence.recurrenceId
      val scope = if (recurrenceId == null) EditScope.ALL else EditScope.THIS_ONLY
      viewModel.saveSchedule(state, scope, recurrenceId)
      onBack()
    },
  )
}

/**
 * Figma 邮子清单的待办详情页。
 *
 * 页面直接编辑共享 Schedule 模型；待办侧只暴露一个截止时间点，重复、提醒、分类和备注仍与
 * SchedulePage 保持同一份数据。“同步到课表”只负责打开同一资源的课表编辑入口，不创建副本。
 */
@Composable
internal fun ScheduleTodoDetailPage(
  item: ScheduleTodoItemUi,
  categories: List<ScheduleCategory>,
  editorEnabled: Boolean,
  onBack: () -> Unit,
  onSyncToSchedule: () -> Unit,
  onSave: (EditScheduleModelState) -> Unit,
) {
  val colors = LocalAppColors.current
  val state = rememberEditScheduleModelState(
    editSchedule = item.schedule,
    occurrence = item.occurrence.toDomainOccurrence(),
  )
  var validationMessage by remember(item.key) { mutableStateOf<String?>(null) }
  var showDateEditor by remember(item.key) { mutableStateOf(false) }
  var dateEditorSnapshot by remember(item.key) { mutableStateOf<ScheduleTodoDateEditorSnapshot?>(null) }

  /** 校验通过才交给仓库；失败时留在详情页，避免静默丢失当前输入。 */
  fun submit() {
    if (!editorEnabled) return
    // Todo 设计只支持截止时间点；保存旧的时间段事项时以结束时刻收敛为 Deadline。
    state.isAllDay = false
    state.isInterval = false
    state.startTime = ""
    if (state.canConfirm) {
      onSave(state)
    } else {
      validationMessage = "请检查待办名称、时间与分组"
    }
  }

  Surface(
    color = colors.bottomBg,
    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      ScheduleTodoDetailHeader(
        editorEnabled = editorEnabled,
        onBack = onBack,
        onSave = ::submit,
      )
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ScheduleTodoDetailCard {
          ScheduleTodoCreateSection(title = "待办名称") {
            ScheduleTodoFilledInput(
              value = state.title.text.toString(),
              onValueChange = { value ->
                state.title.edit { replace(0, length, value.take(60)) }
                validationMessage = null
              },
              placeholder = "要做点什么？",
              modifier = Modifier.fillMaxWidth().height(48.dp),
            )
          }
        }

        ScheduleTodoDeadlineCard(
          timeText = state.endTime.toTodoDetailTimeText(item.timeText),
          onChangeDate = {
            // 日期编辑器直接复用同一 state；进入前保留原值，取消或手势关闭时完整还原。
            dateEditorSnapshot = ScheduleTodoDateEditorSnapshot(
              startTime = state.startTime,
              endTime = state.endTime,
              isAllDay = state.isAllDay,
              isInterval = state.isInterval,
            )
            showDateEditor = true
          },
          onSyncToSchedule = onSyncToSchedule,
        )

        ScheduleTodoDetailCard {
          ScheduleTodoCreateSection(title = "重复") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              listOf(
                RepeatFreqOption.NONE to "不重复",
                RepeatFreqOption.DAILY to "每天",
                RepeatFreqOption.WEEKLY to "自定义",
              ).forEach { (frequency, label) ->
                ScheduleTodoCreateChip(
                  text = label,
                  selected = when (frequency) {
                    RepeatFreqOption.WEEKLY -> state.recurrence.freq !in
                        setOf(RepeatFreqOption.NONE, RepeatFreqOption.DAILY)

                    else -> state.recurrence.freq == frequency
                  },
                  width = 86.dp,
                  onClick = {
                    state.recurrence = state.recurrence.copy(freq = frequency)
                    validationMessage = null
                  },
                )
              }
            }
          }
        }

        ScheduleTodoDetailCard {
          ScheduleTodoCreateSection(
            title = "提醒时间",
            subtitle = "（到点提醒）",
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(
                15 to "提前15分钟",
                30 to "提前30分钟",
                60 to "自定义",
              ).forEach { (minutes, label) ->
                ScheduleTodoCreateChip(
                  text = label,
                  selected = if (minutes == 60) {
                    state.remindMinutes !in setOf(-1, 15, 30)
                  } else {
                    state.remindMinutes == minutes
                  },
                  width = 104.dp,
                  enabled = state.endTime.isNotBlank(),
                  onClick = {
                    state.remindMinutes = minutes
                    validationMessage = null
                  },
                )
              }
            }
          }
        }

        ScheduleTodoDetailCard {
          ScheduleTodoCreateSection(title = "分组") {
            if (categories.isEmpty()) {
              Text(
                text = "暂无可用分组",
                color = colors.tvLv3,
                fontSize = 13.sp,
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                categories.forEach { category ->
                  ScheduleTodoCreateChip(
                    text = category.name,
                    selected = state.categoryId == category.id,
                    width = 86.dp,
                    onClick = {
                      state.categoryId = category.id
                      validationMessage = null
                    },
                  )
                }
              }
            }
          }
        }

        ScheduleTodoDetailCard {
          ScheduleTodoCreateSection(
            title = "备注",
            subtitle = "（100字以内）",
          ) {
            ScheduleTodoFilledInput(
              value = state.detail.text.toString(),
              onValueChange = { value ->
                state.detail.edit { replace(0, length, value.take(100)) }
                validationMessage = null
              },
              placeholder = "补充点细节吧（非必填）",
              singleLine = false,
              textAlign = TextAlign.Start,
              modifier = Modifier.fillMaxWidth().height(148.dp),
            )
          }
        }

        validationMessage?.let { message ->
          Text(
            text = message,
            color = MaterialTheme.colors.error,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
      }
    }
  }

  ScheduleTodoDateEditorSheet(
    show = showDateEditor,
    state = state,
    onCancel = {
      dateEditorSnapshot?.restoreTo(state)
      dateEditorSnapshot = null
      showDateEditor = false
    },
    onConfirm = {
      dateEditorSnapshot = null
      validationMessage = null
      showDateEditor = false
    },
  )
}

/** 详情页顶部对应 Figma 的返回、居中标题和右上角胶囊形保存按钮。 */
@Composable
private fun ScheduleTodoDetailHeader(
  editorEnabled: Boolean,
  onBack: () -> Unit,
  onSave: () -> Unit,
) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.align(Alignment.CenterStart),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "返回清单",
        tint = colors.tvLv1,
      )
    }
    Text(
      text = "待办详情",
      color = colors.tvLv1,
      fontSize = 21.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.align(Alignment.Center),
    )
    Surface(
      color = colors.positive.copy(alpha = if (editorEnabled) 1f else 0.4f),
      contentColor = if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .clickableNoIndicator(enabled = editorEnabled, onClick = onSave),
    ) {
      Text(
        text = "保存",
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
      )
    }
  }
}

/** 每一组详情字段使用独立白色圆角卡片，保持页面层级与 Figma 一致。 */
@Composable
private fun ScheduleTodoDetailCard(
  content: @Composable () -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.middleBg,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      content()
    }
  }
}

/**
 * 新版 Figma 的截止时间卡片。
 *
 * 卡片右上角为 30dp 的独立日期按钮，主体轮廓按导出 SVG 为按钮预留缺口；日期文案只读，
 * 避免继续暴露旧版自由文本输入。同步课表操作仍位于同一张卡片下半部。
 */
@Composable
private fun ScheduleTodoDeadlineCard(
  timeText: String,
  onChangeDate: () -> Unit,
  onSyncToSchedule: () -> Unit,
) {
  val colors = LocalAppColors.current
  Box(modifier = Modifier.fillMaxWidth().height(129.dp)) {
    Surface(
      color = colors.middleBg,
      shape = ScheduleTodoDeadlineCardShape,
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 13.dp, end = 65.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "截止时间",
            color = colors.tvLv2,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.weight(1f))
          Text(
            text = timeText,
            color = colors.tvLv3,
            fontSize = 14.sp,
          )
        }
        ScheduleTodoSyncToScheduleButton(
          onClick = onSyncToSchedule,
          modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 70.dp),
        )
      }
    }
    Surface(
      color = colors.negative,
      shape = RoundedCornerShape(5.dp),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 7.dp, end = 9.dp)
        .size(30.dp)
        .clickableNoIndicator(onClick = onChangeDate),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          painter = painterResource(ConfigRes.configIcChangeDate()),
          contentDescription = "更改截止日期",
          tint = colors.positive,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

/** 导出 SVG 中日期卡片的 343×129 轮廓，右上角缺口用于容纳独立按钮。 */
private val ScheduleTodoDeadlineCardShape = GenericShape { size, _ ->
  val x = size.width / 343f
  val y = size.height / 129f
  moveTo(294f * x, 32.289f * y)
  cubicTo(294f * x, 38.916f * y, 299.373f * x, 44.289f * y, 306f * x, 44.289f * y)
  lineTo(331f * x, 44.289f * y)
  cubicTo(337.627f * x, 44.289f * y, 343f * x, 49.662f * y, 343f * x, 56.289f * y)
  lineTo(343f * x, 113f * y)
  cubicTo(343f * x, 121.837f * y, 335.837f * x, 129f * y, 327f * x, 129f * y)
  lineTo(16f * x, 129f * y)
  cubicTo(7.163f * x, 129f * y, 0f, 121.837f * y, 0f, 113f * y)
  lineTo(0f, 16f * y)
  cubicTo(0f, 7.163f * y, 7.163f * x, 0f, 16f * x, 0f)
  lineTo(282f * x, 0f)
  cubicTo(288.627f * x, 0f, 294f * x, 5.373f * y, 294f * x, 12f * y)
  close()
}

/** 日期选择层只修改日期，时间点沿用原值；取消时由调用方恢复进入前的快照。 */
@Composable
private fun ScheduleTodoDateEditorSheet(
  show: Boolean,
  state: EditScheduleModelState,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
) {
  ScheduleBottomSheet(
    show = show,
    onDismiss = onCancel,
    onDismissRequest = { true },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "取消",
          color = LocalAppColors.current.tvLv1,
          fontSize = 15.sp,
          modifier = Modifier.clickableNoIndicator(onClick = onCancel),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = "更改日期",
          color = LocalAppColors.current.tvLv1,
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = "确定",
          color = LocalAppColors.current.positive,
          fontSize = 15.sp,
          modifier = Modifier.clickableNoIndicator(onClick = onConfirm),
        )
      }
      EditScheduleCalendarArea(state = state)
    }
  }
}

/** 进入日期编辑前的最小可恢复状态；仅用于取消编辑，不进入仓库或网络协议。 */
private data class ScheduleTodoDateEditorSnapshot(
  val startTime: String,
  val endTime: String,
  val isAllDay: Boolean,
  val isInterval: Boolean,
) {
  /** 将取消前保存的四个时间字段还原到页面编辑 state。 */
  fun restoreTo(state: EditScheduleModelState) {
    state.startTime = startTime
    state.endTime = endTime
    state.isAllDay = isAllDay
    state.isInterval = isInterval
  }
}

/** 将编辑器内部完整日期串收敛为设计稿的“月日 时分”展示格式。 */
private fun String.toTodoDetailTimeText(fallback: String): String {
  val parsed = parseScheduleDateTime(this) ?: return fallback
  val minute = parsed.minuteOfDay ?: return "${parsed.date.monthNumber}月${parsed.date.dayOfMonth}日"
  return "${parsed.date.monthNumber}月${parsed.date.dayOfMonth}日 " +
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
}

/** 初始化期间或目标已经不存在时保留独立详情页的返回能力，不回退打开旧编辑弹窗。 */
@Composable
private fun ScheduleTodoDetailUnavailable(
  loading: Boolean,
  onBack: () -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.bottomBg,
    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      ScheduleTodoDetailHeader(
        editorEnabled = false,
        onBack = onBack,
        onSave = {},
      )
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = if (loading) "正在加载待办…" else "待办不存在或已被删除",
          color = colors.tvLv3,
          fontSize = 15.sp,
        )
      }
    }
  }
}

/**
 * Figma 详情页的“同步到课表”操作。
 *
 * 左侧 35dp 图标和右侧 38dp 按钮直接采用导出 SVG 的尺寸；按钮不展示设计稿下方的说明小字。
 */
@Composable
private fun ScheduleTodoSyncToScheduleButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      color = colors.positive,
      shape = RoundedCornerShape(5.dp),
      modifier = Modifier.size(35.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          painter = painterResource(ConfigRes.configIcCalendarSync()),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Surface(
      color = colors.positive,
      contentColor = if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1,
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier
        .size(width = 229.dp, height = 38.dp)
        .clickableNoIndicator(onClick = onClick),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = "同步到课表",
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}
