package com.cyxbs.pages.schedule.ui.todo.figma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetScope
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.ui.edit.EditScheduleModelState
import com.cyxbs.pages.schedule.ui.edit.RecurrenceDraft
import com.cyxbs.pages.schedule.ui.edit.RepeatFreqOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 保留的 Figma 邮子清单新建待办底部弹窗。
 *
 * 当前生产入口已经切回长期迭代的 EditScheduleDialog；本实现仅作为设计方案存档与后续视觉对照，
 * 不参与清单页默认交互。弹层复用项目 [BottomSheetCompose]，只采集清单语义并转换成
 * [EditScheduleModelState]。保存仍进入共享 Schedule 仓库，因此清单页和 SchedulePage 使用同一数据；
 * 时间严格按设计稿收敛为单个截止时间点，不提供时间段入口。
 */
@Composable
internal fun ScheduleTodoCreateDialog(
  show: Boolean,
  categories: List<ScheduleCategory>,
  onDismiss: () -> Unit,
  onSave: (EditScheduleModelState) -> Unit,
) {
  if (!show) return

  val colors = LocalAppColors.current
  var title by remember { mutableStateOf("") }
  var pointTime by remember { mutableStateOf("") }
  var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id) }
  var repeatFrequency by remember { mutableStateOf(RepeatFreqOption.NONE) }
  var reminderMinutes by remember { mutableStateOf(30) }
  var note by remember { mutableStateOf("") }
  var validationMessage by remember { mutableStateOf<String?>(null) }

  /** 将当前视觉表单转换为共享编辑模型；校验失败时保持弹窗展开，不产生仓库写入。 */
  fun submit() {
    val state = EditScheduleModelState(origin = null)
    state.title.edit { replace(0, length, title.trim()) }
    state.detail.edit { replace(0, length, note.trim()) }
    state.categoryId = categoryId
    state.isAllDay = false
    state.isInterval = false
    state.startTime = ""
    state.endTime = pointTime.trim()
    state.recurrence = RecurrenceDraft(freq = repeatFrequency)
    state.remindMinutes = reminderMinutes
    if (state.canConfirm) {
      onSave(state)
    } else {
      validationMessage = "请检查标题、时间与分组是否填写完整"
    }
  }

  ScheduleTodoCreateBottomSheet(onDismiss = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(668.dp)
        .navigationBarsPadding()
        .then(bottomSheetDraggable()),
    ) {
      // Figma 中取消按钮位于主体异形轮廓后方，主体背景会遮住按钮下半段。
      Surface(
        color = colors.negative,
        contentColor = colors.tvLv3,
        shape = RoundedCornerShape(21.dp),
        modifier = Modifier
          .offset(x = 33.dp, y = 29.dp)
          .size(width = 85.dp, height = 110.dp)
          .clickableNoIndicator(onClick = onDismiss),
      ) {
        Box(contentAlignment = Alignment.TopStart) {
          Text(
            text = "取消",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, top = 23.dp),
          )
        }
      }

      Box(
        modifier = Modifier
          .matchParentSize()
          .shadow(4.dp, ScheduleTodoCreateSheetShape, clip = false)
          .background(colors.topBg, ScheduleTodoCreateSheetShape),
      )

      ScheduleTodoCreateHeader(onSave = ::submit)

      Column(
        modifier = Modifier
          .matchParentSize()
          .padding(top = 104.898.dp)
          .clip(ScheduleTodoCreateScrollClipShape)
          .verticalScroll(rememberScrollState())
          .padding(start = 32.dp, top = 9.dp, end = 32.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
      ) {
        ScheduleTodoFilledInput(
          value = title,
          onValueChange = {
            title = it.take(60)
            validationMessage = null
          },
          placeholder = "要做点什么？（必填）",
          modifier = Modifier.width(177.dp).height(34.dp),
        )

        ScheduleTodoTimeInput(
          label = "截止时间",
          value = pointTime,
          onValueChange = {
            pointTime = it
            validationMessage = null
          },
        )

        ScheduleTodoCreateSection(title = "分组") {
          if (categories.isEmpty()) {
            Text("暂无可用分组", color = colors.tvLv3, fontSize = 13.sp)
          } else {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              categories.take(3).forEach { category ->
                ScheduleTodoCreateChip(
                  text = category.name,
                  selected = categoryId == category.id,
                  width = 80.dp,
                  onClick = {
                    categoryId = category.id
                    validationMessage = null
                  },
                )
              }
            }
          }
        }

        ScheduleTodoCreateSection(title = "重复") {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
              RepeatFreqOption.NONE to "不重复",
              RepeatFreqOption.DAILY to "每天",
              RepeatFreqOption.WEEKLY to "自定义",
            ).forEach { (frequency, label) ->
              ScheduleTodoCreateChip(
                text = label,
                selected = repeatFrequency == frequency,
                width = 80.dp,
                onClick = { repeatFrequency = frequency },
              )
            }
          }
        }

        ScheduleTodoCreateSection(
          title = "提醒时间",
          subtitle = "（需先设置时间）",
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
              15 to "提前15分钟",
              30 to "提前30分钟",
              60 to "自定义"
            ).forEach { (minutes, label) ->
              ScheduleTodoCreateChip(
                text = label,
                selected = reminderMinutes == minutes,
                width = 100.dp,
                enabled = pointTime.isNotBlank(),
                onClick = { reminderMinutes = minutes },
              )
            }
          }
        }

        ScheduleTodoCreateSection(title = "备注", subtitle = "（100字以内）") {
          ScheduleTodoFilledInput(
            value = note,
            onValueChange = { note = it.take(100) },
            placeholder = "补充点细节吧（非必填）",
            singleLine = false,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().height(148.dp),
          )
        }

        validationMessage?.let {
          Text(text = it, color = MaterialTheme.colors.error, fontSize = 13.sp)
        }
      }
    }
  }
}

