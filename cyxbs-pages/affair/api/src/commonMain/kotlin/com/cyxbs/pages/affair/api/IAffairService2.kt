package com.cyxbs.pages.affair.api

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable


/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
interface IAffairService2 {

  /**
   * 观察登陆人的事务，如果处于未登陆状态则传递 null
   * @param needRequest 订阅时是否需要请求新的数据，默认为 false，主页课表会自动请求
   */
  fun observeAffair(
    needRequest: Boolean = false
  ): Flow<List<Affair>?>

  fun getCacheAffair(): List<Affair>?

  fun addAffair(affair: Affair)

  fun deleteAffair(id: Int)

  fun updateAffair(affair: Affair)

  @Serializable
  data class Affair(
    val id: Int,
    val remindTime: Int,
    val title: String,
    val content: String,
    val whatTime: List<AffairWhatTime>,
  )

  @Serializable
  data class AffairWhatTime(
    val start: MinuteTime,
    val end: MinuteTime,
    val date: List<Date>,
  )
}