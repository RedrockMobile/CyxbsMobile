package com.cyxbs.pages.course.reminder

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.NSError
import platform.Foundation.localTimeZone
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 封装 iOS 通知中心，只管理独立前缀及旧课前提醒 ID，不触碰其他业务请求。
 *
 * 所有清理和写入由上层持有同一把 Mutex 后调用，确保账号切换和快速关开时不会互相覆盖。
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosCourseNotificationStore {
  private const val PREFIX = "cyxbs.course-reminder."
  private const val LEGACY_ID = "remindBeforeCourseBegin"
  private val center get() = UNUserNotificationCenter.currentNotificationCenter()
  // UNUserNotificationCenter 弱持有 delegate，必须由进程级对象保留实例。
  private val foregroundDelegate = CourseReminderForegroundDelegate()

  /** 没有原有通知代理时补充本功能的前台提醒，不替换其他模块已经安装的代理。 */
  fun configureForegroundPresentation() {
    if (center.delegate == null) center.delegate = foregroundDelegate
  }

  /** 查询现有权限，不显示系统授权弹窗，供启动和回到前台时恢复队列。 */
  suspend fun hasAuthorization(): Boolean = suspendCancellableCoroutine { continuation ->
    center.getNotificationSettingsWithCompletionHandler { settings ->
      val status = settings?.authorizationStatus
      if (continuation.isActive) {
        continuation.resume(
          status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
        )
      }
    }
  }

  /** 只有用户点击开启时请求横幅和声音；原生回调可能不在主线程，由协程恢复调用上下文。 */
  suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { continuation ->
    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) {
        granted, error ->
      if (continuation.isActive) continuation.resume(granted && error == null)
    }
  }

  /**
   * 用最新课程快照替换本功能的请求，保留其他业务并为其预留队列容量。
   *
   * [ensureAuthorized] 在每次原生写入前后复核账号代次、设置和协程取消，不能仅检查学号。
   */
  suspend fun replace(
    accountId: String,
    events: List<CourseReminderEvent>,
    ensureAuthorized: () -> Unit,
  ) {
    val pending = pendingRequests()
    ensureAuthorized()
    val reminders = CourseNotificationReminderPlanner.plan(
      events = events,
      now = com.cyxbs.components.config.time.MinuteTimeDate.now(),
      otherPendingCount = pending.count { !isManaged(it.identifier) },
    )
    val identifiers = reminders.map { "$PREFIX$accountId|${it.event.stableId}" }
    // 先移除已不存在的课程，释放槽位；相同 ID 的 add 会直接更新已有请求。
    center.removePendingNotificationRequestsWithIdentifiers(
      pending.map { it.identifier }.filter { isManaged(it) && it !in identifiers }
    )
    reminders.zip(identifiers).forEach { (reminder, identifier) ->
      ensureAuthorized()
      val content = UNMutableNotificationContent().apply {
        setTitle("还有20分钟上课")
        setSubtitle(reminder.event.title)
        setBody(listOf(
          reminder.event.location.takeIf(String::isNotBlank)?.let { "教室：$it" },
          reminder.event.description.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString("\n"))
        setSound(UNNotificationSound.defaultSound())
      }
      val date = reminder.notifyAt
      val components = NSDateComponents().apply {
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
        timeZone = NSTimeZone.localTimeZone
        year = date.date.year.toLong()
        month = date.date.monthNumber.toLong()
        day = date.date.dayOfMonth.toLong()
        hour = date.time.hour.toLong()
        minute = date.time.minute.toLong()
        second = 0
      }
      val request = UNNotificationRequest.requestWithIdentifier(
        identifier,
        content,
        UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false),
      )
      // 原生 add 不可撤销，必须等其回调后才释放写锁；否则关闭开关可能先清理、后被迟到写入覆盖。
      suspendCoroutine<Unit> { continuation ->
        center.addNotificationRequest(request) { error: NSError? ->
          if (error == null) continuation.resume(Unit)
          else continuation.resumeWithException(IllegalStateException(error.localizedDescription))
        }
      }
      currentCoroutineContext().ensureActive()
      ensureAuthorized()
    }
  }

  /** 清理本功能的账号投影；[includeDelivered] 决定是否同时移除通知中心里已送达的提醒。 */
  suspend fun clear(includeDelivered: Boolean = true) {
    center.removePendingNotificationRequestsWithIdentifiers(
      pendingRequests().map { it.identifier }.filter(::isManaged) + LEGACY_ID
    )
    if (!includeDelivered) return
    val delivered = suspendCancellableCoroutine<List<UNNotification>> { continuation ->
      center.getDeliveredNotificationsWithCompletionHandler { notifications ->
        if (continuation.isActive) continuation.resume(notifications.orEmpty().filterIsInstance<UNNotification>())
      }
    }
    center.removeDeliveredNotificationsWithIdentifiers(
      delivered.map { it.request.identifier }.filter(::isManaged)
    )
  }

  /** 读取待发请求以便区分本功能与其他业务，并计算共享通知队列的剩余容量。 */
  private suspend fun pendingRequests(): List<UNNotificationRequest> = suspendCancellableCoroutine { continuation ->
    center.getPendingNotificationRequestsWithCompletionHandler { requests ->
      if (continuation.isActive) continuation.resume(requests.orEmpty().filterIsInstance<UNNotificationRequest>())
    }
  }

  /** 固定命名空间避免清理课前提醒时误删日程或其他业务的本地通知。 */
  private fun isManaged(identifier: String): Boolean = identifier.startsWith(PREFIX) || identifier == LEGACY_ID

  /** 只让课前通知在前台显示横幅及声音，其他通知维持没有代理时的默认行为。 */
  private class CourseReminderForegroundDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
      center: UNUserNotificationCenter,
      willPresentNotification: UNNotification,
      withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
      withCompletionHandler(
        if (isManaged(willPresentNotification.request.identifier)) {
          UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound
        } else {
          0uL
        }
      )
    }
  }
}
