package com.cyxbs.pages.course.dialog.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.rememberTextDialog
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import com.cyxbs.pages.course.dialog.item.affair.AffairEditCompose
import com.cyxbs.pages.course.dialog.item.affair.AffairEditWeekText
import com.cyxbs.pages.course.dialog.item.affair.AffairShowCompose
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import com.cyxbs.pages.course.view.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_affair_time_add
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_affair_time_delete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

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
    val date: Date

    val whatTime: MinuteTimePair

    val title: String

    val content: String

    data class Show(private val model: AffairDateModel) : CurrentForm {
      override val date: Date
        get() = model.date.value
      override val whatTime: MinuteTimePair
        get() = model.whatTime.value.timePair.value
      override val title: String
        get() = model.idModel.title.value
      override val content: String
        get() = model.idModel.content.value

      fun createEdit(): Edit? {
        val editor = model.idModel.tryCreateEditor()
        if (editor != null) {
          val dateModelEditor = editor.whatTimeDate.firstNotNullOfOrNull { entry ->
            if (entry.key.whatTimeModel == model.whatTime.value)
              entry.value.find { it.dateModel == model }
            else null
          }
          if (dateModelEditor != null) {
            return Edit(dateModelEditor)
          } else {
            toast("出现异常，当前 model 无法找到对应 editor")
          }
        } else {
          toast("出现异常，当前 model 无法创建 editor")
        }
        return null
      }
    }

    data class Edit(var editor: AffairDateModelEditor) : CurrentForm {
      val isInEditTime = mutableStateOf(false)
      val isHourMinuteValid = mutableStateOf(true)
      override val date: Date
        get() = editor.date
      override val whatTime: MinuteTimePair
        get() = editor.whatTimeEditor!!.timePair
      override val title: String
        get() = editor.idModelEditor.title
      override val content: String
        get() = editor.idModelEditor.content

      // 编辑状态
      val editState = mutableStateOf(EditState.EditBasic)

      // 编辑时间段
      val editTimePair = mutableStateOf<AffairWhatTimeModelEditor?>(null)

      // 原始数据
      private val originModel: AffairDateModel = editor.dateModel

      // 点击保存时的检查
      val clickSaveCheck = mutableListOf<() -> String?>()

      // 点击上一步时的检查
      val clickPrevCheck = mutableListOf<() -> String?>()

      // 点击其他时间时的检查
      val clickSwitchTimeCheck = mutableListOf<() -> String?>()

      // 取消编辑
      fun cancelEdit(): Show? {
        if (editor.idModelEditor.cancelEdit()) {
          clickSaveCheck.clear()
          clickPrevCheck.clear()
          clickSwitchTimeCheck.clear()
          return Show(originModel)
        }
        return null
      }

      // 提交
      suspend fun commit(): Show? {
        return editor.idModelEditor.commit().onSuccess {
          when (it) {
            AffairIdModelEditor.EditResult.Deleted -> toast("事务已被删除")
            AffairIdModelEditor.EditResult.Success -> toast("修改成功")
          }
        }.map {
          clickSaveCheck.clear()
          clickPrevCheck.clear()
          clickSwitchTimeCheck.clear()
          Show(editor.dateModel)
        }.getOrNull()
      }
    }

    enum class EditState {
      EditBasic, EditTime
    }
  }
}


