package com.cyxbs.pages.noclass.ui.noClassCourse

import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.noclass.bean.NoClassSpareTime

class NoClassPageDecoration : CoursePageDecoration<NoClassLessonItem>() {

    fun setAllData(data: HashMap<Int, NoClassSpareTime>) {
        val totalStudents = computeTotalStudents(data)
        val items = convertToItems(data, totalStudents)
        itemHierarchy.reset(items)
    }

    companion object {
        fun computeTotalStudents(data: HashMap<Int, NoClassSpareTime>): Int {
            return data.values.firstOrNull()?.mIdToNameMap?.size ?: 0
        }

        fun convertToItems(
            data: HashMap<Int, NoClassSpareTime>,
            totalStudents: Int
        ): List<NoClassLessonWhatTime> {
            val items = mutableListOf<NoClassLessonWhatTime>()
            data.forEach { (week, spareTime) ->
                for (day in 0..6) {
                    val line = spareTime.spareDayTime[day] ?: continue
                    convertDayItems(week, day, line, totalStudents, items)
                }
            }
            return items
        }

        private fun convertDayItems(
            page: Int,
            day: Int,
            line: NoClassSpareTime.SpareLineTime,
            totalStudents: Int,
            outItems: MutableList<NoClassLessonWhatTime>
        ) {
            addLessonPair(page, day, line, totalStudents, outItems, 1, 2)
            addLessonPair(page, day, line, totalStudents, outItems, 3, 4)
            addLessonPair(page, day, line, totalStudents, outItems, 5, 6)
            addLessonPair(page, day, line, totalStudents, outItems, 7, 8)
            addLessonPair(page, day, line, totalStudents, outItems, 9, 10)
            addLessonPair(page, day, line, totalStudents, outItems, 11, 12)
        }

        private fun addLessonPair(
            page: Int,
            day: Int,
            line: NoClassSpareTime.SpareLineTime,
            totalStudents: Int,
            outItems: MutableList<NoClassLessonWhatTime>,
            first: Int,
            second: Int
        ) {
            val id1 = line.SpareItem[first].spareId
            val id2 = line.SpareItem[second].spareId
            val isId1Busy = id1.size != totalStudents
            val isId2Busy = id2.size != totalStudents

            if (id1 == id2) {
                if (isId1Busy) {
                    addBusyBlock(page, day, first, 2, id1.size, totalStudents, outItems)
                }
            } else {
                if (isId1Busy) {
                    addBusyBlock(page, day, first, 1, id1.size, totalStudents, outItems)
                }
                if (isId2Busy) {
                    addBusyBlock(page, day, second, 1, id2.size, totalStudents, outItems)
                }
            }
        }

        private fun addBusyBlock(
            page: Int,
            day: Int,
            beginLesson: Int,
            length: Int,
            freeCount: Int,
            totalStudents: Int,
            outItems: MutableList<NoClassLessonWhatTime>
        ) {
            val busyCount = totalStudents - freeCount
            val text = if (freeCount == 0) "全员\n忙碌" else "${busyCount}人\n忙碌"
            outItems.add(
                NoClassLessonWhatTime.forPeriod(
                    page = page,
                    day = day,
                    beginLesson = beginLesson,
                    endLesson = beginLesson + length - 1,
                    topText = text,
                )
            )
        }
    }
}