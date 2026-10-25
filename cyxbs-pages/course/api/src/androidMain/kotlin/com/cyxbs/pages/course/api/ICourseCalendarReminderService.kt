package com.cyxbs.pages.course.api

/**
 * Android 课表系统日历提醒能力。
 *
 * 设置页只负责请求日历权限和切换用户意图；课程事件规划、账号隔离与 CalendarProvider 写入均由实现模块负责。
 */
interface ICourseCalendarReminderService {

  /** 当前登录账号是否已开启课前系统日历提醒；权限缺失时返回 false。 */
  fun isEnabled(): Boolean

  /**
   * 为当前登录账号开启课前提醒并立即开始同步。
   *
   * @return 当前账号与日历权限均有效、开启请求已被接受时返回 true。
   */
  fun enable(): Boolean

  /**
   * 关闭当前登录账号的课前提醒，并异步删除独立的“掌邮课表”日历。
   *
   * 已写入的其他系统日历和 Schedule 模块的“掌邮日程”日历不受影响。
   */
  fun disable()

  /**
   * 退出登录前停止同步并清理当前账号的课表日历。
   *
   * 清理在应用级协程中异步执行，避免账号作用域紧接着销毁时中断 CalendarProvider 操作。
   */
  fun clearBeforeLogout()
}
