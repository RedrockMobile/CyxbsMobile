package com.cyxbs.pages.course.home.dialog.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.toChinese
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.affair.api.AffairDateModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
@Composable
fun AffairBottomSheetDialog(affairDateModel: AffairDateModel) {
  SelectionContainer {
    Column(
      modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
      TitleWithBtn(title = affairDateModel.idModel.title.value)
      WeekAndTime(modifier = Modifier.padding(top = 8.dp), affairDateModel = affairDateModel)
      AffairContent(modifier = Modifier.padding(top = 20.dp), affairDateModel = affairDateModel)
    }
  }
}

@Composable
private fun TitleWithBtn(title: String) {
  val deleteConfirmDialogState = remember { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1F).basicMarquee(iterations = Int.MAX_VALUE),
      text = title,
      fontSize = 22.sp,
      color = LocalAppColors.current.tvLv2,
      fontWeight = FontWeight.Bold,
    )
    Icon(
      contentDescription = "编辑事务",
      painter = rememberVectorPainter(Icons.Outlined.Settings),
      tint = LocalAppColors.current.tvLv2,
      modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
        // 编辑面板
      },
    )
    Icon(
      contentDescription = "删除事务",
      painter = rememberVectorPainter(Icons.Outlined.Delete),
      tint = LocalAppColors.current.tvLv2,
      modifier = Modifier.padding(start = 8.dp).clickableNoIndicator {
        deleteConfirmDialogState.value = true
      },
    )
  }
  DeleteAffairDialog(showState = deleteConfirmDialogState)
}

@Composable
private fun WeekAndTime(modifier: Modifier, affairDateModel: AffairDateModel) {
  val text = remember { mutableStateOf("") }
  Text(
    text = text.value,
    fontSize = 13.sp,
    color = LocalAppColors.current.tvLv2,
    modifier = modifier,
  )
  LaunchedEffect(Unit) {
    combine(
      SchoolCalendar.observeFirstMonDayNullable(),
      affairDateModel.whatTime.map { it.timePair }.flattenConcat(),
      affairDateModel.date
    ) { firstDate, timePair, date ->
      val whichWeek = firstDate?.daysUntil(date)?.div(7)?.plus(1)
        ?.let { "第${Num2CN.number2ChineseNumber(it)}周" } ?: ""
      val dayOfWeek = date.dayOfWeek.toChinese()
      val timePair = timePair.toString()
      "$whichWeek $dayOfWeek $timePair"
    }.onEach {
      text.value = it
    }.launchIn(this)
  }
}

@Composable
private fun AffairContent(modifier: Modifier, affairDateModel: AffairDateModel) {
  Text(
    text = affairDateModel.idModel.content.collectAsState().value,
    fontSize = 15.sp,
    color = LocalAppColors.current.tvLv2,
    modifier = modifier
  )
}


@Composable
private fun DeleteAffairDialog(
  showState: MutableState<Boolean>,
) {
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "确定",
    negativeBtnText = "取消",
    onClickPositiveBtn = {
      toast("todo：删除事务")
      showState.value = false
    },
    onClickNegativeBtn = {
      showState.value = false
    }
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 28.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "确定要删除该事务吗？",
        color = LocalAppColors.current.tvLv2,
        fontSize = 14.sp,
      )
    }
  }
}