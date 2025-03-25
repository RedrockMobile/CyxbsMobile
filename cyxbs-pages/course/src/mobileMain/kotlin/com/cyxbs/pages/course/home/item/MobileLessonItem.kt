package com.cyxbs.pages.course.home.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.BottomSheetCompose
import com.cyxbs.components.utils.compose.BottomSheetValueState
import com.cyxbs.components.utils.compose.rememberBottomSheetState
import com.cyxbs.components.view.ui.Window
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.header.BottomSheetItemHeader
import com.cyxbs.pages.course.view.item.CourseItem
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/25
 */
abstract class MobileLessonItem(
  override val page: Int,
  val lesson: LessonByWeeks
) : CourseItem, BottomSheetItemHeader {
  override val dayOfWeek: DayOfWeek
    get() = lesson.dayOfWeek
  override val beginTime: MinuteTime
    get() = lesson.beginTime
  override val finalTime: MinuteTime
    get() = lesson.finalTime

  @Composable
  fun CourseBottomSheetDialog(
    visibleState: MutableState<Boolean>,
  ) {
    if (visibleState.value) {
      Window(
        dismissOnBackPress = {
          visibleState.value = false
        }
      ) {
        val bottomSheetState = rememberBottomSheetState()
        BottomSheetCompose(
          modifier = Modifier,
          bottomSheetState = bottomSheetState,
          dismissOnClickOutside = true,
          scrimColor = Color.Transparent,
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Spacer(
              modifier = Modifier.fillMaxWidth().height(36.dp).background(
                brush = Brush.verticalGradient(
                  colors = listOf(Color(0x005369BC), Color(0x205369BC))
                )
              )
            )
            Box(
              modifier = Modifier.padding(top = 20.dp)
                .fillMaxSize()
                .then(bottomSheetDraggable())
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(LocalAppColors.current.whiteBlack)
            ) {
            }
          }
        }
        LaunchedEffect(Unit) {
          bottomSheetState.expand()
          bottomSheetState.stateFlow.first { it == BottomSheetValueState.Collapsed }
          visibleState.value = false
        }
      }
    }
  }
}

