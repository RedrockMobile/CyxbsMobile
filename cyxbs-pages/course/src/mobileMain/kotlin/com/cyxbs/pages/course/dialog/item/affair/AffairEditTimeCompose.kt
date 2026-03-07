package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogExtension
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import com.cyxbs.pages.course.frame.decoration.AffairDecorationViewModel
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_affair_time_add
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_affair_time_delete
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/19
 */
@Composable
fun AffairEditTimeCompose(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
) {
  val lastSelectedWhatTimeState = remember { mutableStateOf(currentForm.editor.whatTimeEditor!!) }
  val lastSelectedDateModelState = remember { mutableStateOf(currentForm.editor) }
  Column {
    TimePairRow(
      currentForm = currentForm,
      lastSelectedWhatTimeState = lastSelectedWhatTimeState,
    )
    Divider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp))
    val whatTimeModelEditor = currentForm.editTimePair.value
    if (whatTimeModelEditor == null) {
      // 展示日期
      DateFlowRow(
        currentForm = currentForm,
        courseState = courseState,
        lastSelectedWhatTimeState = lastSelectedWhatTimeState,
        lastSelectedDateModelState = lastSelectedDateModelState,
      )
    } else {
      key(whatTimeModelEditor) {
        // 时间段编辑
        AffairEditTimePairCompose(
          whatTimeModelEditor = whatTimeModelEditor,
        )
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
    currentForm.editor.idModelEditor.whatTimeDate.keys.toList().sortedBy {
      it.timePair.value
    }.toMutableStateList()
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
              if (currentForm.editTimePair.value != null) {
                currentForm.editTimePair.value = item
              }
            }
          } else {
            if (currentForm.editTimePair.value == null) {
              // 流转到时间段编辑
              currentForm.editTimePair.value = item
            } else {
              currentForm.editTimePair.value = null
            }
          }
        }
      ) {
        Text(
          text = produceState(item.timePair) {
            item.whatTimeModel.timePair.mergeFlow.collect {
              value = it
            }
          }.value.toString(),
          color = if (isSelected.value) Color(0xFFFFA192)
          else LocalAppColors.current.tvLv2,
          fontSize = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 3.dp, end = 3.dp)
            .background(color = 0xACE8F0FC.dark(0xB32C2C2C), RoundedCornerShape(29.dp))
            .padding(vertical = 6.dp)
            .width(106.dp)
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
                val index = timePairRowItem.indexOf(item)
                timePairRowItem.removeAt(index)
                if (currentForm.editTimePair.value === item) {
                  currentForm.editTimePair.value = null
                }
                if (lastSelectedWhatTimeState.value === item) {
                  lastSelectedWhatTimeState.value = timePairRowItem.getOrNull(index)
                    ?: timePairRowItem.getOrNull(index - 1)!! // 只有一个时间段时是不展示删除按钮的
                }
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
          val error = currentForm.clickSwitchTimeCheck.firstNotNullOfOrNull { it() }
          if (error != null) {
            toast(error)
            return@clickable
          }
          val whatTimeModelEditor = lastSelectedWhatTimeState.value
          val idModelEditor = whatTimeModelEditor.idModelEditor
          var diff = 10
          while (true) {
            var startTime = whatTimeModelEditor.timePair.second.plusMinutes(diff)
            var endTime = startTime.plusMinutes(whatTimeModelEditor.timePair.durationMinute())
            if (endTime < startTime) {
              // 超过一天了
              endTime = whatTimeModelEditor.timePair.first.plusMinutes(-diff)
              startTime = endTime.plusMinutes(-whatTimeModelEditor.timePair.durationMinute())
              if (endTime < startTime) {
                // 还是超过一天，说明原有的 whatTimeModelEditor.timePair 太长了
                startTime = MinuteTime(8, 0).plusMinutes(diff)
                endTime = startTime.plusMinutes(60)
              }
            }
            val newWhatTimeModelEditor = idModelEditor.add(MinuteTimePair(startTime, endTime))
            if (newWhatTimeModelEditor != null) {
              lastSelectedWhatTimeState.value = newWhatTimeModelEditor
              currentForm.editTimePair.value = newWhatTimeModelEditor
              timePairRowItem.add(newWhatTimeModelEditor)
              break
            }
            diff += 10
          }
        }
      )
    }
  }
  DisposableEffect(Unit) {
    val check = {
      val emptyWhatTime = currentForm.editor.idModelEditor.whatTimeDate.mapNotNull {
        if (it.value.isEmpty()) it.key else null
      }
      if (emptyWhatTime.size == 1) {
        "${emptyWhatTime[0].timePair} 内对应的日期为空"
      } else if (emptyWhatTime.size > 1) {
        "以下时间段对应日期为空：\n${emptyWhatTime.joinToString("\n")}"
      } else null
    }
    currentForm.clickPrevCheck.add(check)
    currentForm.clickSwitchTimeCheck.add(check)
    onDispose {
      currentForm.clickPrevCheck.remove(check)
      currentForm.clickSwitchTimeCheck.remove(check)
    }
  }
}

