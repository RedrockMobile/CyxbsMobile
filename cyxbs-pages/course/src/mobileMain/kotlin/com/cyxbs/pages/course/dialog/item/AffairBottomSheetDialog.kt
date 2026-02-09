package com.cyxbs.pages.course.dialog.item

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.toChinese
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.components.view.ui.rememberTextDialog
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import com.cyxbs.pages.course.frame.AbstractCourseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */

class AffairBottomSheetDialogState(
  currentForm: CurrentForm,
) {

  val currentFormState = mutableStateOf(currentForm)

  sealed interface CurrentForm {
    val model: AffairDateModel

    data class Show(override val model: AffairDateModel) : CurrentForm
    data class Edit(val editor: AffairDateModelEditor) : CurrentForm {
      val isInEditTime = mutableStateOf(false)
      val isWeakNumValid = mutableStateOf(true)
      val isDayOfWeakValid = mutableStateOf(true)
      val isHourMinuteValid = mutableStateOf(true)
      override val model: AffairDateModel
        get() = editor.dateModel
    }
  }
}


@Composable
fun AffairBottomSheetDialog(
  courseBottomSheetDialogState: CourseItemBottomSheetDialogState,
  affairBottomSheetDialogState: AffairBottomSheetDialogState,
) {
  SelectionContainer {
    Column(
      modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
      EditTitleWithBtn(courseBottomSheetDialogState, affairBottomSheetDialogState)
      val currentForm = affairBottomSheetDialogState.currentFormState.value
      if (currentForm is CurrentForm.Edit && currentForm.isInEditTime.value) {
        AffairModifyTime(
          modifier = Modifier.padding(top = 8.dp),
          courseState = courseBottomSheetDialogState,
          currentForm = currentForm,
        )
      } else {
        WeekAndTime(
          modifier = Modifier.padding(top = 8.dp),
          courseState = courseBottomSheetDialogState,
          affairState = affairBottomSheetDialogState,
        )
        AffairContentEditor(
          modifier = Modifier.padding(top = 8.dp).onGloballyPositioned {
            courseBottomSheetDialogState.setImePeekBottomInWindow(it.positionInWindow().y + it.size.height + 10)
          },
          affairState = affairBottomSheetDialogState
        )
      }
    }
  }
}

