package com.cyxbs.pages.course.page.course.bean

import com.cyxbs.components.base.crash.CrashDialog
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.utils.network.IApiWrapper
import com.cyxbs.pages.course.api.utils.checkCourseItem
import com.cyxbs.pages.course.page.course.room.ILessonEntity
import com.cyxbs.pages.course.page.course.room.StuLessonEntity
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * 详细的字段解释请看数据库中的实体类 [ILessonEntity]
 */
data class StuLessonBean(
  @SerializedName("data")
  override val `data`: List<StuLesson>,
  @SerializedName("info")
  override val info: String,
  @SerializedName("nowWeek")
  val nowWeek: Int,
  @SerializedName("status")
  override val status: Int,
  @SerializedName("stuNum")
  val stuNum: String,
  @SerializedName("version")
  override val version: String
) : Serializable, ILessonVersion, IApiWrapper<List<StuLessonBean.StuLesson>> {
  data class StuLesson(
    @SerializedName("begin_lesson")
    val beginLesson: Int,
    @SerializedName("classroom")
    val classroom: String,
    @SerializedName("course")
    val course: String,
    @SerializedName("course_num")
    val courseNum: String,
    @SerializedName("day")
    val day: String,
    @SerializedName("hash_day")
    val hashDay: Int, // 星期数，0 为星期一
    @SerializedName("hash_lesson")
    val hashLesson: Int, // 课的起始数（我也不知道怎么具体描述），0 为 1、2 节课，1 为 3、4 节课，依次类推
    @SerializedName("lesson")
    val lesson: String,
    @SerializedName("period")
    val period: Int, // 课的长度
    @SerializedName("rawWeek")
    val rawWeek: String,
    @SerializedName("teacher")
    val teacher: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("week")
    val week: List<Int>,
    @SerializedName("week_begin")
    val weekBegin: Int,
    @SerializedName("week_end")
    val weekEnd: Int,
    @SerializedName("weekModel")
    val weekModel: String
  ) : Serializable
  
  override val num: String
    get() = stuNum
  
  fun toStuLessonEntity(): List<StuLessonEntity> {
    var isNotified = false
    return buildList {
      data.filter {
        // 检查数据是否存在越界
        // debug 版查他课表就会自动显示异常原因弹窗
        if (checkCourseItem(it.beginLesson, it.period)) {
          true
        } else {
          if (isDebug()) {
            // debug 版查他人课表直接显示异常原因
            toast("发生课表数据越界！")
            CrashDialog.Builder(RuntimeException("lesson=$it")).show()
          } else {
            toastLong("课表数据存在越界，请向我们反馈")
          }
          false
        }
      }.forEach {
        try {
          add(
            StuLessonEntity(
              stuNum,
              it.beginLesson,
              it.classroom,
              it.course,
              it.courseNum,
              it.day,
              it.hashDay,
              it.hashLesson,
              it.lesson,
              it.period,
              it.rawWeek,
              it.teacher,
              it.type,
              it.week,
              it.weekBegin,
              it.weekEnd,
              it.weekModel
            )
          )
        } catch (e: Exception) {
          // 后端返回的数据经常出问题，如果有人在群里反馈，
          // 就打开 debug 版查他课表就会自动显示异常原因弹窗
          if (!isNotified) {
            isNotified = true
            if (isDebug()) {
              // debug 版查他人课表直接显示异常原因
              toast("发生课表数据异常！")
              CrashDialog.Builder(RuntimeException("lesson=$it", e)).show()
            } else {
              toastLong("课表数据可能存在异常，请向我们反馈")
            }
          }
        }
      }
    }
  }
}