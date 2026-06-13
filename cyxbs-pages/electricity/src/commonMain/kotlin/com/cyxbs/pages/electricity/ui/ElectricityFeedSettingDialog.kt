package com.cyxbs.pages.electricity.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.electricity.config.BUILDING_NAMES
import com.cyxbs.pages.electricity.config.BUILDING_NAMES_HEADER
import com.cyxbs.pages.electricity.config.SP_BUILDING_FOOT_KEY
import com.cyxbs.pages.electricity.config.SP_BUILDING_HEAD_KEY
import com.cyxbs.pages.electricity.config.SP_ROOM_KEY
import com.cyxbs.pages.electricity.config.parseBuildingId
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.Res
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_confirm
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_dormitory_label
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.compose.resources.stringResource

/**
 * 宿舍选择 + 寝室号输入对话框
 *
 * 像素级复刻 electricity_dialog_dormitory_select.xml：
 * - 整体 8dp 圆角白底 / 黑底（随主题）
 * - 顶部一行「宿舍号：[EditText]」24sp 加粗
 * - 紧贴一行「XX栋」15sp，alpha=0.6
 * - 一条 1dp、alpha=0.1 的分隔线
 * - 左右两个滚轮（无背景框）：苑 + 楼栋，24sp，3 项可见
 * - 一条同样的分隔线
 * - 底部居中 120dp 宽渐变圆角按钮「确定」
 */
@Composable
fun ElectricityFeedSettingDialog(
  onDismiss: () -> Unit,
  onConfirm: (id: String, room: String) -> Unit,
) {
  val headers = remember { BUILDING_NAMES_HEADER.toPersistentList() }
  val initialHead = remember { AccountSettings.now.getInt(SP_BUILDING_HEAD_KEY, 0).coerceIn(0, headers.lastIndex) }
  val initialFoot = remember {
    val list = BUILDING_NAMES[headers[initialHead]].orEmpty()
    AccountSettings.now.getInt(SP_BUILDING_FOOT_KEY, 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))
  }
  val roomState = rememberTextFieldState(
    initialText = remember { AccountSettings.now.getString(SP_ROOM_KEY, "101").ifEmpty { "101" } },
  )

  val headAnimatable = remember { Animatable(initialHead.toFloat()) }

  val currentHead by remember {
    derivedStateOf { headAnimatable.targetValue.toInt().coerceIn(0, headers.lastIndex) }
  }
  val footOptions by remember {
    derivedStateOf {
      BUILDING_NAMES[headers[currentHead]].orEmpty()
        .map { it.replaceAfter("舍", "") }
        .toPersistentList()
    }
  }
  // 切苑时 footOptions 长度会变化。WheelSelectCompose 内部只夹后续 snapTo，
  // 不会立刻把 Animatable.value 收紧到新的上界，于是它的 LazyLayout 在 measure 时
  // 会用旧 value 去访问新 options 越界（IndexOutOfBoundsException）。
  // 这里用 currentHead 当 key 让 footAnimatable 跟着重建即可。
  var currentFoot by remember { mutableStateOf(initialFoot) }
  val buildingLabel by remember {
    derivedStateOf {
      BUILDING_NAMES[headers[currentHead]]?.getOrNull(currentFoot).orEmpty()
        .substringAfter('(').substringBefore(')')
    }
  }

  val colors = LocalAppColors.current
  val wheelTextStyle = TextStyle(
    fontSize = 24.sp,
    textAlign = TextAlign.Center,
    color = colors.tvLv2,
  )

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.whiteBlack),
    ) {
      // ↑ 顶部到「宿舍号：」 marginTop = 23dp
      Spacer(modifier = Modifier.height(24.dp))
      DormitoryRoomInput(roomState = roomState)
      Text(
        text = buildingLabel,
        color = colors.tvLv2.copy(alpha = 0.6f),
        fontSize = 15.sp,
        modifier = Modifier.padding(start = 26.dp),
      )
      Spacer(modifier = Modifier.height(17.dp))
      Divider()
      Spacer(modifier = Modifier.height(16.dp))
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        WheelSelectCompose(
          // 高度 = 3 行 24sp 字号 + 两份 11dp 间距，约等于 110dp
          modifier = Modifier.size(width = 96.dp, height = 110.dp),
          selectedLine = headAnimatable,
          options = headers,
          textStyle = wheelTextStyle,
        )
        key(currentHead) {
          val maxFootIdx = (footOptions.size - 1).coerceAtLeast(0)
          // 保留切苑前选中的下标，超出新苑范围时夹到上限
          val startFoot = currentFoot.coerceIn(0, maxFootIdx)
          val footAnimatable = remember {
            Animatable(startFoot.toFloat()).apply {
              updateBounds(lowerBound = 0f, upperBound = maxFootIdx.toFloat())
            }
          }
          LaunchedEffect(footAnimatable) {
            snapshotFlow { footAnimatable.targetValue.toInt().coerceIn(0, maxFootIdx) }
              .collect { currentFoot = it }
          }
          WheelSelectCompose(
            modifier = Modifier.size(width = 96.dp, height = 110.dp),
            selectedLine = footAnimatable,
            options = if (footOptions.isEmpty()) persistentListOf("") else footOptions,
            textStyle = wheelTextStyle,
          )
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
      Divider()
      ConfirmButton(
        text = stringResource(Res.string.electricity_confirm),
        onClick = {
          val header = headers[currentHead]
          val label = BUILDING_NAMES[header]?.getOrNull(currentFoot).orEmpty()
          val id = parseBuildingId(label)
          val room = roomState.text.toString()
          AccountSettings.now.putInt(SP_BUILDING_HEAD_KEY, currentHead)
          AccountSettings.now.putInt(SP_BUILDING_FOOT_KEY, currentFoot)
          AccountSettings.now.putString(SP_ROOM_KEY, room)
          onConfirm(id, room)
        },
      )
      Spacer(modifier = Modifier.height(29.dp))
    }
  }
}

