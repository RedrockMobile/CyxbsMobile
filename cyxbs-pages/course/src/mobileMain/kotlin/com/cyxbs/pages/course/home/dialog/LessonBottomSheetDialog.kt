package com.cyxbs.pages.course.home.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.toChinese
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.course.api.LessonByWeeks
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_item_header_link_double
import org.jetbrains.compose.resources.painterResource

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/29
 */
@Composable
fun LessonBottomSheetDialog(lesson: LessonByWeeks, enableShowLinkIcon: Boolean) {
  Column(
    modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)
  ) {
    TitleWithLinkIcon(lesson.course, enableShowLinkIcon)
    ClassroomWithTeacher(lesson.classroom, lesson.teacher)
    TwoTextLine("周期", lesson.rawWeek)
    TwoTextLine("时间", "${lesson.dayOfWeek.toChinese()} ${lesson.beginTime}-${lesson.finalTime}")
    TwoTextLine("课程类型", lesson.type)
  }
}

@Composable
private fun TitleWithLinkIcon(title: String, enableShowLinkIcon: Boolean) {
  Layout(
    modifier = Modifier.fillMaxWidth(),
    content = {
      Text(
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        text = title,
        fontSize = 22.sp,
        color = LocalAppColors.current.tvLv2,
        fontWeight = FontWeight.Bold,
      )
      if (enableShowLinkIcon) {
        Image(
          contentDescription = "关联人的课程",
          contentScale = ContentScale.Inside,
          painter = painterResource(Res.drawable.course_ic_item_header_link_double),
          modifier = Modifier.clickableNoIndicator {
            // todo 跳转到搜索课表界面并打开关联人的课表
          },
        )
      }
    },
    measurePolicy = { measurables, constraints ->
      val icon = measurables.getOrNull(1)?.measure(
        Constraints(
          maxWidth = constraints.maxWidth,
          maxHeight = constraints.maxHeight,
        )
      )
      val textTitle = measurables[0].measure(
        Constraints(
          maxWidth = constraints.maxWidth - (icon?.width?.plus(16.dp.roundToPx()) ?: 0),
          maxHeight = constraints.maxHeight,
        )
      )
      layout(constraints.maxWidth, textTitle.height) {
        textTitle.placeRelative(0, 0)
        icon?.placeRelative(
          constraints.maxWidth - icon.width,
          (textTitle.height - icon.height) / 2
        )
      }
    }
  )
}

@Composable
private fun ClassroomWithTeacher(classroom: String, teacher: String) {
  Layout(
    modifier = Modifier.padding(top = 8.dp),
    content = {
      Text(
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        text = classroom,
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
      )
      Icon(
        modifier = Modifier.padding(start = 3.dp, bottom = 1.dp).size(12.dp),
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = null,
        tint = LocalAppColors.current.tvLv2,
      )
      Text(
        modifier = Modifier.padding(start = 8.dp),
        text = teacher,
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2,
      )
    },
    measurePolicy = { measurables, constraints ->
      val textTeacher = measurables[2].measure(constraints)
      val icon = measurables[1].measure(constraints)
      val textClassroom = measurables[0].measure(
        Constraints(
          maxWidth = constraints.maxWidth - textTeacher.width - icon.width,
          minHeight = textTeacher.height,
          maxHeight = textTeacher.height,
        )
      )
      layout(textClassroom.width + icon.width + textTeacher.width, textTeacher.height) {
        textClassroom.placeRelative(0, 0)
        icon.placeRelative(textClassroom.width, (textTeacher.height - icon.height) / 2)
        textTeacher.placeRelative(textClassroom.width + icon.width, 0)
      }
    }
  )
}

@Composable
private fun TwoTextLine(
  text1: String,
  text2: String,
) {
  Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
    Text(
      modifier = Modifier.align(Alignment.TopStart),
      text = text1,
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2,
    )
    Text(
      modifier = Modifier.align(Alignment.TopEnd),
      text = text2,
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2,
      fontWeight = FontWeight.Bold,
    )
  }
}