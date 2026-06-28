package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 添加 / 详情两个编辑界面共用的原子控件与纯函数，保证两处视觉与行为一致。
 *
 * 配色统一走 [LocalAppColors] 语义色（支持暗黑），对齐老安卓端的填充输入框 + 胶囊 chip 风格。
 */

/**
 * 填充式输入框：圆角纯色底（[com.cyxbs.components.config.compose.theme.AppColor.bottomBg]），
 * 无下划线，复刻老端 `todo_shape_inner_add_thing_ev`。
 *
 * 基于 [TextFieldState]（新版 BasicTextField），文本由 [state] 持有，无需 value/onValueChange 回写。
 *
 * @param maxLength 限制最大字符数（如备注 100 字）；null 不限制。
 * @param cornerRadius 单行标题用 22dp（胶囊），多行备注用 12dp。
 * @param minHeight 最小高度；多行备注传更大值。
 */
@Composable
fun ScheduleFilledTextField(
  state: TextFieldState,
  hint: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  singleLine: Boolean = false,
  maxLength: Int? = null,
  cornerRadius: Dp = 12.dp,
  minHeight: Dp = 44.dp,
) {
  val colors = LocalAppColors.current
  // 输入框背景对齐老安卓端：日间 #E8F1FC（淡蓝），夜间 #1F1F1F。
  val fieldBg = 0xFFE8F1FC.dark(0xFF1F1F1F)
  BasicTextField(
    state = state,
    enabled = enabled,
    lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
    inputTransformation = maxLength?.let { InputTransformation.maxLength(it) },
    textStyle = TextStyle(color = colors.tvLv3, fontSize = 15.sp),
    cursorBrush = SolidColor(colors.positive),
    modifier = modifier
      .background(fieldBg, RoundedCornerShape(cornerRadius))
      .defaultMinSize(minHeight = minHeight)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    decorator = { inner ->
      Box(
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
      ) {
        if (state.text.isEmpty()) {
          Text(text = hint, color = colors.tvLv3.copy(alpha = 0.4f), fontSize = 15.sp)
        }
        inner()
      }
    },
  )
}

/** 时间类型分段切换：截止 / 时间段，二选一。 */
@Composable
fun ScheduleTimeTypeToggle(
  isInterval: Boolean,
  enabled: Boolean = true,
  onChange: (Boolean) -> Unit,
) {
  Row(modifier = Modifier) {
    TimeTypeChip(
      text = "时间段",
      selected = isInterval,
      modifier = Modifier.weight(1f),
      onClick = { if (enabled) onChange(true) },
    )
    Spacer(modifier = Modifier.width(8.dp))
    TimeTypeChip(
      text = "截止",
      selected = !isInterval,
      modifier = Modifier.weight(1f),
      onClick = { if (enabled) onChange(false) },
    )
  }
}

@Composable
private fun TimeTypeChip(
  text: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  val accent = colors.positive
  Text(
    text = text,
    textAlign = TextAlign.Center,
    fontSize = 14.sp,
    color = if (selected) accent else colors.tvLv3.copy(alpha = 0.5f),
    modifier = modifier
      .border(
        width = 1.dp,
        color = if (selected) accent else colors.tvLv3.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
      )
      .background(
        color = if (selected) accent.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(8.dp),
      )
      .clickable(onClick = onClick)
      .padding(vertical = 9.dp),
  )
}

/** 胶囊式重复 chip：positive 淡底 + positive 文字，可选删除 ×。 */
@Composable
fun ScheduleRepeatChip(
  label: String,
  onRemove: (() -> Unit)?,
) {
  val accent = LocalAppColors.current.positive
  Row(
    modifier = Modifier
      .background(accent.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
      .padding(start = 14.dp, end = if (onRemove != null) 8.dp else 14.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
    if (onRemove != null) {
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "移除",
        tint = accent,
        modifier = Modifier.size(15.dp).clickable(onClick = onRemove),
      )
    }
  }
}

/** todo 分类的中文显示名：内置三类映射成中文，自定义分类原样显示。 */
internal fun categoryLabel(type: String): String = when (type) {
  ScheduleEntity.TYPE_STUDY -> "学习"
  ScheduleEntity.TYPE_LIFE -> "生活"
  ScheduleEntity.TYPE_OTHER -> "其他"
  else -> type
}

/** 分组候选项展示数据：[type] 分类值，[used] 是否被某个 todo 使用（未使用的可删除）。 */
data class CategoryDisplay(val type: String, val used: Boolean)

/**
 * 计算分组候选的展示顺序：
 * - 已使用的在前，按「使用顺序」= 最近使用时间（该分类下 todo 的最大 lastModifyTime）倒序；
 * - 未使用的在后，按字符自身升序。
 *
 * @param pool 持久化的候选池（默认三类 + 自定义）。
 * @param todos 所有 todo，用于判断使用情况与最近使用时间。
 */
fun orderedCategories(pool: List<String>, todos: List<ScheduleEntity>): List<CategoryDisplay> {
  val recency = HashMap<String, Long>()
  for (t in todos) {
    val type = t.type
    if (type.isBlank()) continue
    val prev = recency[type]
    if (prev == null || t.lastModifyTime > prev) recency[type] = t.lastModifyTime
  }
  val used = recency.keys.sortedByDescending { recency.getValue(it) }
  // 未使用项按「显示名」字符升序（默认三类按 学习/生活/其他 的中文，而非 study/life/other）。
  val unused = (pool.toSet() - recency.keys).sortedBy { categoryLabel(it) }
  return used.map { CategoryDisplay(it, true) } + unused.map { CategoryDisplay(it, false) }
}

/**
 * 分组内联选择器：候选 chip 平铺（参考课表 HistoryChip 样式）+ 末尾「+」按钮。
 *
 * - 未被任何 todo 使用的分类（含默认三类）右上角带删除角标，可移除。
 * - 点「+」就地变成一个带下划线的可编辑 item，自动聚焦弹出键盘，回车确认即新增并选中。
 *
 * @param categories 已排序的候选项（见 [orderedCategories]）。
 * @param onSelect 选中某分类。
 * @param onAdd 新增自定义分类（持久化到候选池）。
 * @param onDelete 删除某分类（仅未使用项会触发）。
 */
@Composable
fun CategorySelector(
  selectedType: String,
  categories: List<CategoryDisplay>,
  onSelect: (String) -> Unit,
  onAdd: (String) -> Unit,
  onDelete: (String) -> Unit,
) {
  val chipBg = 0xFFE8F1FC.dark(0xFF1F1F1F)
  var adding by remember { mutableStateOf(false) }
  val textState = rememberTextFieldState()

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    categories.forEach { item ->
      CategoryChip(
        label = categoryLabel(item.type),
        selected = item.type == selectedType,
        chipBg = chipBg,
        onClick = { onSelect(item.type) },
        // 未被任何 todo 使用的分类（含默认三类）可删除。
        onDelete = if (!item.used) ({ onDelete(item.type) }) else null,
      )
    }

    if (adding) {
      CategoryAddField(
        textState = textState,
        chipBg = chipBg,
        onConfirm = {
          val v = textState.text.toString().trim()
          if (v.isNotEmpty()) {
            onAdd(v)
            onSelect(v)
          }
          textState.clearText()
          adding = false
        },
      )
    } else {
      CategoryAddButton(
        chipBg = chipBg,
        onClick = {
          textState.clearText()
          adding = true
        },
      )
    }
  }
}

