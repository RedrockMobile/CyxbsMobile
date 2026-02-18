package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import kotlinx.datetime.isoDayNumber

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/11
 */

@Composable
internal fun AffairEditTimeCompose(
  page: Int,
  pageCount: Int,
  currentForm: CurrentForm.Edit,
  dateModelEditor: AffairDateModelEditor,
  courseState: CourseItemBottomSheetDialogState,
) {
  Card {
    Row(
      modifier = Modifier.fillMaxSize().onGloballyPositioned {
        courseState.setImePeekBottomInWindow(it.positionInWindow().y + it.size.height + 10)
      },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (pageCount > 3) {
        Text(
          text = "${page + 1}/$pageCount",
          fontSize = 16.sp,
          color = LocalAppColors.current.tvLv2
        )
      }
      EditWeekNum(dateModelEditor)
      EditDayOfWeek(dateModelEditor)
      EditHourMinute(currentForm, dateModelEditor)
    }
  }
}

@Composable
private fun EditWeekNum(
  dateModelEditor: AffairDateModelEditor,
) {
  Text(
    modifier = Modifier.padding(start = 12.dp),
    text = "第",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )
  val weekNumText = rememberTextFieldState(remember {
    SchoolCalendar.getFirstMonDay()!!.daysUntil(dateModelEditor.date)
      .div(7).plus(1)
      .toString()
  })
  EditTimeNumTextField(
    modifier = Modifier.width(36.dp),
    textFieldState = weekNumText,
    inputRange = 1..21,
  )
  Text(
    text = "周",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )
}

@Composable
private fun EditDayOfWeek(
  dateModelEditor: AffairDateModelEditor,
) {
  Text(
    modifier = Modifier.padding(start = 12.dp),
    text = "周",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )
  val dayOfWeekText =
    rememberTextFieldState(dateModelEditor.date.dayOfWeek.isoDayNumber.toString())
  EditTimeNumTextField(
    modifier = Modifier.width(24.dp),
    textFieldState = dayOfWeekText,
    inputRange = 1..7
  )
}

@Composable
private fun EditHourMinute(
  currentForm: CurrentForm.Edit,
  dateModelEditor: AffairDateModelEditor,
) {
  val timePair = remember { dateModelEditor.whatTimeEditor!!.whatTimeModel.timePair.value }
  val beginHourText =
    rememberTextFieldState(timePair.first.hour.toString())
  val beginMinuteText =
    rememberTextFieldState(timePair.first.minute.toString().padStart(2, '0'))
  val finalHourText =
    rememberTextFieldState(timePair.second.hour.toString())
  val finalMinuteText =
    rememberTextFieldState(timePair.second.minute.toString().padStart(2, '0'))
  val isBeginHourValid = remember { mutableStateOf(true) }
  val isBeginMinuteValid = remember { mutableStateOf(true) }
  val isFinalHourValid = remember { mutableStateOf(true) }
  val isFinalMinuteValid = remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    snapshotFlow {
      arrayOf(beginHourText.text, beginMinuteText.text, finalHourText.text, finalMinuteText.text)
    }.collect {
      val beginHour = it[0].toString().toIntOrNull()
      if (beginHour == null) {
        isBeginHourValid.value = false
      }
      val beginMinute = it[1].toString().toIntOrNull()
      if (beginMinute == null) {
        isBeginMinuteValid.value = false
      }
      val finalHour = it[2].toString().toIntOrNull()
      if (finalHour == null) {
        isFinalHourValid.value = false
      }
      val finalMinute = it[3].toString().toIntOrNull()
      if (finalMinute == null) {
        isFinalMinuteValid.value = false
      }
      if (beginHour != null && beginMinute != null && finalHour != null && finalMinute != null) {
        isBeginHourValid.value = beginHour <= finalHour
        isFinalHourValid.value = beginHour <= finalHour
        isBeginMinuteValid.value = !(beginHour == finalHour && beginMinute >= finalMinute)
        isFinalMinuteValid.value = !(beginHour == finalHour && beginMinute >= finalMinute)
      }
      currentForm.isHourMinuteValid.value =
        isBeginHourValid.value && isBeginMinuteValid.value && isFinalHourValid.value && isFinalMinuteValid.value
    }
  }
  EditTimeNumTextField(
    modifier = Modifier.padding(start = 12.dp).width(30.dp),
    textFieldState = beginHourText,
    inputRange = 0..23,
    isValid = isBeginHourValid,
  )
  Text(
    modifier = Modifier,
    text = ":",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )

  EditTimeNumTextField(
    modifier = Modifier.width(36.dp).onFocusChanged {
      if (!it.hasFocus) {
        if (beginMinuteText.text.length == 1) {
          beginMinuteText.setTextAndPlaceCursorAtEnd("0${beginMinuteText.text}")
        }
      }
    },
    textFieldState = beginMinuteText,
    inputRange = 0..59,
    isValid = isBeginMinuteValid,
  )
  Text(
    modifier = Modifier.padding(start = 4.dp),
    text = "-",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )

  EditTimeNumTextField(
    modifier = Modifier.padding(start = 4.dp).width(30.dp),
    textFieldState = finalHourText,
    inputRange = 0..23,
    isValid = isFinalHourValid,
  )
  Text(
    text = ":",
    fontSize = 20.sp,
    color = LocalAppColors.current.tvLv2
  )

  EditTimeNumTextField(
    modifier = Modifier.width(36.dp).onFocusChanged {
      if (!it.hasFocus) {
        if (finalMinuteText.text.length == 1) {
          finalMinuteText.setTextAndPlaceCursorAtEnd("0${finalMinuteText.text}")
        }
      }
    },
    textFieldState = finalMinuteText,
    inputRange = 0..59,
    isValid = isFinalMinuteValid,
  )
}


@Composable
private fun EditTimeNumTextField(
  textFieldState: TextFieldState,
  inputRange: IntRange,
  isValid: State<Boolean> = remember { mutableStateOf(true) },
  modifier: Modifier = Modifier,
) {
  BasicTextField(
    modifier = modifier,
    state = textFieldState,
    cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Number,
      imeAction = ImeAction.Done,
    ),
    textStyle = TextStyle(
      fontSize = 20.sp,
      color = LocalAppColors.current.tvLv2,
      textAlign = TextAlign.Center,
    ),
    decorator = { innerTextField ->
      Column {
        innerTextField()
        Divider(
          color = if (isValid.value) MaterialTheme.colors.onSurface.copy(alpha = 0.12F)
          else MaterialTheme.colors.error
        )
      }
    },
    inputTransformation = InputTransformation.byValue { current, proposed ->
      if (proposed.isEmpty()) proposed
      else proposed.toString().toIntOrNull()?.takeIf { it in inputRange }?.toString() ?: current
    }
  )
}