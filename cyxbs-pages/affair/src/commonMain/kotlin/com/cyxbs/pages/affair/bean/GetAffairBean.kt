package com.cyxbs.pages.affair.bean

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.components.utils.network.IApiWrapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
@Serializable
data class GetAffairBean(
  @SerialName("data")
  override val `data`: List<ContentBean>,
  @SerialName("info")
  override val info: String,
  @SerialName("state")
  val state: Int,
  @SerialName("status")
  override val status: Int,
  @SerialName("stuNum")
  val stuNum: String,
  @SerialName("term")
  val term: Int
) : IApiWrapper<List<GetAffairBean.ContentBean>> {

  @Serializable
  data class ContentBean(
    @SerialName("content")
    val content: String,
    @SerialName("date")
    val oldDate: List<AffairDateBean>,
    @SerialName("id")
    val id: Int,
    @SerialName("time")
    val remindTime: Int,
    @SerialName("title")
    val title: String
  ) {

    /**
     * Compose 版本课表在旧版课表接口上实现了随意时间段的新事务
     *
     * 新版本事务按一下规则进行解析:
     * - 如果 day == -1，
     *   - 则表示该事务是任意时间段的事务
     *   - 则 begin_lesson 字段表示 事务开始时间，格式为 小时数 * 100 + 分钟数
     *   - 则 period 字段表示 事务持续时间，单位分钟
     *   - 则 week 字段表示该事务在哪几天，格式为 年份 * 10000 + 月份 * 100 + 日
     */
    fun toAffair(): AffairEntity? {
      val affair = runCatching {
        AffairEntity(
          remoteId = id,
          remindTime = remindTime,
          title = title,
          content = content,
          whatTime = oldDate.mapNotNull { bean ->
            if (bean.day == -1) {
              // Compose 版事务
              val start = MinuteTime(hour = bean.beginLesson / 100, minute = bean.beginLesson % 100)
              if (bean.period <= 0) throw IllegalArgumentException("period 不能小于等于 0")
              val end = start.plusMinutes(bean.period)
              if (end <= start) throw IllegalArgumentException("结束时间不能小于等于开始时间")
              val date = bean.week.map {
                Date(year = it / 10000, month = (it % 10000) / 100, dayOfMonth = it % 100)
              }
              if (date.isEmpty()) null else AffairWhatTime(MinuteTimePair(start, end), date)
            } else if (bean.day >= 0) {
              // 旧版事务
              // 旧版事务中 day 为星期数，星期一为 0
              val firstDate = SchoolCalendar.getFirstMonDay()
              // 因为旧版本的事务需要开学第一天计算出日期，所以这里会存在没有开学第一天就没有日期的情况
              // 但后续升级后旧版本事务会越来越少，暂时就保持这种逻辑吧
              if (firstDate != null) {
                val start = getStartTimeMinute(getStartRow(bean.beginLesson))
                val end = getEndTimeMinute(getEndRow(bean.beginLesson, bean.period))
                if (end <= start) throw IllegalArgumentException("结束时间不能小于等于开始时间")
                val date = bean.week.map {
                  firstDate.plusDays((it - 1) * 7 + bean.day)
                }
                if (date.isEmpty()) null else AffairWhatTime(MinuteTimePair(start, end), date)
              } else null
            } else null
          }
        )
      }.onFailure {
        if (isDebug()) {
          showExceptionDialog(RuntimeException("下发事务存在转换异常, $this", it))
        }
      }.getOrNull()
      if (affair == null) return null
      if (affair.whatTime.isEmpty()) return null
      return affair
    }
  }

  @Serializable
  data class AffairDateBean(
    @SerialName("begin_lesson")
    val beginLesson: Int,
    @SerialName("period")
    val period: Int,
    @SerialName("day")
    val day: Int,
    @SerialName("week")
    val week: List<Int>,
  )
}

/**
 * 把 [beginLesson] 转换成对应的 row
 */
private fun getStartRow(beginLesson: Int): Int {
  return when (beginLesson) {
    in 1 .. 4 -> beginLesson - 1
    in 5 .. 8 -> beginLesson
    in 9 .. 12 -> beginLesson + 1
    -1 -> 4 // 中午
    -2 -> 9 // 傍晚
    else -> throw IllegalArgumentException("出现了未知的时间点，beginLesson = $beginLesson")
  }
}

/**
 * 得到结尾对应的 row
 */
private fun getEndRow(beginLesson: Int, period: Int): Int {
  val start = getStartRow(beginLesson)
  return start + period - 1
}

/**
 * @param startRow 这个 [startRow] 是调用了 [getStartRow] 转换后的值
 * @return [startRow] 行表示的开始上课的分钟数
 */
private fun getStartTimeMinute(startRow: Int): MinuteTime {
  return when (startRow) {
    0 -> MinuteTime(8, 0) // 第一节课开始
    1 -> MinuteTime(8, 55) // 第二节课开始
    2 -> MinuteTime(10, 15) // 第三节课开始
    3 -> MinuteTime(11, 10) // 第四节课开始
    4 -> MinuteTime(11, 55) // 中午开始
    5 -> MinuteTime(14, 0) // 第五节课开始
    6 -> MinuteTime(14, 55) // 第六节课开始
    7 -> MinuteTime(16, 10) // 第七节课开始
    8 -> MinuteTime(17, 10) // 第八节课开始
    9 -> MinuteTime(17, 55) // 傍晚开始
    10 -> MinuteTime(19, 0) // 第九节课开始
    11 -> MinuteTime(19, 55) // 第十节课开始
    12 -> MinuteTime(20, 50) // 第十一节课开始
    13 -> MinuteTime(21, 45) // 第十二节课开始
    else -> error("startRow = $startRow")
  }
}

/**
 * @param endRow 这个 [endRow] 是调用了 [getEndRow] 转换后的值，也可以是 row
 * @return [endRow] 行表示的下课的分钟数
 */
private fun getEndTimeMinute(endRow: Int): MinuteTime {
  return when (endRow) {
    0 -> MinuteTime(8, 45) // 第一节课结束
    1 -> MinuteTime(9, 40) // 第二节课结束
    2 -> MinuteTime(11, 0) // 第三节课结束
    3 -> MinuteTime(11, 55) // 第四节课结束
    4 -> MinuteTime(14, 0) // 中午结束（兼容旧版本逻辑）
    5 -> MinuteTime(14, 45) // 第五节课结束
    6 -> MinuteTime(15, 40) // 第六节课结束
    7 -> MinuteTime(17, 0) // 第七节课结束
    8 -> MinuteTime(17, 55) // 第八节课结束
    9 -> MinuteTime(19, 0) // 傍晚结束（兼容旧版本逻辑）
    10 -> MinuteTime(19, 45) // 第九节课结束
    11 -> MinuteTime(20, 40) // 第十节课结束
    12 -> MinuteTime(21, 35) // 第十一节课结束
    13 -> MinuteTime(22, 30) // 第十二节课结束
    else -> error("endRow = $endRow")
  }
}