/**
 * 「+」点开后就地变成的可编辑 chip：自动聚焦弹键盘，随输入逐字扩宽，回车确认（[onConfirm]）。
 * [textState] 由 [CategorySelector] 持有，确认后由其清空并切回 [CategoryAddButton]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryAddField(
  textState: TextFieldState,
  chipBg: androidx.compose.ui.graphics.Color,
  onConfirm: () -> Unit,
) {
  val colors = LocalAppColors.current
  val underlineColor = colors.tvLv3.copy(alpha = 0.4f)
  val fieldStyle = TextStyle(color = colors.tvLv3, fontSize = 14.sp)
  val textMeasurer = rememberTextMeasurer()
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() } // 点击 + 号切换为输入框后就自动弹起键盘
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(chipBg)
      .height(34.dp)
      .padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    BasicTextField(
      state = textState,
      textStyle = fieldStyle,
      cursorBrush = SolidColor(colors.positive),
      lineLimits = TextFieldLineLimits.SingleLine,
      inputTransformation = InputTransformation.maxLength(5),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      onKeyboardAction = { onConfirm() },
      modifier = Modifier
        .layout { measurable, constraints ->
          // 动态宽度：用 TextMeasurer 量出当前文本（空时量 hint）的宽度，再给 BasicTextField 显式定宽。
          // 为什么要测量而不是靠它自己包裹内容：BasicTextField 默认按父约束的 maxWidth 占满宽度（内部
          // CoreTextField 把编辑区铺成最大宽度以便横向输入/滚动光标），并不会缩到文字宽度；放进 FlowRow
          // 也会撑满该行剩余宽度。所以这里手动测量文字宽度来定宽，实现「从 hint 宽度起随输入逐字扩宽」，
          // 末尾 +1dp 留出光标余量。
          val text = textState.text.ifEmpty { "分组名" }.toString()
          val textWidth = textMeasurer.measure(text, fieldStyle).size.width + 3
          val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = textWidth))
          layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
          }
        }
        // 下划线
        .drawBehind {
          val y = size.height
          drawLine(
            color = underlineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
          )
        }.focusRequester(focusRequester),
      decorator = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (textState.text.isEmpty()) {
            Text(text = "分组名", color = colors.tvLv3.copy(alpha = 0.4f), fontSize = 14.sp)
          }
          innerTextField()
        }
      },
    )
  }
}

/** 「+」按钮：与 chip 同背景色的圆，点击切换为 [CategoryAddField]。 */
@Composable
private fun CategoryAddButton(
  chipBg: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier
      .size(34.dp)
      .clip(CircleShape)
      .background(chipBg)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Add,
      contentDescription = "自定义分组",
      tint = colors.tvLv3.copy(alpha = 0.7f),
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun CategoryChip(
  label: String,
  selected: Boolean,
  chipBg: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  val colors = LocalAppColors.current
  Box {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (selected) colors.positive.copy(alpha = 0.15f) else chipBg)
        .clickable(onClick = onClick)
        .height(34.dp)
        .padding(horizontal = 14.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontSize = 14.sp,
        color = if (selected) colors.positive else colors.tvLv3,
      )
    }
    if (onDelete != null) {
      // 右上角删除角标
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(16.dp)
          .clip(CircleShape)
          .background(colors.tvLv3.copy(alpha = 0.45f))
          .clickable(onClick = onDelete),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "删除分组",
          tint = colors.whiteBlack,
          modifier = Modifier.size(10.dp),
        )
      }
    }
  }
}