@Composable
private fun DateFlowRow(
  currentForm: CurrentForm.Edit,
  courseState: CourseItemBottomSheetDialogState,
  lastSelectedWhatTimeState: MutableState<AffairWhatTimeModelEditor>,
  lastSelectedDateModelState: MutableState<AffairDateModelEditor>,
) {
  val dateList = remember(lastSelectedWhatTimeState.value) {
    lastSelectedWhatTimeState.value.dateList.sortedBy {
      it.date
    }.toMutableStateList()
  }
  FlowRow(
    modifier = Modifier.onGloballyPositioned {
      courseState.setImePeekBottomInWindow(it.positionInWindow().y + it.size.height + 10)
    },
    itemVerticalAlignment = Alignment.CenterVertically
  ) {
    dateList.fastForEach { item ->
      key(item) {
        DateFlowRowItem(
          dateModelEditor = item,
          dateList = dateList,
          currentForm = currentForm,
          lastSelectedDateModelState = lastSelectedDateModelState,
        )
      }
    }
    // + 号
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
          diff++ // 正常来说一直往后递增，一定会有能加进去的日期，这里不考虑最后一周越界的问题，一般也触发不了
        }
      }
    )
  }
  LaunchedEffect(Unit) {
    snapshotFlow { lastSelectedWhatTimeState.value }.drop(1).collect {
      val dateModel = lastSelectedWhatTimeState.value.dateList.firstOrNull()
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
      courseState.currentPageItemFlow.value =
        itemState.item.extensions.get(CourseItemBottomSheetDialogExtension::class)
    }
  }
}

@Composable
private fun DateFlowRowItem(
  dateModelEditor: AffairDateModelEditor,
  dateList: SnapshotStateList<AffairDateModelEditor>,
  currentForm: CurrentForm.Edit,
  lastSelectedDateModelState: MutableState<AffairDateModelEditor>,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 2.dp, bottom = 3.dp, end = 3.dp)
      .background(color = 0xACE8F0FC.dark(0xB32C2C2C), RoundedCornerShape(29.dp))
      .height(IntrinsicSize.Min)
      .clickable {
        if (lastSelectedDateModelState.value != dateModelEditor) {
          val error = currentForm.clickSwitchTimeCheck.firstNotNullOfOrNull { it() }
          if (error != null) {
            toast(error)
          } else {
            lastSelectedDateModelState.value = dateModelEditor
          }
        }
      }
  ) {
    val isSelected = rememberDerivedStateOfStructure {
      dateModelEditor === lastSelectedDateModelState.value
    }
    val dateState = remember { mutableStateOf(dateModelEditor.date) }
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
        if (dateModelEditor.whatTimeEditor?.remove(dateModelEditor) == true) {
          val index = dateList.indexOf(dateModelEditor)
          if (dateModelEditor === lastSelectedDateModelState.value) {
            lastSelectedDateModelState.value =
              dateList.getOrNull(index + 1) ?: dateList.getOrNull(index - 1) ?: dateModelEditor
          }
          dateList.remove(dateModelEditor)
        }
      },
    )
    DisposableEffect(Unit) {
      val check = {
        if (weekNumIsError.value) {
          "周数不合法，请先修改"
        } else if (dayOfWeekIsError.value) {
          "星期不合法，请先修改"
        } else null
      }
      currentForm.clickPrevCheck.add(check)
      currentForm.clickSwitchTimeCheck.add(check)
      onDispose {
        currentForm.clickPrevCheck.remove(check)
        currentForm.clickSwitchTimeCheck.remove(check)
      }
    }
    LaunchedEffect(Unit) {
      snapshotFlow { dateState.value }.collect {
        dateModelEditor.setDate(it)
      }
    }
  }
}