package com.cyxbs.pages.course.view.timeline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.AppColor
import com.cyxbs.components.config.compose.theme.AppDarkColor
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.course.view.timeline.data.FixedTimelineData
import com.cyxbs.pages.course.view.timeline.data.LessonTimelineData
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.collections.immutable.persistentListOf

/**
 * 默认课表时间轴
 *
 * @author 985892345
 * @date 2025/2/10
 */

internal val DefaultTimelineStartMinuteTime = MinuteTime(4, 0)

internal val DefaultTimelineTextColor = AppColor.tvLv1
internal val DefaultTimelineTextDarkColor = AppDarkColor.tvLv1
internal val DefaultTimelineLightTextColor = Color(0x66142C52)
internal val DefaultTimelineLightTextDarkColor = Color(0x80F0F0F0)

internal val DefaultTimeline = persistentListOf(
  MutableTimelineData(
    text = "···",
    optionText = "凌晨",
    startTime = DefaultTimelineStartMinuteTime,
    endTime = MinuteTime(8, 0),
    maxWeight = 4F,
    initialWeight = 0.1F,
  ),
  LessonTimelineData(1),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(1),
    endTime = CourseUtils.getStartMinuteTime(2),
    weight = 0.01F,
  ),
  LessonTimelineData(2),
  FixedTimelineData(
    text = "大课间",
    optionText = "大课间",
    startTime = MinuteTime(9, 40),
    endTime = MinuteTime(10, 15),
    weight = 0.05F,
    fontSize = 8.sp,
  ),
  LessonTimelineData(3),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(3),
    endTime = CourseUtils.getStartMinuteTime(4),
    weight = 0.01F,
  ),
  LessonTimelineData(4),
  MutableTimelineData(
    text = "中午",
    optionText = "中午",
    startTime = MinuteTime(11, 55),
    endTime = MinuteTime(14, 0),
    maxWeight = 2F,
    initialWeight = 0.1F,
    fontSize = 10.sp,
  ),
  LessonTimelineData(5),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(5),
    endTime = CourseUtils.getStartMinuteTime(6),
    weight = 0.01F,
  ),
  LessonTimelineData(6),
  FixedTimelineData(
    text = "大课间",
    optionText = "大课间",
    startTime = MinuteTime(15, 40),
    endTime = MinuteTime(16, 15),
    weight = 0.05F,
    fontSize = 8.sp,
  ),
  LessonTimelineData(7),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(7),
    endTime = CourseUtils.getStartMinuteTime(8),
    weight = 0.01F,
  ),
  LessonTimelineData(8),
  MutableTimelineData(
    text = "傍晚",
    optionText = "傍晚",
    startTime = MinuteTime(17, 55),
    endTime = MinuteTime(19, 0),
    maxWeight = 1F,
    initialWeight = 0.1F,
    fontSize = 10.sp,
  ),
  LessonTimelineData(9),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(9),
    endTime = CourseUtils.getStartMinuteTime(10),
    weight = 0.01F,
  ),
  LessonTimelineData(10),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(10),
    endTime = CourseUtils.getStartMinuteTime(11),
    weight = 0.01F,
  ),
  LessonTimelineData(11),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(11),
    endTime = CourseUtils.getStartMinuteTime(12),
    weight = 0.01F,
  ),
  LessonTimelineData(12),
  MutableTimelineData(
    text = "···",
    optionText = "深夜",
    startTime = MinuteTime(22, 30),
    endTime = DefaultTimelineStartMinuteTime,
    maxWeight = 5.5F,
    initialWeight = 0.2F,
  ),
)