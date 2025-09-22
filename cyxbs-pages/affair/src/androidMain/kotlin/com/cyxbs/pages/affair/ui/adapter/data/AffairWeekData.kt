package com.cyxbs.pages.affair.ui.adapter.data

import com.cyxbs.pages.affair.room.AffairEntity
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.api.utils.getEndRow
import com.cyxbs.pages.course.api.utils.getStartRow
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.lazyUnlock

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/6/12 21:12
 */
sealed interface AffairAdapterData {
  abstract override fun equals(other: Any?): Boolean
  val onlyId: Any // 唯一 id，用于比对 item 是否发生位置改变
}

/**
 * 用来显示周数的数据类
 */
data class AffairWeekData(
  val week: Int,
) : AffairAdapterData {
  override val onlyId: Any
    get() = week
  
  fun getWeekStr(): String {
    return WEEK_ARRAY[week]
  }
  
  companion object {
    /**
     * 这里面的个数跟 [ICourseService.maxWeek] 挂钩
     */
    val WEEK_ARRAY: Array<String> by lazyUnlock {
      com.cyxbs.components.init.appContext.resources.getStringArray(
        com.cyxbs.pages.course.api.R.array.course_api_course_weeks_strings)
    }
  }
}

/**
 * 用来显示时间的数据类
 */
data class AffairTimeData(
  val weekNum: Int, // 星期几，星期一 为 0
  val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
  val period: Int, // 长度
  val isWrapBefore: Boolean = false // 用于判断是否换行
) : AffairAdapterData {
  override val onlyId: Any
    get() = weekNum * 10000 + beginLesson * 100 + period
  
  fun getTimeStr(): String {
    val startRow = getStartRow(beginLesson)
    val endRow = getEndRow(beginLesson, period)
    return if (startRow == endRow) {
      "${DAY_ARRAY[weekNum]} ${LESSON_ARRAY[getStartRow(beginLesson)]}"
    } else {
      "${DAY_ARRAY[weekNum]} ${LESSON_ARRAY[getStartRow(beginLesson)]}-${LESSON_ARRAY[getEndRow(beginLesson, period)]}"
    }
  }
  
  companion object {
    val LESSON_ARRAY = arrayOf(
      "第一节课", //8:00
      "第二节课", //8:55
      "第三节课", //10:15
      "第四节课", //11:10
      "中午", //12:00
      "第五节课", //14:00
      "第六节课", //14:55
      "第七节课", //16:15
      "第八节课", //17:10
      "傍晚", //18:00
      "第九节课", //19:00
      "第十节课", //19:55
      "第十一节课", //20:50
      "第十二节课", //21:45
    )

    val DAY_ARRAY by lazyUnlock {
      val resources = com.cyxbs.components.init.appContext.resources
      arrayOf(
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_mon),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_tue),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_wed),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_thu),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_fri),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_sat),
        resources.getString(com.cyxbs.pages.course.api.R.string.course_api_week_sun),
      )
    }
  }
}

/**
 * 用来给事务显示加号的数据类
 */
object AffairTimeAdd : AffairAdapterData {
  override fun equals(other: Any?): Boolean = other is AffairTimeAdd
  override val onlyId: Any
    get() = javaClass.hashCode()
}

// 将展示的数据转换为要上传的数据
fun List<AffairAdapterData>.toAtWhatTime(): List<AffairEntity.AtWhatTime> {
  val newList = arrayListOf<AffairEntity.AtWhatTime>()
  val weekList = arrayListOf<Int>()
  val timeList = arrayListOf<AffairTimeData>()
  forEach {
    when (it) {
      is AffairWeekData -> {
        weekList.add(it.week)
      }
      is AffairTimeData -> {
        timeList.add(it)
      }
      is AffairTimeAdd -> {}
    }
  }
  timeList.forEach {
    newList.add(AffairEntity.AtWhatTime(it.beginLesson, it.weekNum, it.period, weekList))
  }
  return newList
}