@Composable
private fun DormitoryRoomInput(roomState: TextFieldState) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier.padding(start = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(Res.string.electricity_dormitory_label),
      color = colors.tvLv2,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
    )
    val underlineColor = colors.tvLv4.copy(alpha = 0.3f)
    BasicTextField(
      state = roomState,
      lineLimits = TextFieldLineLimits.SingleLine,
      cursorBrush = SolidColor(colors.tvLv4),
      textStyle = TextStyle(
        fontSize = 24.sp,
        color = colors.tvLv2,
        textAlign = TextAlign.Center,
      ),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      // 只接收数字、最多 4 位，由 InputTransformation 在编辑时直接过滤
      inputTransformation = InputTransformation.byValue { _, proposed ->
        proposed.filter { it.isDigit() }.take(4)
      },
      modifier = Modifier.padding(8.dp).width(60.dp)
        // 在 BasicTextField 自身背景层画下划线，提示这是可编辑区域
        .drawBehind {
          val strokeWidth = 1.dp.toPx()
          drawLine(
            color = underlineColor,
            start = Offset(0f, size.height - strokeWidth / 2),
            end = Offset(size.width, size.height - strokeWidth / 2),
            strokeWidth = strokeWidth,
          )
        },
    )
  }
}

@Composable
private fun ConfirmButton(text: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .width(120.dp)
        .clip(RoundedCornerShape(100.dp))
        .background(
          brush = Brush.horizontalGradient(
            // electricity_shape_button：view_button_gradient_start/end_color
            colors = listOf(Color(0xFF4841E2), Color(0xFF5D5DF7)),
          ),
        )
        .clickableSingle(onClick = onClick)
        .padding(horizontal = 32.dp, vertical = 11.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun Divider() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(LocalAppColors.current.tvLv4.copy(alpha = 0.1f)),
  )
}
