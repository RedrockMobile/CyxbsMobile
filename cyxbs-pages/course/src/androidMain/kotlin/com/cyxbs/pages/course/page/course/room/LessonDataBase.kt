package com.cyxbs.pages.course.page.course.room

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyxbs.components.init.appContext
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.ConcurrentHashMap

/**
 * # 更新日志
 * 2024-3-26:
 * 课程出现错乱，导出数据库后发现，Room 中数据源有问题，
 * 但是导入有问题的数据库却又能正常更新，所以未排查到问题原因，
 *
 * 因为课程本来就是直接用后端的数据覆盖本地的，所以这里在保持接口不变的情况下改成了 sp
 *
 *
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/1 21:12
 */
object LessonDataBase {
  val stuLessonDao: StuLessonDao by lazy {
    StuLessonDao()
  }
  val teaLessonDao: TeaLessonDao by lazy {
    TeaLessonDao()
  }
  val lessonVerDao: LessonVerDao by lazy {
    LessonVerDao()
  }
}

sealed interface ILessonEntity {
  val course: String // 课程名
  val classroom: String // 教室
  val courseNum: String // 课程号
  val hashDay: Int // 星期数，星期一为 0
  val beginLesson: Int // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
  val period: Int // 课的长度
  val day: String // 星期几，这是字符串的星期几：星期一、星期二......
  val hashLesson: Int // 老代码数据，0 表示 1、2 节课，1 表示 3、4 节课，现已抛弃
  val lesson: String
  val rawWeek: String // 周期
  val teacher: String
  val type: String // 选修 or 必修
  val week: List<Int> // 第几周的课
  val weekBegin: Int
  val weekEnd: Int
  val weekModel: String
  
  val num: String // 学号或者教师工号 (不建议将次设置为数据库字段)
}

data class StuLessonEntity(
  val stuNum: String,
  override val beginLesson: Int,
  override val classroom: String,
  override val course: String,
  override val courseNum: String,
  override val day: String,
  override val hashDay: Int,
  override val hashLesson: Int,
  override val lesson: String,
  override val period: Int,
  override val rawWeek: String,
  override val teacher: String,
  override val type: String,
  override val week: List<Int>,
  override val weekBegin: Int,
  override val weekEnd: Int,
  override val weekModel: String,
) : ILessonEntity {
  override val num: String
    get() = stuNum
}

private val Gson = Gson()

class StuLessonDao {

  private val stuLessonSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_lesson", Context.MODE_PRIVATE)

  private val observerMap = ConcurrentHashMap<String, BehaviorSubject<List<StuLessonEntity>>>()
  
  fun observeLesson(stuNum: String): Observable<List<StuLessonEntity>> {
    return observerMap.getOrPut(stuNum) {
      val list = getLesson(stuNum)
      BehaviorSubject.createDefault(list)
    }
  }
  
  fun getLesson(stuNum: String): List<StuLessonEntity> {
    return stuLessonSp.getString(stuNum, null)?.let {
      Gson.fromJson(it, object : TypeToken<List<StuLessonEntity>>() {}.type)
    } ?: emptyList()
  }
  
  fun resetData(stuNum: String, lesson: List<StuLessonEntity>) {
    stuLessonSp.edit {
      putString(stuNum, Gson.toJson(lesson))
    }
    observerMap[stuNum]?.onNext(lesson)
  }
}

data class TeaLessonEntity(
  val teaNum: String,
  override val beginLesson: Int,
  override val classroom: String,
  override val course: String,
  override val courseNum: String,
  override val day: String,
  override val hashDay: Int,
  override val hashLesson: Int,
  override val lesson: String,
  override val period: Int,
  override val rawWeek: String,
  override val teacher: String,
  override val type: String,
  override val week: List<Int>,
  override val weekBegin: Int,
  override val weekEnd: Int,
  override val weekModel: String,
  val classNumber: List<String>,
) : ILessonEntity {
  override val num: String
    get() = teaNum
}

class TeaLessonDao {

  private val teaLessonSp = com.cyxbs.components.init.appContext.getSharedPreferences("tea_lesson", Context.MODE_PRIVATE)

  fun getLesson(teaNum: String): List<TeaLessonEntity> {
    return teaLessonSp.getString(teaNum, null)?.let {
      Gson.fromJson(it, object : TypeToken<List<TeaLessonEntity>>() {}.type)
    } ?: emptyList()
  }


  fun resetData(teaNum: String, lesson: List<TeaLessonEntity>) {
    teaLessonSp.edit {
      putString(teaNum, Gson.toJson(lesson))
    }
  }
}

class LessonVerDao {

  private val lessonVersionSp = com.cyxbs.components.init.appContext.getSharedPreferences("lesson_version", Context.MODE_PRIVATE)

  fun findVersion(num: String): String? {
    return lessonVersionSp.getString(num, null)
  }

  fun insertVersion(num: String, version: String) {
    lessonVersionSp.edit {
      putString(num, version)
    }
  }

  fun clear() {
    lessonVersionSp.edit { clear() }
  }
}