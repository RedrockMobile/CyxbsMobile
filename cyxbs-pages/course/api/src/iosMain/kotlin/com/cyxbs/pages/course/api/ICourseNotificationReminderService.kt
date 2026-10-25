package com.cyxbs.pages.course.api

/**
 * iOS 课前系统本地通知能力，与 Android 的系统日历写入相互独立。
 *
 * 设置页负责触发授权并切换开关；实现负责课程观察、通知队列和账号隔离。
 * 同步方法由主线程调用，授权方法可挂起且不会自行开启开关。
 */
interface ICourseNotificationReminderService {

  /** 当前登录账号是否希望开启提醒；系统权限仍在安排通知前单独检查。 */
  fun isEnabled(): Boolean

  /** 请求通知的横幅和声音权限；拒绝或请求期间账号切换时返回 false。 */
  suspend fun requestAuthorization(): Boolean

  /** 授权已通过时开启当前账号的提醒，并异步安排最近的课程。 */
  fun enable(): Boolean

  /** 关闭当前账号的提醒，异步清除本功能的待发及已送达通知。 */
  fun disable()

  /** 退出登录前关闭提醒；清理在应用级作用域内执行，不影响其他业务通知。 */
  fun clearBeforeLogout()
}
