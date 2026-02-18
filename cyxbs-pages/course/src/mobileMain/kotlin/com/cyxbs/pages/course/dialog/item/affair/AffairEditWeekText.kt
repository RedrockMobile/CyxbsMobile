package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.toChinese
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.course.frame.AbstractCourseFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/18
 */
@Composable
internal fun AffairEditWeekText(
  modifier: Modifier,
  dateState: MutableState<Date>,
  weekNumIsError: MutableState<Boolean>,
  dayOfWeekIsError: MutableState<Boolean>,
  readOnly: Boolean,
  fontSize: TextUnit = 13.sp,
  color: Color = LocalAppColors.current.tvLv2,
  weekNumDayOfWeekPadding: Dp = 8.dp,
) {
  Row(modifier) {
    WeekNumCompose(
      dateState = dateState,
      weekNumIsError = weekNumIsError,
      fontSize = fontSize,
      color = color,
      readOnly = readOnly,
    )
    DayOfWeekCompose(
      dateState = dateState,
      dayOfWeekIsError = dayOfWeekIsError,
      modifier = Modifier.padding(start = weekNumDayOfWeekPadding),
      fontSize = fontSize,
      color = color,
      readOnly = readOnly,
    )
  }
}

@Composable
private fun WeekNumCompose(
  dateState: MutableState<Date>,
  weekNumIsError: MutableState<Boolean>,
  readOnly: Boolean,
  modifier: Modifier = Modifier,
  fontSize: TextUnit = 13.sp,
  color: Color = LocalAppColors.current.tvLv2,
) {
  val courseFrame = AbstractCourseFrame.current
  val weekNumText = rememberTextFieldState(remember {
    Num2CN.number2ChineseNumber(courseFrame.getWeekNum(dateState.value))
  })
  val maxWeak = courseFrame.maxWeek
  Row(modifier) {
    Text(
      text = "第",
      fontSize = fontSize,
      color = color,
    )
    BasicTextField(
      modifier = Modifier.widthIn(min = 10.dp).width(IntrinsicSize.Min),
      state = weekNumText,
      readOnly = readOnly,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
      ),
      textStyle = TextStyle(
        fontSize = fontSize,
        color = color,
        textAlign = TextAlign.Center,
      ),
      decorator = { innerTextField ->
        Column {
          innerTextField()
          Divider(
            color = when {
              readOnly -> Color.Transparent
              weekNumIsError.value -> MaterialTheme.colors.error
              else -> MaterialTheme.colors.onSurface.copy(alpha = 0.12F)
            }
          )
        }
      },
      inputTransformation = InputTransformation.byValue { current, proposed ->
        if (proposed.isEmpty()) return@byValue proposed // 允许完全删除
        if (current.contains(proposed)) return@byValue proposed // 减少的时候直接通过
        val char0 = proposed[0]
        if (char0 == '0') return@byValue current // 首个字符不允许 0 开头
        if (proposed.length == 1) {
          if (char0.isDigit()) {
            // 只输入单个数字字符时
            return@byValue Num2CN.number2ChineseNumber(char0 - '0')
          }
        } else if (proposed.length == 2) {
          val char1 = proposed[1]
          if (char0.isDigit() && char1.isDigit()) {
            // 连续输入两个数字字符
            return@byValue Num2CN.number2ChineseNumber(proposed.toString().toInt())
          }
          if (char0 == '十' && char1.isDigit()) {
            return@byValue Num2CN.number2ChineseNumber(10 + (char1 - '0'))
          }
          val char0Num = Num2CN.CN_CHARS.indexOf(char0.toString())
          if (char0Num > 0 && char1.isDigit()) {
            // 首个字符是中文数字，第二个字符是数字
            val num = char0Num * 10 + (char1 - '0')
            if (num in 10..maxWeak) {
              return@byValue Num2CN.number2ChineseNumber(num)
            }
          }
          if (char0.isDigit() && char1 == '十') {
            // 首个字符是数字，第二个字符是'十'
            val num = (char0 - '0') * 10
            if (num in 10..maxWeak) {
              return@byValue Num2CN.number2ChineseNumber(num)
            }
          }
          val char1Num = Num2CN.CN_CHARS.indexOf(char1.toString())
          if (char0.isDigit() && char1Num >= 0) {
            // 首个字符是数字，第二个字符是中文数字
            val num = (char0 - '0') * 10 + char1Num
            if (num in 10..maxWeak) {
              return@byValue Num2CN.number2ChineseNumber(num)
            }
          }
        } else if (proposed.length == 3) {
          if (char0 == '十') return@byValue current // 首个字符为'十'时不允许存在三个字符
          val char1 = proposed[1]
          val char2 = proposed[2]
          val char0Num = Num2CN.CN_CHARS.indexOf(char0.toString())
          if (char0Num > 0 && char1 == '十' && char2.isDigit()) {
            val num = char0Num * 10 + (char2 - '0')
            if (num in 10..maxWeak) {
              return@byValue Num2CN.number2ChineseNumber(num)
            }
          }
        }
        current // 其余情况不允许修改
      }
    )
    Text(
      text = "周",
      fontSize = fontSize,
      color = color,
    )
  }
  LaunchedEffect(dateState) {
    // 设置 model 周数并跳转对应页
    snapshotFlow { weekNumText.text }.collectLatest {
      if (it.isEmpty()) {
        weekNumIsError.value = true
      } else {
        weekNumIsError.value = false
        val num = if (it.length == 1) {
          if (it[0] == '十') 10 else {
            Num2CN.CN_CHARS.indexOf(it[0].toString())
          }
        } else if (it.length == 2) {
          if (it[0] == '十') {
            10 + Num2CN.CN_CHARS.indexOf(it[1].toString())
          } else if (it[1] == '十') {
            Num2CN.CN_CHARS.indexOf(it[0].toString()) * 10
          } else error("不应该出现的分支, it = $it")
        } else if (it.length == 3 && it[1] == '十') {
          Num2CN.CN_CHARS.indexOf(it[0].toString()) * 10 + Num2CN.CN_CHARS.indexOf(it[2].toString())
        } else error("不应该出现的分支, it = $it")
        val date = dateState.value
        val newDate = courseFrame.beginDate.value!!
          .plusWeeks(num - 1)
          .plusDays(date.dayOfWeekOrdinal)
        dateState.value = newDate
        courseFrame.animateScrollToDate(newDate)
      }
    }
  }
  LaunchedEffect(dateState) {
    snapshotFlow { dateState.value }.drop(1).collect { date ->
      val weekNumStr = Num2CN.number2ChineseNumber(courseFrame.getWeekNum(date))
      if (weekNumStr != weekNumText.text) {
        weekNumText.setTextAndPlaceCursorAtEnd(weekNumStr)
      }
    }
  }
}