/**
 * 创建页独立的 BottomSheet 生命周期包装。
 *
 * 首次完成测量后自动展开；点击遮罩、返回键或下滑至底部都会先执行收起动画，再通知外层移除表单。
 */
@Composable
private fun ScheduleTodoCreateBottomSheet(
  onDismiss: () -> Unit,
  content: @Composable BottomSheetScope.() -> Unit,
) {
  val latestOnDismiss = rememberUpdatedState(onDismiss)
  val state = remember {
    BottomSheetState(
      onDismissRequest = { hideSuspend() },
      hideable = true,
    )
  }
  LaunchedEffect(state) {
    delay(100)
    state.expandAsync()
  }
  LaunchedEffect(state) {
    state.stateFlow.first { it == BottomSheetValueState.Expanded }
    state.stateFlow.first {
      it == BottomSheetValueState.Hide || it == BottomSheetValueState.Collapsed
    }
    latestOnDismiss.value()
  }
  BottomSheetCompose(
    bottomSheetState = state,
    peekHeight = 0.dp,
    dismissOnBackPress = true,
    dismissOnClickOutside = true,
    scrimColor = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
    content = content,
  )
}

/**
 * Figma `Subtract` 节点的精确轮廓。
 *
 * 设计基准宽 375：左侧主体在 y=104.898 开始，经两段 30dp 圆角抬升到 x=136/y=8 的主面板顶部；
 * 这段空出来的阶梯用于露出后方取消按钮。左侧异形尺寸保持设计 dp，右侧圆角锚定实际宽度，
 * 因此在 Desktop 宽窗口中不会把 85dp 的取消按钮错误拉伸。
 */
private object ScheduleTodoCreateSheetShape : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    fun x(value: Float) = with(density) { value.dp.toPx() }
    fun y(value: Float) = with(density) { value.dp.toPx() }
    return Outline.Generic(
      Path().apply {
        moveTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(0f, y(134.898f))
        cubicTo(0f, y(118.330f), x(13.4315f), y(104.898f), x(30f), y(104.898f))
        lineTo(x(76f), y(104.898f))
        cubicTo(x(92.5685f), y(104.898f), x(106f), y(91.467f), x(106f), y(74.8984f))
        lineTo(x(106f), y(38f))
        cubicTo(x(106f), y(21.4315f), x(119.431f), y(8f), x(136f), y(8f))
        lineTo(size.width - x(30f), y(8f))
        cubicTo(size.width - x(13.431f), y(8f), size.width, y(21.4315f), size.width, y(38f))
        close()
      },
    )
  }
}