@Composable
private fun EditTitleWithBtn(
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  val readOnly = rememberDerivedStateOfStructure {
    val currentForm = affairState.currentFormState.value
    currentForm is CurrentForm.Show || (currentForm is CurrentForm.Edit && currentForm.isInEditTime.value)
  }
  val model = affairState.currentFormState.value.model
  val textFieldState = rememberTextFieldState(initialText = model.idModel.title.value)
  val focusRequester = remember { FocusRequester() }
  val coroutineScope = rememberCoroutineScope()
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicTextField(
      modifier = Modifier.weight(1F)
        .focusRequester(focusRequester)
        .plusDsl {
          if (readOnly.value) {
            basicMarquee(iterations = Int.MAX_VALUE)
          }
        },
      state = textFieldState,
      readOnly = readOnly.value,
      lineLimits = TextFieldLineLimits.SingleLine,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
        showKeyboardOnFocus = false,
      ),
      textStyle = TextStyle(
        fontSize = 22.sp,
        color = LocalAppColors.current.tvLv2,
        fontWeight = FontWeight.Bold,
      ),
    )
    when (val currentForm = affairState.currentFormState.value) {
      is CurrentForm.Show -> {
        ShowStateButtons(courseState, affairState)
      }

      is CurrentForm.Edit -> {
        EditStateButtons(
          courseState = courseState,
          affairState = affairState,
          currentForm = currentForm,
          parentCoroutineScope = coroutineScope
        )
      }
    }
    LaunchedEffect(Unit) {
      snapshotFlow { textFieldState.text }.collect {
        val currentForm = affairState.currentFormState.value
        if (currentForm is CurrentForm.Edit) {
          currentForm.editor.idModelEditor.setTitle(it.toString())
        }
      }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
      snapshotFlow { affairState.currentFormState.value }.drop(1).collectLatest { form ->
        when (form) {
          is CurrentForm.Show -> {
            courseState.bottomSheetState.userScrollEnabled.value = true
            focusRequester.freeFocus()
            textFieldState.setTextAndPlaceCursorAtEnd(form.model.idModel.title.value)
          }

          is CurrentForm.Edit -> {
            courseState.bottomSheetState.userScrollEnabled.value = false
            focusRequester.requestFocus()
            snapshotFlow { form.isInEditTime.value }.filter { it }.collect {
              focusManager.clearFocus() // 移除焦点
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ShowStateButtons(
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Icon(
    contentDescription = "编辑事务",
    painter = rememberVectorPainter(Icons.Outlined.Settings),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      // 编辑事务
      val model = affairState.currentFormState.value.model
      val editor = model.idModel.tryCreateEditor()
      if (editor != null) {
        affairState.currentFormState.value = CurrentForm.Edit(
          editor.whatTimeDate.firstNotNullOf { entry ->
            if (entry.key.whatTimeModel == model.whatTime.value)
              entry.value.find { it.dateModel == model }
            else null
          }
        )
        // 进入编辑模式后将弹窗的内容设置为仅当前 item
        courseState.dialogContents.value = listOf(courseState.currentPageItemFlow.value!!)
      }
    },
  )
  val dialog = rememberTextDialog(
    text = "确定删除该事务吗？",
    negativeBtnText = "取消",
    onClickNegativeBtn = { dismiss() },
    onClickPositiveBtn = {
      // 复杂事务的删除需要单独处理
      toast("暂未实现")
    },
  )
  Icon(
    contentDescription = "删除事务",
    painter = rememberVectorPainter(Icons.Outlined.Delete),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      dialog.show()
    },
  )
}

@Composable
private fun EditStateButtons(
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
  currentForm: CurrentForm.Edit,
  parentCoroutineScope: CoroutineScope,
) {
  val completeEditDialog = rememberTextDialog(
    text = "确定事务编辑完成？",
    negativeBtnText = "返回",
    onClickPositiveBtn = {
      toast("暂未实现")
    },
  )
  Icon(
    contentDescription = if (!currentForm.isInEditTime.value) "保存编辑" else "返回上一级",
    painter =
      if (!currentForm.isInEditTime.value) rememberVectorPainter(Icons.Outlined.Check)
      else rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowBack),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      if (!currentForm.isInEditTime.value) {
        completeEditDialog.show()
      } else if (!currentForm.isHourMinuteValid.value) {
        toast("时间段不合法")
      } else {
        currentForm.isInEditTime.value = false
      }
    },
  )
  val cancelEditDialog = rememberTextDialog(
    text = "确定取消编辑？",
    negativeBtnText = "返回",
    onClickPositiveBtn = {
      if (currentForm.editor.idModelEditor.cancelEdit()) {
        affairState.currentFormState.value = CurrentForm.Show(currentForm.editor.dateModel)
      }
      dismiss()
    }
  )
  Icon(
    contentDescription = "取消编辑",
    painter = rememberVectorPainter(Icons.Outlined.Close),
    tint = LocalAppColors.current.tvLv2,
    modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
      cancelEditDialog.show()
    },
  )

  DisposableEffect(Unit) {
    val oldOnDismiss = courseState.bottomSheetState.onDismissRequest
    courseState.bottomSheetState.onDismissRequest = {
      cancelEditDialog.showAndCover(
        onClickPositiveBtnProxy = {
          onClickPositiveBtn.invoke(this)
          parentCoroutineScope.launch {
            // 需要使用外界的 coroutineScope，因为 EditStateButtons 函数立马就被移除了
            collapse()
          }
        },
      )
    }
    onDispose {
      courseState.bottomSheetState.onDismissRequest = oldOnDismiss
    }
  }
}

