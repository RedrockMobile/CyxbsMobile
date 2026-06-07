package com.cyxbs.pages.noclass.ui.noClassCourse

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.api.CourseUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek

class NoClassLessonItem(
    whatTime: CourseItemWhatTime,
    coroutineScope: CoroutineScope,
    val topText: String,
    val textColor: Color,
    val backgroundColor: Color,
    val beginLesson: Int,
    val lessonLength: Int,
    val onClick: ((NoClassLessonItem) -> Unit)? = null,
) : CourseItem(whatTime, coroutineScope) {

    @Composable
    override fun CourseItemContent() {
        CourseDefaultItemContent(
            itemState = itemState,
            topText = topText,
            bottomText = "",
            textColor = textColor,
            backgroundColor = backgroundColor,
            onClick = { onClick?.invoke(this@NoClassLessonItem) },
        )
    }
}

data class NoClassLessonWhatTime(
    val page: Int,
    val dayOfWeek: DayOfWeek,
    private val itemBeginTime: MinuteTime,
    private val itemFinalTime: MinuteTime,
    val topText: String,
    val textColor: Color,
    val backgroundColor: Color,
    val beginLesson: Int,
    val lessonLength: Int,
    val onClick: ((NoClassLessonItem) -> Unit)? = null,
) : ItemHierarchyWhatTime<NoClassLessonItem>() {

    override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
        CourseItemWhatTime.Fixed(page, dayOfWeek, itemBeginTime, itemFinalTime)
    )

    override fun createItem(coroutineScope: CoroutineScope): NoClassLessonItem {
        return NoClassLessonItem(
            whatTime = this,
            coroutineScope = coroutineScope,
            topText = topText,
            textColor = textColor,
            backgroundColor = backgroundColor,
            beginLesson = beginLesson,
            lessonLength = lessonLength,
            onClick = onClick,
        )
    }

    companion object {
        fun forPeriod(
            page: Int,
            day: Int,
            beginLesson: Int,
            endLesson: Int,
            topText: String,
            onClick: ((NoClassLessonItem) -> Unit)? = null,
        ): NoClassLessonWhatTime {
            val beginTime = CourseUtils.getStartMinuteTime(beginLesson)
            val finalTime = CourseUtils.getEndMinuteTime(endLesson)
            val (textColor, bgColor) = getColorsForLesson(beginLesson)
            return NoClassLessonWhatTime(
                page = page,
                dayOfWeek = DayOfWeek.entries[day],
                itemBeginTime = beginTime,
                itemFinalTime = finalTime,
                topText = topText,
                textColor = textColor,
                backgroundColor = bgColor,
                beginLesson = beginLesson,
                lessonLength = endLesson - beginLesson + 1,
                onClick = onClick,
            )
        }

        private fun getColorsForLesson(beginLesson: Int): Pair<Color, Color> {
            return when {
                beginLesson <= 4 -> NoClassCourseColor.morningText to NoClassCourseColor.morningBg
                beginLesson <= 8 -> NoClassCourseColor.afternoonText to NoClassCourseColor.afternoonBg
                else -> NoClassCourseColor.eveningText to NoClassCourseColor.eveningBg
            }
        }
    }
}
