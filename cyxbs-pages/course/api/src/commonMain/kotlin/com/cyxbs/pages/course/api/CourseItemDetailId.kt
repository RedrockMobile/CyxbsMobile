package com.cyxbs.pages.course.api

import com.cyxbs.components.config.serializable.defaultJson
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable

/** 当前课表 Item 定位协议版本；版本只由课程模块解释，外部入口必须原样传递整个 ID。 */
private const val CourseItemIdVersion = 1

/**
 * 一个实际课表 Item 的不透明定位载荷。
 *
 * [itemKey] 区分同一业务生成的不同 CourseItem；同一 Item 被裁成多个可见区间时仍只生成一个载荷。
 * [detailId] 供 Android 独立详情容器实时查询业务内容，不参与课表 Item 定位。
 */
@Serializable
private data class CourseItemIdPayload(
  val version: Int,
  val week: Int,
  val itemKey: String,
  val detailId: String,
)

/**
 * 生成包含教学周的课表 Item ID。
 *
 * [week] 是 Item 所在教学周；[itemKey] 必须能区分该周内的实际 Item；[detailId] 是详情业务身份。
 * 返回值是不透明协议，课表外部只能原样保存和回传。
 */
fun courseItemId(week: Int, itemKey: String, detailId: String): String {
  require(week > 0) { "week 必须大于 0" }
  require(itemKey.isNotBlank()) { "itemKey 不能为空" }
  require(detailId.isNotBlank()) { "detailId 不能为空" }
  return defaultJson.encodeToString(
    CourseItemIdPayload(
      version = CourseItemIdVersion,
      week = week,
      itemKey = itemKey,
      detailId = detailId,
    )
  )
}

/** 从不透明 [itemId] 中读取教学周；非法、未知版本或旧格式 ID 返回 null。 */
fun courseItemWeekOrNull(itemId: String): Int? = decodeCourseItemId(itemId)?.week

/**
 * 从不透明 [itemId] 中读取详情业务身份。
 *
 * 仅供课程模块的实时详情查询使用；非法或未知版本 ID 返回 null。
 */
fun courseItemDetailIdOrNull(itemId: String): String? = decodeCourseItemId(itemId)?.detailId

/** 统一校验协议版本和必要字段，避免不同消费端各自接受半有效 ID。 */
private fun decodeCourseItemId(itemId: String): CourseItemIdPayload? = runCatching {
  defaultJson.decodeFromString<CourseItemIdPayload>(itemId)
}.getOrNull()?.takeIf {
  it.version == CourseItemIdVersion && it.week > 0 && it.itemKey.isNotBlank() && it.detailId.isNotBlank()
}

/** 自己课程的详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseLessonDetailIdPrefix = "lesson:"

/** 关联课程的详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseLinkedLessonDetailIdPrefix = "link-lesson:"

/** 日程详情标识前缀；标识内容由课程模块解释，外部入口不得自行解析。 */
const val CourseScheduleDetailIdPrefix = "schedule:"

/**
 * 生成课程或关联课程的稳定详情标识。
 *
 * 标识不包含教学周，因此同一课程跨周共用详情身份；[isLinked] 用于隔离本人和关联人的数据源。
 */
fun LessonByWeeks.courseItemDetailId(isLinked: Boolean): String {
  return courseLessonDetailId(
    isLinked = isLinked,
    courseNum = courseNum,
    dayOfWeek = dayOfWeek.isoDayNumber,
    beginLesson = beginLesson,
    period = period,
    rawWeek = rawWeek,
    teacher = teacher,
    classroom = classroom,
  )
}

/** ID 纯拼接实现，供协议测试避开 [LessonByWeeks] 的课节时间配置初始化。 */
internal fun courseLessonDetailId(
  isLinked: Boolean,
  courseNum: String,
  dayOfWeek: Int,
  beginLesson: Int,
  period: Int,
  rawWeek: String,
  teacher: String,
  classroom: String,
): String {
  val prefix = if (isLinked) CourseLinkedLessonDetailIdPrefix else CourseLessonDetailIdPrefix
  return "$prefix$courseNum:$dayOfWeek:$beginLesson:$period:$rawWeek:$teacher:$classroom"
}

/** 生成日程详情身份；移动或跨日后仍由 occurrence identity 定位同一业务对象。 */
fun scheduleCourseItemDetailId(identity: String): String = "$CourseScheduleDetailIdPrefix$identity"
