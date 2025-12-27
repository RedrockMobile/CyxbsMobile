package com.cyxbs.pages.course.frame.header

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.course.frame.AbstractCourseFrame
import com.cyxbs.pages.course.model.LinkLessonRepository
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_item_header_link_double
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_item_header_link_single
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */

// 课表头
// 默认 fillMaxSize 并向下居中
@Composable
fun CourseHeader(
  modifier: Modifier,
  frame: AbstractCourseFrame,
) {
  Box(modifier = modifier.fillMaxSize()) {
    Row(modifier = Modifier.align(Alignment.BottomStart)) {
      CourseHeaderTitle(
        frame = frame,
        modifier = Modifier.align(Alignment.Bottom)
          .padding(start = 16.dp, bottom = 4.dp),
      )
      CourseHeaderSubtitle(
        frame = frame,
        modifier = Modifier.align(Alignment.Bottom)
          .padding(start = 13.dp, bottom = 6.dp)
      )
    }
    CourseLinkStudentBtn(
      frame = frame,
      modifier = Modifier.align(Alignment.BottomEnd)
        .padding(end = 110.dp, bottom = 2.dp),
    )
    CourseHeaderBack(
      frame = frame,
      modifier = Modifier.align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 2.dp)
    )
  }
}

@Composable
private fun CourseHeaderTitle(
  modifier: Modifier,
  frame: AbstractCourseFrame,
) {
  Text(
    text = frame.pagerState.currentPage.let {
      if (it == 0) "整学期"
      else "第${Num2CN.number2ChineseNumber(it)}周"
    },
    modifier = modifier,
    color = LocalAppColors.current.tvLv2,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp
  )
}

@Composable
private fun CourseHeaderSubtitle(
  modifier: Modifier,
  frame: AbstractCourseFrame,
) {
  // 0 -> 1 -> 0
  val pageFraction by rememberDerivedStateOfStructure(frame) {
    val fraction = frame.pagerState.currentPageOffsetFraction
    1 - minOf(abs(fraction + frame.pagerState.currentPage - frame.initialPage), 1F)
  }
  Text(
    text = "(本周)",
    modifier = modifier.graphicsLayer {
      alpha = pageFraction
      scaleX = pageFraction
      scaleY = pageFraction
    },
    fontSize = 15.sp,
    color = LocalAppColors.current.tvLv2,
  )
}

@Composable
private fun CourseHeaderBack(modifier: Modifier, frame: AbstractCourseFrame) {
  val coroutineScope = rememberCoroutineScope()
  // 0 -> 1 -> 0
  val pageFraction by rememberDerivedStateOfStructure(frame) {
    val fraction = frame.pagerState.currentPageOffsetFraction
    1 - minOf(abs(fraction + frame.pagerState.currentPage - frame.initialPage), 1F)
  }
  Text(
    text = "回到本周",
    modifier = modifier.graphicsLayer {
      alpha = 1 - pageFraction
      translationX = pageFraction * size.width
    }.clip(CircleShape).background(
      brush = Brush.horizontalGradient(
        colors = listOf(Color.Blue, Color(0xFF8686FF)),
      )
    ).clickableNoIndicator {
      coroutineScope.launch {
        frame.pagerState.animateScrollToPage(frame.initialPage)
      }
    }.padding(vertical = 8.dp, horizontal = 16.dp),
    color = Color.White,
    fontSize = 13.sp,
  )
}

@Composable
private fun CourseLinkStudentBtn(
  modifier: Modifier,
  frame: AbstractCourseFrame,
) {
  // 0 -> 1 -> 0
  val pageFraction by rememberDerivedStateOfStructure(frame) {
    val fraction = frame.pagerState.currentPageOffsetFraction
    1 - minOf(abs(fraction + frame.pagerState.currentPage - frame.initialPage), 1F)
  }
  if (LinkLessonRepository.state.collectAsState().value.isNotNull()) {
    val enableShow = LinkLessonRepository.enableShow.collectAsState().value
    Image(
      contentDescription = if (enableShow) "点击不显示关联人课程" else "点击显示关联人课程",
      contentScale = ContentScale.Inside,
      painter = painterResource(
        if (enableShow) Res.drawable.course_ic_item_header_link_double
        else Res.drawable.course_ic_item_header_link_single,
      ),
      modifier = modifier.size(32.dp).graphicsLayer {
        translationX = (101.dp * pageFraction).toPx()
      }.clickableNoIndicator {
        LinkLessonRepository.changeVisible()
      },
    )
  }
}