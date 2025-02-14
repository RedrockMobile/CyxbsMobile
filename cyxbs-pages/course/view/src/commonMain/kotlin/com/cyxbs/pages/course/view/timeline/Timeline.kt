package com.cyxbs.pages.course.view.timeline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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

internal val DefaultTimeline = persistentListOf(
  MutableTimelineData(
    text = "···",
    optionText = "凌晨",
    startTime = DefaultTimelineStartMinuteTime,
    endTime = MinuteTime(8, 0),
    maxWeight = 4F,
    initialWeight = 0.1F,
    color = Color.DarkGray,
    hasTomorrow = false,
  ),
  LessonTimelineData(1, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(1),
    endTime = CourseUtils.getStartMinuteTime(2),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(2, false),
  FixedTimelineData(
    text = "大课间",
    optionText = "大课间",
    startTime = MinuteTime(9, 40),
    endTime = MinuteTime(10, 15),
    weight = 0.05F,
    fontSize = 8.sp,
    color = Color.DarkGray,
    hasTomorrow = false
  ),
  LessonTimelineData(3, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(3),
    endTime = CourseUtils.getStartMinuteTime(4),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(4, false),
  MutableTimelineData(
    text = "中午",
    optionText = "中午",
    startTime = MinuteTime(11, 55),
    endTime = MinuteTime(14, 0),
    maxWeight = 2F,
    initialWeight = 0.1F,
    fontSize = 10.sp,
    color = Color.DarkGray,
    hasTomorrow = false,
  ),
  LessonTimelineData(5, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(5),
    endTime = CourseUtils.getStartMinuteTime(6),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(6, false),
  FixedTimelineData(
    text = "大课间",
    optionText = "大课间",
    startTime = MinuteTime(15, 40),
    endTime = MinuteTime(16, 15),
    weight = 0.05F,
    fontSize = 8.sp,
    color = Color.DarkGray,
    hasTomorrow = false,
  ),
  LessonTimelineData(7, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(7),
    endTime = CourseUtils.getStartMinuteTime(8),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(8, false),
  MutableTimelineData(
    text = "傍晚",
    optionText = "傍晚",
    startTime = MinuteTime(17, 55),
    endTime = MinuteTime(19, 0),
    maxWeight = 1F,
    initialWeight = 0.1F,
    fontSize = 10.sp,
    color = Color.DarkGray,
    hasTomorrow = false,
  ),
  LessonTimelineData(9, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(9),
    endTime = CourseUtils.getStartMinuteTime(10),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(10, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(10),
    endTime = CourseUtils.getStartMinuteTime(11),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(11, false),
  FixedTimelineData(
    text = "",
    optionText = "课间",
    startTime = CourseUtils.getEndMinuteTime(11),
    endTime = CourseUtils.getStartMinuteTime(12),
    weight = 0.01F,
    hasTomorrow = false
  ),
  LessonTimelineData(12, false),
  MutableTimelineData(
    text = "···",
    optionText = "深夜",
    startTime = MinuteTime(22, 30),
    endTime = DefaultTimelineStartMinuteTime,
    maxWeight = 5.5F,
    initialWeight = 0.2F,
    color = Color.DarkGray,
    hasTomorrow = true,
  ),
)