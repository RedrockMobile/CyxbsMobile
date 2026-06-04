package com.cyxbs.pages.mine.user

import kotlinx.coroutines.flow.StateFlow

/**
 * 「我的」主页平台相关能力（commonMain 声明，androidMain 实现）。
 *
 * 这些能力依赖仅在 androidMain / mobileMain 可见的服务或 Activity 跳转，
 * 无法直接在 commonMain 调用，故下放到平台层：
 * - 未读消息数 [unreadCount] 来自 mobileMain 的 INotificationService
 * - [launchNotification] 来自 androidMain 的 ILaunchNotificationService
 * - 其余 jumpXxx 为 Android Activity / 路由跳转
 *
 * 业务侧通过 `MineNavPlatform::class.implOrNull()` 获取，其它平台暂无实现时优雅降级。
 * 迁移完所有二级页面为 cmp 后可逐步删除本接口。
 */
interface MineNavPlatform {

  /** 未读消息数，用于消息中心红点 */
  val unreadCount: StateFlow<Int>

  /** 进入消息中心 */
  fun launchNotification()

  /** 邮票中心 */
  fun jumpStore()

  /** 反馈中心 */
  fun jumpFeedbackCenter()

  /** 签到页 */
  fun jumpSign()

  /** 设置页 */
  fun jumpSetting()

  /** 活动中心 */
  fun jumpActivityCenter()
}
