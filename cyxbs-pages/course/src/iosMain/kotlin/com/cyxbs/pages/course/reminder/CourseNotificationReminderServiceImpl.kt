package com.cyxbs.pages.course.reminder

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.ICourseNotificationReminderService
import com.cyxbs.pages.course.api.ILessonService2
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationSignificantTimeChangeNotification
import platform.darwin.NSObjectProtocol

/** iOS 独有的账号级提醒设置，兼容旧原生页按 key 是否存在判断开启的行为。 */
private object CourseNotificationReminderSettings {
  private const val ENABLED_KEY = "course.notification_reminder_enabled"
  private const val LEGACY_KEY = "Mine_RemindBeforeClass"

  /** 首次读取时把旧设备开关归属当前账号，后续切号不会继承旧开关。 */
  fun isEnabled(accountId: String): Boolean {
    val settings = AccountSettings.get(accountId)
    val legacy = NSUserDefaults.standardUserDefaults
    if (legacy.objectForKey(LEGACY_KEY) != null) {
      // 已存在的账号设置优先；即使旧 key 仍在，也不能把当前账号的明确关闭转移给下一账号。
      if (!settings.hasKey(ENABLED_KEY)) settings.putBoolean(ENABLED_KEY, true)
      legacy.removeObjectForKey(LEGACY_KEY)
    }
    return settings.getBoolean(ENABLED_KEY, false)
  }

  /** 按账号保存意图并移除旧全局 key，避免关闭后被历史值重新开启。 */
  fun setEnabled(accountId: String, enabled: Boolean) {
    AccountSettings.get(accountId).putBoolean(ENABLED_KEY, enabled)
    NSUserDefaults.standardUserDefaults.removeObjectForKey(LEGACY_KEY)
  }
}

/**
 * iOS CMP 课前提醒：直接观察课程服务，不依赖旧课表页面是否打开。
 *
 * 用户开启后安排课前 20 分钟的系统本地通知；前台恢复、时区/时间变化及课程更新会补充队列。
 * 原生写入不可取消，因此同步与清理串行化，并在每次写入前后复核完整账号代次。
 */
@OptIn(ExperimentalForeignApi::class)
@ImplProvider(clazz = ICourseNotificationReminderService::class)
object CourseNotificationReminderServiceImpl : ICourseNotificationReminderService {
  private val refreshRevision = MutableStateFlow(0L)
  private val notificationMutex = Mutex()
  private val lifecycleObservers = mutableListOf<NSObjectProtocol>()
  private var lifecycleJob: Job? = null
  private var authorizedSession: AccountSession? = null

  override fun isEnabled(): Boolean {
    val accountId = IAccountService::class.impl().session.value.accountId ?: return false
    return CourseNotificationReminderSettings.isEnabled(accountId)
  }

  override suspend fun requestAuthorization(): Boolean = withContext(Dispatchers.Main.immediate) {
    val accounts = IAccountService::class.impl()
    val session = accounts.session.value
    if (session.accountId == null) return@withContext false
    val granted = IosCourseNotificationStore.requestAuthorization()
    val accepted = granted && accounts.session.value === session
    authorizedSession = session.takeIf { accepted }
    accepted
  }

  override fun enable(): Boolean {
    val session = IAccountService::class.impl().session.value
    val accountId = session.accountId ?: return false
    if (authorizedSession !== session) return false
    CourseNotificationReminderSettings.setEnabled(accountId, true)
    ensureStarted()
    refreshRevision.update { it + 1 }
    return true
  }

  override fun disable() {
    val accountId = IAccountService::class.impl().session.value.accountId ?: return
    CourseNotificationReminderSettings.setEnabled(accountId, false)
    authorizedSession = null
    refreshRevision.update { it + 1 }
    clearDisabledNotificationsAsync()
  }

  override fun clearBeforeLogout() {
    disable()
  }

  /** 注册进程级生命周期监听和课程 worker，重复调用不会建立第二个观察任务。 */
  internal fun ensureStarted() {
    if (lifecycleJob?.isActive == true) return
    IosCourseNotificationStore.configureForegroundPresentation()
    if (lifecycleObservers.isEmpty()) {
      listOf(
        UIApplicationDidBecomeActiveNotification,
        UIApplicationSignificantTimeChangeNotification,
        NSSystemTimeZoneDidChangeNotification,
      ).forEach { name ->
        lifecycleObservers += NSNotificationCenter.defaultCenter.addObserverForName(
          name = name,
          `object` = null,
          queue = null,
        ) { refreshRevision.update { it + 1 } }
      }
    }
    lifecycleJob = appCoroutineScope.launch(Dispatchers.Main.immediate) {
      val accounts = IAccountService::class.impl()
      val lessons = ILessonService2::class.impl()
      var previousAccountId: String? = null
      combine(accounts.session, refreshRevision) { session, _ -> session }
        .collectLatest { session ->
          val accountId = session.accountId
          val accountChanged = previousAccountId != accountId
          previousAccountId = accountId
          val enabled = accountId != null && CourseNotificationReminderSettings.isEnabled(accountId)
          try {
            notificationMutex.withLock {
              currentCoroutineContext().ensureActive()
              if (accounts.session.value === session) {
                // 开关关闭及切号时移除已送达内容，正常刷新只替换待发队列。
                IosCourseNotificationStore.clear(includeDelivered = accountChanged || !enabled)
              }
            }
            if (accountId == null || !enabled) return@collectLatest
            if (!IosCourseNotificationStore.hasAuthorization()) return@collectLatest
            combine(
              lessons.observeLesson(accountId),
              SchoolCalendar.observeFirstMonDayNullable(),
            ) { data, firstMonday -> firstMonday to data }
              .distinctUntilChanged()
              .collectLatest { (firstMonday, data) ->
                val events = firstMonday?.let { CourseReminderEventPlanner.plan(it, data) }.orEmpty()
                try {
                  notificationMutex.withLock {
                    val writeContext = currentCoroutineContext()
                    IosCourseNotificationStore.replace(accountId, events) {
                      writeContext.ensureActive()
                      check(accounts.session.value === session) { "Course reminder account session changed" }
                      check(CourseNotificationReminderSettings.isEnabled(accountId)) { "Course reminder was disabled" }
                    }
                  }
                } catch (throwable: Throwable) {
                  if (throwable is CancellationException) throw throwable
                  println("同步 iOS 课前通知失败：${throwable.message}")
                }
              }
          } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            // 下一次课程/设置或前台变化会重新订阅，不让通知错误影响课表和应用启动。
            println("恢复 iOS 课前通知失败：${throwable.message}")
          }
        }
    }
  }

  /** 在应用作用域清理；排队期间若已重新开启或切到开启提醒的新账号，则保留最新队列。 */
  private fun clearDisabledNotificationsAsync() {
    appCoroutineScope.launch(Dispatchers.Main.immediate) {
      try {
        notificationMutex.withLock {
          if (!isEnabled()) IosCourseNotificationStore.clear()
        }
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        println("清理 iOS 课前通知失败：${throwable.message}")
      }
    }
  }
}

/** 主进程启动后恢复已有提醒设置，不在初始化时主动弹出通知授权。 */
@ImplProvider(clazz = InitialService::class, name = "CourseNotificationReminderInitialService")
object CourseNotificationReminderInitialService : InitialService {
  override fun onMainProcess() {
    CourseNotificationReminderServiceImpl.ensureStarted()
  }
}