/**
 * 表单滚动区的固定裁切边界。
 *
 * 滚动内容从取消按钮下方的主体圆角开始，左上角沿 Figma 的 30dp 圆角进入正文；标题、取消与保存按钮
 * 位于裁切线以上的固定层，不会跟随正文滚动，也不会被滚动内容覆盖。
 */
private object ScheduleTodoCreateScrollClipShape : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radius = with(density) { 30.dp.toPx() }
    return Outline.Generic(
      Path().apply {
        moveTo(radius, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(0f, radius)
        cubicTo(0f, radius * 0.4477f, radius * 0.4477f, 0f, radius, 0f)
        close()
      },
    )
  }
}

/** 顶部位于异形主体的高位区域，只包含居中标题与圆形保存按钮。 */
@Composable
private fun ScheduleTodoCreateHeader(
  onSave: () -> Unit,
) {
  val colors = LocalAppColors.current
  Box(modifier = Modifier.fillMaxWidth().height(114.dp)) {
    Text(
      text = "新建待办",
      color = colors.tvLv1,
      fontSize = 26.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.align(Alignment.Center),
    )
    Surface(
      color = colors.positive,
      shape = RoundedCornerShape(22.dp),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 10.dp, end = 10.dp)
        .size(44.dp)
        .clickableNoIndicator(onClick = onSave),
    ) {
      Box(contentAlignment = Alignment.Center) {
        ScheduleTodoSaveIcon(
          color = if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1,
          modifier = Modifier.size(26.dp),
        )
      }
    }
  }
}

/**
 * Figma `保存 1` SVG 的 Compose Path 实现。
 *
 * 两条路径逐点对应 26×26 viewBox；仅把 SVG 的固定填充色替换为主题色，几何轮廓不做 Material 图标替代。
 */
@Composable
private fun ScheduleTodoSaveIcon(
  color: Color,
  modifier: Modifier = Modifier,
) {
  val bodyPath = remember {
    Path().apply {
      moveTo(20.7949f, 1.65039f)
      lineTo(5.20508f, 1.65039f)
      cubicTo(3.24492f, 1.65039f, 1.65039f, 3.24492f, 1.65039f, 5.20508f)
      lineTo(1.65039f, 20.7949f)
      cubicTo(1.65039f, 22.7551f, 3.24492f, 24.3496f, 5.20508f, 24.3496f)
      lineTo(20.7949f, 24.3496f)
      cubicTo(22.7551f, 24.3496f, 24.3496f, 22.7551f, 24.3496f, 20.7949f)
      lineTo(24.3496f, 5.20508f)
      cubicTo(24.3496f, 3.24492f, 22.7551f, 1.65039f, 20.7949f, 1.65039f)
      close()

      moveTo(9.13047f, 3.68164f)
      lineTo(16.8695f, 3.68164f)
      lineTo(16.8695f, 10.3035f)
      cubicTo(16.8695f, 11.1059f, 16.217f, 11.7609f, 15.4121f, 11.7609f)
      lineTo(10.5854f, 11.7609f)
      cubicTo(9.78301f, 11.7609f, 9.12793f, 11.1084f, 9.12793f, 10.3035f)
      lineTo(9.12793f, 3.68164f)
      lineTo(9.13047f, 3.68164f)
      close()

      moveTo(22.3184f, 20.7949f)
      cubicTo(22.3184f, 21.6354f, 21.6354f, 22.3184f, 20.7949f, 22.3184f)
      lineTo(5.20508f, 22.3184f)
      cubicTo(4.36465f, 22.3184f, 3.68164f, 21.6354f, 3.68164f, 20.7949f)
      lineTo(3.68164f, 5.20508f)
      cubicTo(3.68164f, 4.36465f, 4.36465f, 3.68164f, 5.20508f, 3.68164f)
      lineTo(7.09922f, 3.68164f)
      lineTo(7.09922f, 10.3035f)
      cubicTo(7.09922f, 12.2256f, 8.66328f, 13.7922f, 10.5879f, 13.7922f)
      lineTo(15.4146f, 13.7922f)
      cubicTo(17.3367f, 13.7922f, 18.9033f, 12.2281f, 18.9033f, 10.3035f)
      lineTo(18.9033f, 3.68164f)
      lineTo(20.7949f, 3.68164f)
      cubicTo(21.6354f, 3.68164f, 22.3184f, 4.36465f, 22.3184f, 5.20508f)
      lineTo(22.3184f, 20.7949f)
      close()
    }
  }
  val switchPath = remember {
    Path().apply {
      moveTo(14.5818f, 9.98101f)
      cubicTo(15.1429f, 9.98101f, 15.5974f, 9.52651f, 15.5974f, 8.96538f)
      lineTo(15.5974f, 6.58374f)
      cubicTo(15.5974f, 6.02261f, 15.1429f, 5.56812f, 14.5818f, 5.56812f)
      cubicTo(14.0207f, 5.56812f, 13.5662f, 6.02261f, 13.5662f, 6.58374f)
      lineTo(13.5662f, 8.96538f)
      cubicTo(13.5662f, 9.52397f, 14.0207f, 9.98101f, 14.5818f, 9.98101f)
      close()
    }
  }
  Canvas(modifier = modifier) {
    val scale = minOf(size.width, size.height) / 26f
    withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
      drawPath(bodyPath, color)
      drawPath(switchPath, color)
    }
  }
}

