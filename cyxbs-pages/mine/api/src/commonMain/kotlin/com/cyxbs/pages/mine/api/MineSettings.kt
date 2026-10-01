package com.cyxbs.pages.mine.api

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.sp.defaultSettings

/** 设置页对其他模块提供的设备级设置，不随登录账号切换。 */
object MineSettings {

  // 沿用原安卓 key 保留已有设置；外部业务仅通过本对象的读写方法访问。
  private const val SP_COURSE_SHOW_STATE = "course_show_state"

  /**
   * 是否在启动 App 时优先显示课表。
   *
   * iOS 合并 KMP 与旧原生设置的开启状态，兼容已有选择；读取不会迁移或写入数据。
   */
  fun isShowCourseFirst(): Boolean =
    defaultSettings.getBoolean(SP_COURSE_SHOW_STATE, false) ||
      CourseLaunchLegacySettings::class.implOrNull()?.isShowCourseFirst() == true

  /**
   * 保存启动课表开关。
   *
   * @param enabled 开启时只写 KMP key；关闭时同时清空平台旧 key，避免旧值再次开启开关。
   */
  fun setShowCourseFirst(enabled: Boolean) {
    defaultSettings.putBoolean(SP_COURSE_SHOW_STATE, enabled)
    if (!enabled) CourseLaunchLegacySettings::class.implOrNull()?.clearShowCourseFirst()
  }
}

/**
 * 启动课表开关的可选历史数据兼容能力。
 *
 * 仅存在历史 key 的平台通过 KtProvider 注册实现；其他平台无需实现，直接使用 KMP 设置。
 */
interface CourseLaunchLegacySettings {

  /** 读取历史开关，旧 key 缺失时返回 false。 */
  fun isShowCourseFirst(): Boolean

  /** 关闭开关时清空历史值，后续只由 KMP 设置决定。 */
  fun clearShowCourseFirst()
}
