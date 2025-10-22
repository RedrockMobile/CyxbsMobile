package com.cyxbs.pages.course.page.course.room

import android.content.Context
import androidx.core.content.edit
import com.cyxbs.components.utils.extensions.defaultGson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.atomicfu.locks.SynchronizedObject
import java.io.Serializable
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
  @SerializedName("stuNum")
  val stuNum: String,
  @SerializedName("beginLesson")
  override val beginLesson: Int,
  @SerializedName("classroom")
  override val classroom: String,
  @SerializedName("course")
  override val course: String,
  @SerializedName("courseNum")
  override val courseNum: String,
  @SerializedName("day")
  override val day: String,
  @SerializedName("hashDay")
  override val hashDay: Int,
  @SerializedName("hashLesson")
  override val hashLesson: Int,
  @SerializedName("lesson")
  override val lesson: String,
  @SerializedName("period")
  override val period: Int,
  @SerializedName("rawWeek")
  override val rawWeek: String,
  @SerializedName("teacher")
  override val teacher: String,
  @SerializedName("type")
  override val type: String,
  @SerializedName("week")
  override val week: List<Int>,
  @SerializedName("weekBegin")
  override val weekBegin: Int,
  @SerializedName("weekEnd")
  override val weekEnd: Int,
  @SerializedName("weekModel")
  override val weekModel: String,
) : ILessonEntity, Serializable {
  override val num: String
    get() = stuNum
}

class StuLessonDao {

  private val stuLessonSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_lesson", Context.MODE_PRIVATE)

  private val observerMap = ConcurrentHashMap<String, BehaviorSubject<List<StuLessonEntity>>>()

  private val synchronizedObject = SynchronizedObject()
  
  fun observeLesson(stuNum: String): Observable<List<StuLessonEntity>> {
    synchronized(synchronizedObject) {
      return observerMap.getOrPut(stuNum) {
        val list = getLesson(stuNum)
        BehaviorSubject.createDefault(list)
      }
    }
  }
  
  fun getLesson(stuNum: String): List<StuLessonEntity> {
    synchronized(synchronizedObject) {
      val cache = observerMap[stuNum]
      if (cache != null) {
        return cache.value ?: emptyList()
      }
      return getLessonFromSp(stuNum)
    }
  }

  private fun getLessonFromSp(stuNum: String): List<StuLessonEntity> {
    return stuLessonSp.getString(stuNum, null)?.let {
      runCatching<List<StuLessonEntity>> {
        val list = defaultGson.fromJson<List<StuLessonEntity>>(it, object : TypeToken<List<StuLessonEntity>>() {}.type)
        if (list.all { it.week == null }) {
          // 之前 StuLessonEntity 字段被混淆的，所以旧版本上存在一段时间数据是混淆格式，会导致这里反序列化不兼容
          // 所以如果 weeks 字段为空，则直接删除旧数据
          stuLessonSp.edit {
            remove(stuNum)
          }
          emptyList()
        } else {
          list
        }
      }.onFailure {
        stuLessonSp.edit {
          remove(stuNum)
        }
      }.getOrNull()
    } ?: emptyList()
  }
  
  fun resetData(stuNum: String, lesson: List<StuLessonEntity>) {
    synchronized(synchronizedObject) {
      stuLessonSp.edit {
        putString(stuNum, defaultGson.toJson(lesson))
      }
      observerMap[stuNum]?.onNext(lesson)
    }
  }
}

data class TeaLessonEntity(
  @SerializedName("teaNum")
  val teaNum: String,
  @SerializedName("beginLesson")
  override val beginLesson: Int,
  @SerializedName("classroom")
  override val classroom: String,
  @SerializedName("course")
  override val course: String,
  @SerializedName("courseNum")
  override val courseNum: String,
  @SerializedName("day")
  override val day: String,
  @SerializedName("hashDay")
  override val hashDay: Int,
  @SerializedName("hashLesson")
  override val hashLesson: Int,
  @SerializedName("lesson")
  override val lesson: String,
  @SerializedName("period")
  override val period: Int,
  @SerializedName("rawWeek")
  override val rawWeek: String,
  @SerializedName("teacher")
  override val teacher: String,
  @SerializedName("type")
  override val type: String,
  @SerializedName("week")
  override val week: List<Int>,
  @SerializedName("weekBegin")
  override val weekBegin: Int,
  @SerializedName("weekEnd")
  override val weekEnd: Int,
  @SerializedName("weekModel")
  override val weekModel: String,
  @SerializedName("classNumber")
  val classNumber: List<String>,
) : ILessonEntity, Serializable {
  override val num: String
    get() = teaNum
}

class TeaLessonDao {

  private val teaLessonSp = com.cyxbs.components.init.appContext.getSharedPreferences("tea_lesson", Context.MODE_PRIVATE)

  fun getLesson(teaNum: String): List<TeaLessonEntity> {
    return teaLessonSp.getString(teaNum, null)?.let {
      defaultGson.fromJson(it, object : TypeToken<List<TeaLessonEntity>>() {}.type)
    } ?: emptyList()
  }


  fun resetData(teaNum: String, lesson: List<TeaLessonEntity>) {
    teaLessonSp.edit {
      putString(teaNum, defaultGson.toJson(lesson))
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