@Composable
private fun DayOfWeekCompose(
  dateState: MutableState<Date>,
  dayOfWeekIsError: MutableState<Boolean>,
  readOnly: Boolean,
  modifier: Modifier = Modifier,
  fontSize: TextUnit = 13.sp,
  color: Color = LocalAppColors.current.tvLv2,
) {
  val dayOfWeakText = rememberTextFieldState(remember {
    dateState.value.dayOfWeek.toChinese("")
  })
  Row(modifier) {
    Text(
      text = "周",
      fontSize = fontSize,
      color = color,
    )
    BasicTextField(
      modifier = Modifier.widthIn(min = 10.dp).width(IntrinsicSize.Min),
      state = dayOfWeakText,
      readOnly = readOnly,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
      ),
      textStyle = TextStyle(
        fontSize = fontSize,
        color = color,
        textAlign = TextAlign.Center,
      ),
      decorator = { innerTextField ->
        Column {
          innerTextField()
          Divider(
            color = when {
              readOnly -> Color.Transparent
              dayOfWeekIsError.value -> MaterialTheme.colors.error
              else -> MaterialTheme.colors.onSurface.copy(alpha = 0.12F)
            }
          )
        }
      },
      inputTransformation = InputTransformation.byValue { current, proposed ->
        if (proposed.isEmpty()) proposed else {
          val num = proposed.filter { it.isDigit() }.toString().toIntOrNull()
          if (num != null && num in 1..7) {
            DayOfWeek(num).toChinese("")
          } else current
        }
      }
    )
  }
  LaunchedEffect(dateState) {
    // 设置 model 星期数
    snapshotFlow { dayOfWeakText.text }.collect {
      if (it.isEmpty()) {
        dayOfWeekIsError.value = true
      } else {
        val date = dateState.value
        val num = when (it.toString()) {
          "一" -> 1
          "二" -> 2
          "三" -> 3
          "四" -> 4
          "五" -> 5
          "六" -> 6
          "日" -> 7
          else -> error("输入的数字有误: $it")
        }
        dateState.value = date.weekBeginDate.plusDays(num - 1)
      }
    }
  }
  LaunchedEffect(dateState) {
    // ui 与 model 同步
    snapshotFlow { dateState.value }.drop(1).collect { date ->
      val newDayOfWeek = date.dayOfWeek.toChinese("")
      if (newDayOfWeek != dayOfWeakText.text) {
        dayOfWeakText.setTextAndPlaceCursorAtEnd(newDayOfWeek)
      }
    }
  }
}