/** Figma 表单分区由标题行和填充式控件组成，不再使用 Material OutlinedTextField。 */
@Composable
internal fun ScheduleTodoCreateSection(
  title: String,
  subtitle: String? = null,
  content: @Composable () -> Unit,
) {
  val colors = LocalAppColors.current
  Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(text = title, color = colors.tvLv3, fontSize = 15.sp, fontWeight = FontWeight.Medium)
      subtitle?.let { Text(text = it, color = colors.tvLv3, fontSize = 13.sp) }
    }
    content()
  }
}

/** 时间行沿用设计稿 47dp 填充容器；值由共享编辑模型按分钟级格式解析。 */
@Composable
internal fun ScheduleTodoTimeInput(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(47.dp)
      .background(colors.negative, RoundedCornerShape(16.dp))
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, color = colors.tvLv3, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.weight(1f))
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = TextStyle(
        color = colors.tvLv3,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
      ),
      decorationBox = { inner ->
        if (value.isEmpty()) {
          Text("年/月/日 --:--", color = colors.tvLv3.copy(alpha = 0.7f), fontSize = 16.sp)
        }
        inner()
      },
      modifier = Modifier.width(178.dp),
    )
  }
}

/** 填充式文本输入复刻设计稿的灰色圆角块，并限制文本颜色来自应用主题。 */
@Composable
internal fun ScheduleTodoFilledInput(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier,
  singleLine: Boolean = true,
  textAlign: TextAlign = TextAlign.Center,
) {
  val colors = LocalAppColors.current
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = singleLine,
    textStyle = TextStyle(
      color = colors.tvLv3,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      textAlign = textAlign,
    ),
    decorationBox = { inner ->
      Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
      ) {
        if (value.isEmpty()) {
          Text(
            text = placeholder,
            color = colors.tvLv3.copy(alpha = 0.72f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
          )
        }
        inner()
      }
    },
    modifier = modifier.background(
      colors.negative,
      RoundedCornerShape(if (singleLine) 11.dp else 20.dp)
    ),
  )
}

/** 三列选择按钮采用 Figma 固定宽度，选中态只切换主题强调色文字。 */
@Composable
internal fun ScheduleTodoCreateChip(
  text: String,
  selected: Boolean,
  width: Dp,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.negative.copy(alpha = if (enabled) 1f else 0.45f),
    contentColor = (if (selected) colors.positive else colors.tvLv3)
      .copy(alpha = if (enabled) 1f else 0.45f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .size(width = width, height = 40.dp)
      .clickable(enabled = enabled, onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = text,
        fontSize = if (width >= 100.dp) 14.sp else 15.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}