@Composable
private fun WeekAndTime(
  modifier: Modifier,
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
) {
  Row(modifier) {
    WeakNumCompose(courseState, affairState)
    DayOfWeakCompose(affairState, modifier = Modifier.padding(start = 8.dp))
    Text(
      text = produceState("") {
        affairState.currentFormState.value.model.whatTime.flatMapLatest { it.timePair }.collect {
          value = it.toString()
        }
      }.value,
      fontSize = 13.sp,
      color = LocalAppColors.current.tvLv2,
      modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
        val currentForm = affairState.currentFormState.value
        if (currentForm is CurrentForm.Edit) {
          if (!currentForm.isWeakNumValid.value) {
            toast("周数不合法，请修改后再编辑时间段")
          } else if (!currentForm.isDayOfWeakValid.value) {
            toast("星期不合法，请修改后再编辑时间段")
          } else {
            currentForm.isInEditTime.value = true
          }
        }
      },
    )
  }
}

@Composable
private fun WeakNumCompose(
  courseState: CourseItemBottomSheetDialogState,
  affairState: AffairBottomSheetDialogState,
  modifier: Modifier = Modifier,
) {
  val weakNumText = rememberTextFieldState(remember {
    SchoolCalendar.getFirstMonDay()!!.daysUntil(affairState.currentFormState.value.model.date.value)
      .div(7).plus(1)
      .let { Num2CN.number2ChineseNumber(it) }
  })
  val maxWeak = AbstractCourseFrame.current.maxWeak
  Row(modifier) {
    Text(
      text = "第",
      fontSize = 13.sp,
      color = LocalAppColors.current.tvLv2,
    )
    BasicTextField(
      modifier = Modifier.widthIn(min = 10.dp).width(IntrinsicSize.Min),
      state = weakNumText,
      readOnly = affairState.currentFormState.value is CurrentForm.Show,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
      ),
      textStyle = TextStyle(
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
        textAlign = TextAlign.Center,
      ),
      decorator = { innerTextField ->
        Column {
          val currentForm = affairState.currentFormState.value
          innerTextField()
          Divider(
            color = when (currentForm) {
              is CurrentForm.Show -> Color.Transparent
              is CurrentForm.Edit if !currentForm.isWeakNumValid.value -> MaterialTheme.colors.error
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
      fontSize = 13.sp,
      color = LocalAppColors.current.tvLv2,
    )
  }
  val courseFrame = AbstractCourseFrame.current
  LaunchedEffect(Unit) {
    snapshotFlow { weakNumText.text }.collect {
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Edit) {
        currentForm.isWeakNumValid.value = it.isNotEmpty()
        if (currentForm.isWeakNumValid.value) {
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
          val date = currentForm.editor.date
          val newDate = SchoolCalendar.getFirstMonDay()!!
            .weekBeginDate.plusWeeks(num - 1)
            .plusDays(date.dayOfWeekOrdinal)
          currentForm.editor.setDate(newDate)
          courseFrame.pagerState.animateScrollToPage(courseFrame.getPage(newDate))
        }
      }
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { affairState.currentFormState.value }.drop(1).collect {
      if (it is CurrentForm.Show) {
        weakNumText.setTextAndPlaceCursorAtEnd(
          SchoolCalendar.getFirstMonDay()!!
            .daysUntil(affairState.currentFormState.value.model.date.value)
            .div(7).plus(1)
            .let { Num2CN.number2ChineseNumber(it) }
        )
      }
    }
  }
}

@Composable
private fun DayOfWeakCompose(
  affairState: AffairBottomSheetDialogState,
  modifier: Modifier = Modifier,
) {
  val dayOfWeakText = rememberTextFieldState(remember {
    affairState.currentFormState.value.model.date.value.dayOfWeek.toChinese("")
  })
  val isValid = remember { mutableStateOf(true) }
  Row(modifier) {
    Text(
      text = "周",
      fontSize = 13.sp,
      color = LocalAppColors.current.tvLv2,
    )
    BasicTextField(
      modifier = Modifier.widthIn(min = 10.dp).width(IntrinsicSize.Min),
      state = dayOfWeakText,
      readOnly = affairState.currentFormState.value is CurrentForm.Show,
      cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
      ),
      textStyle = TextStyle(
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
        textAlign = TextAlign.Center,
      ),
      decorator = { innerTextField ->
        Column {
          val currentForm = affairState.currentFormState.value
          innerTextField()
          Divider(
            color = when (currentForm) {
              is CurrentForm.Show -> Color.Transparent
              is CurrentForm.Edit if !currentForm.isDayOfWeakValid.value -> MaterialTheme.colors.error
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
  LaunchedEffect(Unit) {
    snapshotFlow { dayOfWeakText.text }.collect {
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Edit) {
        currentForm.isDayOfWeakValid.value = it.isNotEmpty()
        if (currentForm.isDayOfWeakValid.value) {
          val date = currentForm.editor.date
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
          currentForm.editor.setDate(date.weekBeginDate.plusDays(num - 1))
        }
      }
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { affairState.currentFormState.value }.drop(1).collect {
      if (it is CurrentForm.Show) {
        dayOfWeakText.setTextAndPlaceCursorAtEnd(it.model.date.value.dayOfWeek.toChinese(""))
      }
    }
  }
}

@Composable
private fun AffairContentEditor(modifier: Modifier, affairState: AffairBottomSheetDialogState) {
  val model = remember { affairState.currentFormState.value.model }
  val textFieldState = rememberTextFieldState(model.idModel.content.value)
  val readOnly = rememberDerivedStateOfStructure {
    affairState.currentFormState.value is CurrentForm.Show
  }
  BasicTextField(
    modifier = modifier.fillMaxWidth(),
    state = textFieldState,
    readOnly = readOnly.value,
    cursorBrush = SolidColor(TextFieldDefaults.textFieldColors().cursorColor(false).value),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Text,
    ),
    textStyle = TextStyle(
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2
    ),
  )
  LaunchedEffect(Unit) {
    snapshotFlow { textFieldState.text }.collect {
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Edit) {
        currentForm.editor.idModelEditor.setContent(it.toString())
      }
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { affairState.currentFormState.value }.drop(1).collectLatest { form ->
      when (form) {
        is CurrentForm.Show -> {
          textFieldState.setTextAndPlaceCursorAtEnd(form.model.idModel.title.value)
        }

        is CurrentForm.Edit -> {
        }
      }
    }
  }
}

@Composable
private fun AffairModifyTime(
  modifier: Modifier,
  courseState: CourseItemBottomSheetDialogState,
  currentForm: CurrentForm.Edit,
) {
  val dateModelEditorList = remember {
    currentForm.editor.idModelEditor.whatTimeDate.values.toList().flatten().sortedBy {
      it.date
    }.toMutableStateList()
  }
  val pagerState = rememberPagerState { dateModelEditorList.size }
  Column(modifier = modifier.fillMaxSize()) {
    VerticalPager(
      modifier = Modifier.weight(1F),
      state = pagerState,
      key = { dateModelEditorList[it].hashCode() },
      beyondViewportPageCount = 1,
      pageSize = PageSize.Fixed(60.dp),
      userScrollEnabled = currentForm.isHourMinuteValid.value
    ) {
      AffairEditTime(
        page = it,
        pageCount = pagerState.pageCount,
        currentForm = currentForm,
        dateModelEditor = dateModelEditorList[it],
        courseState = courseState,
      )
    }
    Card(modifier = Modifier) {
      Box(
        modifier = Modifier.fillMaxWidth().height(30.dp).clickableNoIndicator {
          toast("添加新的时间段")
        },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          contentDescription = "添加新的时间段",
          painter = rememberVectorPainter(Icons.Outlined.Add),
          tint = LocalAppColors.current.tvLv2,
        )
      }
    }
  }
}

@Composable
private fun AffairEditTime(
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