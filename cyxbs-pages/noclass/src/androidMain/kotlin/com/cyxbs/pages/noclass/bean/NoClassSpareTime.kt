// NoClassSpareTime 数据类已迁移至 commonMain
// 此文件仅保留依赖 android 特有 API (ILessonService.Lesson, ICourseService.maxWeek) 的扩展函数
@file:JvmName("NoClassSpareTimeAndroidKt")
package com.cyxbs.pages.noclass.bean

import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.api.ILessonService

fun Map<String, List<ILessonService.Lesson>>.toSpareTime(): HashMap<Int, NoClassSpareTime> {
    val studentSpareTimes: HashMap<Int, NoClassSpareTime> = HashMap()
    val stuIds = keys.toList()
    studentSpareTimes[0] = getNewSpareTime(stuIds)
    val semesterStu = studentSpareTimes[0]!!

    this.forEach { entry ->
        entry.value.forEach { lesson ->
            if (studentSpareTimes[lesson.week] == null) {
                studentSpareTimes[lesson.week] = getNewSpareTime(stuIds)
            }
            val stu: NoClassSpareTime = studentSpareTimes[lesson.week]!!
            val line = stu.spareDayTime[lesson.hashDay]
            val semesterLine = semesterStu.spareDayTime[lesson.hashDay]
            (0 until lesson.period).forEach {
                if (line!!.SpareItem[lesson.beginLesson + it].spareId.contains(lesson.stuNum)) {
                    line.SpareItem[lesson.beginLesson + it].spareId.remove(lesson.stuNum)
                    semesterLine!!.SpareItem[lesson.beginLesson + it].spareId.remove(lesson.stuNum)
                }
            }
        }
    }

    if (studentSpareTimes.size <= ICourseService.maxWeek) {
        (0..ICourseService.maxWeek).forEach {
            if (studentSpareTimes[it] == null) {
                studentSpareTimes[it] = getNewSpareTime(stuIds)
            }
        }
    }

    return studentSpareTimes
}

private fun getNewSpareTime(stuIds: List<String>): NoClassSpareTime {
    return NoClassSpareTime(hashMapOf()).apply {
        (0..6).forEach {
            spareDayTime[it] =
                NoClassSpareTime.SpareLineTime(
                    ArrayList<NoClassSpareTime.SpareLineTime.SpareIds>(13).apply {
                        (0..13).forEach { _ ->
                            add(NoClassSpareTime.SpareLineTime.SpareIds(ArrayList(stuIds)))
                        }
                    }
                )
        }
    }
}

private fun getNewEmptySpareTime(): NoClassSpareTime {
    return NoClassSpareTime(hashMapOf()).apply {
        (0..6).forEach {
            spareDayTime[it] =
                NoClassSpareTime.SpareLineTime(
                    ArrayList<NoClassSpareTime.SpareLineTime.SpareIds>(13).apply {
                        (0..13).forEach { _ ->
                            add(NoClassSpareTime.SpareLineTime.SpareIds(arrayListOf()))
                        }
                    }
                )
        }
    }
}
