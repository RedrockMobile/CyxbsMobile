package com.cyxbs.pages.noclass.ui.noClassCourse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.HomeCoursePageContent
import com.cyxbs.pages.course.view.decoration.CoursePageDecorationManager
import com.cyxbs.pages.course.view.page.CourseFrameHeader
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.timeline.data.FixedTimelineData
import com.cyxbs.pages.course.view.timeline.data.LessonTimelineData
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import com.cyxbs.pages.noclass.bean.MAX_WEEK
import com.cyxbs.pages.noclass.bean.NoClassSpareTime
import kotlinx.collections.immutable.persistentListOf

class NoClassCourseFrame : AbstractCourseFrame() {

    override val maxWeek: Int = MAX_WEEK

    override val timeline: CourseTimeline = CourseTimeline(
        data = persistentListOf(
            FixedTimelineData(
                text = "", optionText = "",
                startTime = MinuteTime(0, 0), endTime = MinuteTime(8, 0),
                weight = 0.1F,
            ),
            LessonTimelineData(1),
            breakData(1),
            LessonTimelineData(2),
            breakData(2),
            LessonTimelineData(3),
            breakData(3),
            LessonTimelineData(4),
            MutableTimelineData(
                text = "中午", optionText = "中午",
                startTime = MinuteTime(11, 55), endTime = MinuteTime(14, 0),
                maxWeight = 2F, initialWeight = 0.1F, fontSize = 10.sp,
            ),
            LessonTimelineData(5),
            breakData(5),
            LessonTimelineData(6),
            breakData(6),
            LessonTimelineData(7),
            breakData(7),
            LessonTimelineData(8),
            MutableTimelineData(
                text = "傍晚", optionText = "下午",
                startTime = MinuteTime(17, 55), endTime = MinuteTime(19, 0),
                maxWeight = 1F, initialWeight = 0.1F, fontSize = 10.sp,
            ),
            LessonTimelineData(9),
            breakData(9),
            LessonTimelineData(10),
            breakData(10),
            LessonTimelineData(11),
            breakData(11),
            LessonTimelineData(12),
            FixedTimelineData(
                text = "", optionText = "",
                startTime = MinuteTime(22, 30), endTime = MinuteTime(23, 59),
                weight = 0.1F,
            ),
        ),
    )

    val decoration = NoClassPageDecoration()

    fun setAllData(data: HashMap<Int, NoClassSpareTime>) {
        decoration.setAllData(data)
    }

    companion object {
        private fun breakData(lesson: Int): FixedTimelineData {
            return FixedTimelineData(
                text = "",
                optionText = "",
                startTime = CourseUtils.getEndMinuteTime(lesson),
                endTime = CourseUtils.getStartMinuteTime(lesson + 1),
                weight = 0.1F,
            )
        }
    }
}

@Composable
fun NoClassCourseContent(
    noClassCourseFrame: NoClassCourseFrame,
    noclassData: HashMap<Int, NoClassSpareTime>,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val decorationManager = remember {
        CoursePageDecorationManager(
            courseFrame = noClassCourseFrame,
            courseCoroutineScope = coroutineScope,
            noClassCourseFrame.decoration,
        )
    }

    LaunchedEffect(noclassData) {
        noClassCourseFrame.setAllData(noclassData)
    }

    CompositionLocalProvider(
        AbstractCourseFrame.Local provides noClassCourseFrame,
        CoursePageDecorationManager.Local provides decorationManager,
    ) {
        Column(modifier = modifier) {
            CourseFrameHeader(
                modifier = Modifier.height(44.dp),
                frame = noClassCourseFrame,
                linkBtnVisibility = false,
            )
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = noClassCourseFrame.pagerState,
                pageContent = { page ->
                    noClassCourseFrame.HomeCoursePageContent(page = page)
                },
            )
        }
    }
}

