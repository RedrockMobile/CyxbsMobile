package com.cyxbs.pages.ufield.fairground.model

import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.TodayNoEffect

/**
 * 邮乐园「来到天数」本地缓存
 *
 * 在本地记录用户「第一天的日期」（首次来到邮乐园的日期），离线时即可用
 * `首日.daysUntil(今天) + 1` 本地推算天数，无需每次依赖网络。
 *
 * 缓存使用 [defaultSettings]（multiplatform-settings，设备维度），
 * 存储 [Date.value]（Int 压缩表示）。
 */
object FairgroundDaysCache {

  private const val KEY_FIRST_DATE = "ufield_fairground_first_date"

  /** 已缓存的首日日期，未缓存返回 null */
  private fun getFirstDate(): Date? {
    val accountSettings = AccountSettings.now
    if (!accountSettings.hasKey(KEY_FIRST_DATE)) return null
    return Date(accountSettings.getInt(KEY_FIRST_DATE, 0))
  }

  private fun saveFirstDate(date: Date) {
    AccountSettings.now.putInt(KEY_FIRST_DATE, date.value)
  }

  /**
   * 读取本地缓存的天数（首日到今天，含首日即 +1），无缓存返回 null
   */
  fun getCachedDays(): Int? {
    val first = getFirstDate() ?: return null
    return first.daysUntil(TodayNoEffect) + 1
  }

  /**
   * 用服务端返回的天数反推并落地首日：firstDate = 今天 - (days - 1)。
   * 以服务端为准，纠正本地可能的漂移（如改过系统时间）。
   */
  fun saveDaysFromServer(days: Int) {
    saveFirstDate(TodayNoEffect.minusDays((days - 1).coerceAtLeast(0)))
  }
}