@Composable
fun AffairBottomSheetDialog(
  courseBottomSheetDialogState: CourseItemBottomSheetDialogState,
  affairBottomSheetDialogState: AffairBottomSheetDialogState,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)
  ) {
    when (val currentForm = affairBottomSheetDialogState.currentFormState.value) {
      is CurrentForm.Show -> AffairShowCompose(
        currentForm = currentForm,
        courseState = courseBottomSheetDialogState,
        affairState = affairBottomSheetDialogState,
      )
      is CurrentForm.Edit -> AffairEditCompose(
        currentForm = currentForm,
        courseState = courseBottomSheetDialogState,
        affairState = affairBottomSheetDialogState,
      )
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
  val textFieldState = rememberTextFieldState(initialText = remember {
    affairState.currentFormState.value.title
  })
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
      snapshotFlow { affairState.currentFormState.value }.collectLatest { form ->
        when (form) {
          is CurrentForm.Show -> {
            courseState.bottomSheetState.userScrollEnabled.value = true
            focusRequester.freeFocus()
            textFieldState.setTextAndPlaceCursorAtEnd(form.title)
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
      val currentForm = affairState.currentFormState.value
      if (currentForm is CurrentForm.Show) {
        val edit = currentForm.createEdit()
        if (edit != null) {
          affairState.currentFormState.value = edit
          // 进入编辑模式后将弹窗的内容设置为仅当前 item
          courseState.dialogContents.value = listOf(courseState.currentPageItemFlow.value!!)
          // 进入编辑模式后允许 item 位置改变动画
          LayoutItemModifier.enableAnim.set(courseState.currentPageItemFlow.value!!.itemState, true)
        }
      }
    },
  )
  val dialog = rememberTextDialog(
    text = "确定删除该事务吗？",
    negativeBtnText = "取消",
    onClickNegativeBtn = { dismiss() },
    onClickPositiveBtn = {
      // 复杂事务的删除单独处理，需要考虑删除单个还是删除全部亦或是删除后续
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
      parentCoroutineScope.launch {
        val show = currentForm.commit()
        if (show != null) {
          affairState.currentFormState.value = show
          dismiss()
        }
      }
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
        // 保存编辑
        val error = currentForm.clickSaveCheck.firstNotNullOfOrNull { it() }
        if (error != null) {
          toast(error)
        } else {
          currentForm.clickSaveCheck.clear()
          completeEditDialog.show()
        }
      } else {
        // 返回上一级
        val error = currentForm.clickPrevCheck.firstNotNullOfOrNull { it() }
        if (error != null) {
          toast(error)
        } else {
          currentForm.clickPrevCheck.clear()
          currentForm.clickSwitchTimeCheck.clear()
          currentForm.isInEditTime.value = false
        }
      }
    },
  )
  val cancelEditDialog = rememberTextDialog(
    text = "确定取消编辑？",
    negativeBtnText = "返回",
    onClickPositiveBtn = {
      val show = currentForm.cancelEdit()
      if (show != null) {
        courseState.currentPageItemFlow.value = courseState.dialogContents.value[0] // 还原之前的修改
        affairState.currentFormState.value = show
        // 退出编辑模式后关闭 item 位置改变动画
        LayoutItemModifier.enableAnim.set(courseState.currentPageItemFlow.value!!.itemState, false)
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
      // bottomSheet 下滑时弹出一个确认取消编辑的弹窗
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
private fun AffairContentEditor(modifier: Modifier, affairState: AffairBottomSheetDialogState) {
  val textFieldState = rememberTextFieldState(initialText = remember {
    affairState.currentFormState.value.content
  })
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
          textFieldState.setTextAndPlaceCursorAtEnd(form.title)
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
  val lastSelectedWhatTimeState = remember { mutableStateOf(currentForm.editor.whatTimeEditor!!) }
  val lastSelectedDateModelState = remember { mutableStateOf(currentForm.editor) }
  Column(modifier = modifier.fillMaxSize()) {
    TimePairRow(currentForm, lastSelectedWhatTimeState)
    Divider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp))
    DateGrid(
      courseState = courseState,
      currentForm = currentForm,
      lastSelectedWhatTimeState = lastSelectedWhatTimeState,
      lastSelectedDateModelState = lastSelectedDateModelState,
    )
    LaunchedEffect(Unit) {
      snapshotFlow { lastSelectedWhatTimeState.value }.drop(1).collect {
        val dateModel = lastSelectedWhatTimeState.value?.dateList?.firstOrNull()
        if (dateModel != null) {
          lastSelectedDateModelState.value = dateModel
        }
      }
    }
    val courseFrame = AbstractCourseFrame.current
    val affairDecorationViewModel = viewModel<AffairDecorationViewModel>()
    LaunchedEffect(Unit) {
      snapshotFlow { lastSelectedDateModelState.value }.drop(1).collectLatest { dateModelEditor ->
        courseFrame.animateScrollToDate(dateModelEditor.date)
        currentForm.editor = dateModelEditor
        // 修改外层选中的 CourseItemState，使开始结束时间和滚轴偏移量发生改变
        // 通过这种方法获取 itemState 稍微有些 trick
        val itemState = affairDecorationViewModel.findCourseItemState(dateModelEditor)
        courseState.currentPageItemFlow.value = itemState.item.extensions.get(CourseItemBottomSheetDialogExtension::class)
      }
    }
  }
}

@Composable
private fun TimePairRow(
  currentForm: CurrentForm.Edit,
  lastSelectedWhatTimeState: MutableState<AffairWhatTimeModelEditor>,
) {
  val timePairRowItem = remember {
    currentForm.editor.idModelEditor.whatTimeDate.keys.toList().toMutableStateList()
  }
  LazyRow(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(timePairRowItem, key = { it.hashCode() }) { item ->
      val isSelected = rememberDerivedStateOfStructure {
        item === lastSelectedWhatTimeState.value
      }
      Box(
        modifier = Modifier.clickable {
          if (lastSelectedWhatTimeState.value != item) {
            val error = currentForm.clickSwitchTimeCheck.firstNotNullOfOrNull { it() }
            if (error != null) {
              toast(error)
            } else {
              lastSelectedWhatTimeState.value = item
            }
          }
        }
      ) {
        Text(
          text = item.timePair.toString(),
          color = if (isSelected.value) Color(0xFFFFA192)
          else LocalAppColors.current.tvLv2,
          fontSize = 14.sp,
          modifier = Modifier.padding(top = 3.dp, end = 3.dp)
            .background(color = 0xACE8F0FC.dark(0xB32C2C2C), RoundedCornerShape(29.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        val isLastOne = rememberDerivedStateOfStructure {
          timePairRowItem.size == 1
        }
        if (!isLastOne.value) {
          Image(
            painter = painterResource(Res.drawable.course_ic_affair_time_delete),
            contentDescription = "删除时间段",
            modifier = Modifier.align(Alignment.TopEnd).clickable {
              if (item.idModelEditor.remove(item)) {
                timePairRowItem.remove(item)
              }
            },
          )
        }
      }
    }
    item(key = "+") {
      Image(
        painter = painterResource(Res.drawable.course_ic_affair_time_add),
        contentDescription = "添加时间段",
        modifier = Modifier.padding(start = 6.dp, top = 3.dp).size(19.dp).clickable {
          toast("添加时间段")
        }
      )
    }
  }
}

@Composable
private fun DateGrid(
  modifier: Modifier = Modifier,
  courseState: CourseItemBottomSheetDialogState,
  currentForm: CurrentForm.Edit,
  lastSelectedWhatTimeState: MutableState<AffairWhatTimeModelEditor>,
  lastSelectedDateModelState: MutableState<AffairDateModelEditor>,
) {
  val dateList = remember(lastSelectedWhatTimeState.value) {
    lastSelectedWhatTimeState.value.dateList.toMutableStateList()
  }
  FlowRow(
    modifier = modifier.onGloballyPositioned {
      courseState.setImePeekBottomInWindow(it.positionInWindow().y + it.size.height + 10)
    },
    itemVerticalAlignment = Alignment.CenterVertically
  ) {
    dateList.fastForEach { item ->
      key(item) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 2.dp, bottom = 3.dp, end = 3.dp)
            .background(color = 0xACE8F0FC.dark(0xB32C2C2C), RoundedCornerShape(29.dp))
            .height(IntrinsicSize.Min)
            .clickable {
              if (lastSelectedDateModelState.value != item) {
                val error = currentForm.clickSwitchTimeCheck.firstNotNullOfOrNull { it() }
                if (error != null) {
                  toast(error)
                } else {
                  lastSelectedDateModelState.value = item
                }
              }
            }
        ) {
          val isSelected = rememberDerivedStateOfStructure {
            item === lastSelectedDateModelState.value
          }
          val dateState = remember { mutableStateOf(item.date) }
          val weekNumIsError = remember { mutableStateOf(false) }
          val dayOfWeekIsError = remember { mutableStateOf(false) }
          AffairEditWeekText(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            dateState = dateState,
            weekNumIsError = weekNumIsError,
            dayOfWeekIsError = dayOfWeekIsError,
            fontSize = 11.sp,
            color = if (isSelected.value) Color(0xFFFFA192) else LocalAppColors.current.tvLv2,
            readOnly = !isSelected.value,
            weekNumDayOfWeekPadding = 3.dp,
          )
          Spacer(
            modifier = Modifier.padding(vertical = 8.dp).fillMaxHeight().width(1.dp)
              .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12F))
          )
          Icon(
            contentDescription = "删除日期",
            painter = rememberVectorPainter(Icons.Outlined.Delete),
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3F),
            modifier = Modifier.padding(start = 3.dp, end = 5.dp).size(16.dp).clickable {
              if (item.whatTimeEditor?.remove(item) == true) {
                val index = dateList.indexOf(item)
                if (item === lastSelectedDateModelState.value) {
                  lastSelectedDateModelState.value = dateList.getOrNull(index + 1) ?: dateList.getOrNull(index - 1) ?: item
                }
                dateList.remove(item)
              }
            },
          )
          DisposableEffect(Unit) {
            val checkBack = {
              if (weekNumIsError.value) {
                "周数不合法，请先修改再返回"
              } else if (dayOfWeekIsError.value) {
                "星期不合法，请先修改再返回"
              } else null
            }
            val checkSwitch = {
              if (weekNumIsError.value) {
                "周数不合法，请先修改再切换"
              } else if (dayOfWeekIsError.value) {
                "星期不合法，请先修改再切换"
              } else null
            }
            currentForm.clickPrevCheck.add(checkBack)
            currentForm.clickSwitchTimeCheck.add(checkSwitch)
            onDispose {
              currentForm.clickPrevCheck.remove(checkBack)
              currentForm.clickSwitchTimeCheck.remove(checkSwitch)
            }
          }
          LaunchedEffect(Unit) {
            snapshotFlow { dateState.value }.collect {
              logg("date = $it")
              item.setDate(it)
            }
          }
        }
      }
    }
    Image(
      painter = painterResource(Res.drawable.course_ic_affair_time_add),
      contentDescription = "添加日期",
      modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 3.dp).size(18.dp).clickable {
        val whatTimeModel = lastSelectedWhatTimeState.value
        val nowDate = lastSelectedDateModelState.value.date
        var diff = 1
        while (true) {
          val dateModel = whatTimeModel.add(nowDate.plusDays(diff))
          if (dateModel != null) {
            // 找到第一天能添加的日期
            dateList.add(dateModel)
            lastSelectedDateModelState.value = dateModel
            break
          }
          diff++ // 正常来说一直往后递增，一定会有能加进去的日期，这里就不考虑最后一周的问题
        }
      }
    )
  }
  DisposableEffect(Unit) {
    val checkSwitch = {
      if (dateList.isEmpty()) {
        "${lastSelectedWhatTimeState.value.timePair} 内对应的日期为空"
      } else null
    }
    currentForm.clickPrevCheck.add(checkSwitch)
    currentForm.clickSwitchTimeCheck.add(checkSwitch)
    onDispose {
      currentForm.clickPrevCheck.remove(checkSwitch)
      currentForm.clickSwitchTimeCheck.remove(checkSwitch)
    }
  }
}