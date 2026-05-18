package com.cyxbs.pages.noclass.bean

import com.cyxbs.pages.course.api.LessonByWeeks

const val MAX_WEEK = 25

data class NoClassSpareTime(
    val spareDayTime: HashMap<Int, SpareLineTime>
) {
    var mIdToNameMap: HashMap<String, String> = hashMapOf()

    data class SpareLineTime(
        val SpareItem: ArrayList<SpareIds>
    ) {
        data class SpareIds(
            val spareId: ArrayList<String>
        )
    }

    companion object {
        val EMPTY_PAGE = HashMap<Int, NoClassSpareTime>().apply {
            (0..MAX_WEEK).forEach {
                this[it] = getNewEmptySpareTime()
            }
        }
    }
}

fun Map<String, List<LessonByWeeks>>.toSpareTime(): HashMap<Int, NoClassSpareTime> {
    val studentSpareTimes: HashMap<Int, NoClassSpareTime> = HashMap()
    val stuIds = keys.toList()
    studentSpareTimes[0] = getNewSpareTime(stuIds)
    val semesterStu = studentSpareTimes[0]!!

    this.forEach { (stuNum, lessons) ->
        lessons.forEach { lesson ->
            val hashDay = lesson.dayOfWeek.ordinal
            lesson.week.forEach { week ->
                if (studentSpareTimes[week] == null) {
                    studentSpareTimes[week] = getNewSpareTime(stuIds)
                }
                val stu = studentSpareTimes[week]!!
                val line = stu.spareDayTime[hashDay]
                val semesterLine = semesterStu.spareDayTime[hashDay]
                (0 until lesson.period).forEach {
                    if (line!!.SpareItem[lesson.beginLesson + it].spareId.contains(stuNum)) {
                        line.SpareItem[lesson.beginLesson + it].spareId.remove(stuNum)
                        semesterLine!!.SpareItem[lesson.beginLesson + it].spareId.remove(stuNum)
                    }
                }
            }
        }
    }

    if (studentSpareTimes.size <= MAX_WEEK) {
        (0..MAX_WEEK).forEach {
            if (studentSpareTimes[it] == null) {
                studentSpareTimes[it] = getNewSpareTime(stuIds)
            }
        }
    }

    return studentSpareTimes
}

private fun getNewSpareTime(stuIds: List<String>): NoClassSpareTime {
    return NoClassSpareTime(hashMapOf()).apply {
        (0..6).forEach { day ->
            spareDayTime[day] =
                NoClassSpareTime.SpareLineTime(
                    ArrayList<NoClassSpareTime.SpareLineTime.SpareIds>(13).apply {
                        repeat(14) {
                            add(NoClassSpareTime.SpareLineTime.SpareIds(ArrayList(stuIds)))
                        }
                    }
                )
        }
    }
}

private fun getNewEmptySpareTime(): NoClassSpareTime {
    return NoClassSpareTime(hashMapOf()).apply {
        (0..6).forEach { day ->
            spareDayTime[day] =
                NoClassSpareTime.SpareLineTime(
                    ArrayList<NoClassSpareTime.SpareLineTime.SpareIds>(13).apply {
                        repeat(14) {
                            add(NoClassSpareTime.SpareLineTime.SpareIds(arrayListOf()))
                        }
                    }
                )
        }
    }
}
