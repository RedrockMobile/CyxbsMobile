package com.cyxbs.components.config.time

/**
 * 节日
 *
 * @author 985892345
 * @date 2024/2/17 18:27
 */
object Festival {

  fun get(date: Date): String? {
    return festivals["${date.monthNumber}-${date.dayOfMonth}"]
      ?: date.toChineseCalendar().let { chinese ->
        chineseFestivals["${chinese.month}-${chinese.dayOfMonth}"]
          ?: complexFestivals.firstOrNull {
            it.first.run { date.run { match(chinese) } }
          }?.second
      }
  }

  // 新历节日
  private val festivals = mapOf(
    "1-1" to "元旦节",
    "2-14" to "情人节",
    "5-1" to "劳动节",
    "5-4" to "青年节",
    "6-1" to "儿童节",
    "9-10" to "教师节",
    "10-1" to "国庆节",
    "12-25" to "圣诞节",

    "3-8" to "妇女节",
    "3-12" to "植树节",
    "4-1" to "愚人节",
    "5-12" to "护士节",
    "7-1" to "建党节",
    "8-1" to "建军节",
    "12-24" to "平安夜",
  )

  // 农历节日
  private val chineseFestivals = mapOf(
    "1-1" to "春节",
    "1-15" to "元宵节",
    "2-2" to "龙抬头",
    "5-5" to "端午节",
    "7-7" to "七夕节",
    "7-15" to "中元节",
    "8-15" to "中秋节",
    "9-9" to "重阳节",
    "10-1" to "寒衣节",
    "10-15" to "下元节",
    "12-8" to "腊八节",
    "12-23" to "北方小年",
    "12-24" to "南方小年",
  )

  // 农历复杂节日，需要通过计算才可以确定
  private val complexFestivals: List<Pair<Complex, String>> = listOf(
    Complex {
      val isFinalDay = it.dayOfMonth == it.lengthOfMonth()
      val isLeapMonth = ChineseCalendar.getLeapMonth(it.year) != 0
      val isFinalMonth = isLeapMonth && it.monthPosition == 13 || !isLeapMonth && it.monthPosition == 12
      isFinalDay && isFinalMonth
    } to "除夕"
  )

  private fun interface Complex {
    fun Date.match(calendar: ChineseCalendar): Boolean
  }
}