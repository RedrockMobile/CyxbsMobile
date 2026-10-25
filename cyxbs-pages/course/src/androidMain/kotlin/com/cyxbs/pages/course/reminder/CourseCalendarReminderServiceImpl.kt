package com.cyxbs.pages.course.reminder

import android.util.Log
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.init.appContext
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.course.api.ICourseCalendarReminderService
import com.cyxbs.pages.course.api.ILessonService2
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 课前提醒的账号级用户设置。 */
private object CourseCalendarReminderSettings {
  private const val ENABLED_KEY = "course.calendar_reminder_enabled"

  /** 只有用户明确开启后才允许向独立课表日历写入事件。 */
  fun isEnabled(accountId: String): Boolean =
    AccountSettings.get(accountId).getBoolean(ENABLED_KEY, false)

  /** 按学号隔离保存用户意图，切换账号不会继承上一账号的开关。 */
  fun setEnabled(accountId: String, enabled: Boolean) {
    AccountSettings.get(accountId).putBoolean(ENABLED_KEY, enabled)
  }
}

/**
 * Android 课表系统日历提醒实现。
 *
 * 单一生命周期 worker 同时观察账号代次、课程缓存与开学日期；任一输入变化都会全量刷新独立“掌邮课表”日历。
 */
@ImplProvider(clazz = ICourseCalendarReminderService::class)
object CourseCalendarReminderServiceImpl : ICourseCalendarReminderService {

  private val refreshRevision = MutableStateFlow(0L)
  private var lifecycleJob: Job? = null
  // CalendarProvider 调用本身不可取消，统一串行化同步与清理，避免快速关开开关时互相覆盖。
  private val calendarWriteMutex = Mutex()

  override fun isEnabled(): Boolean {
    val accountId = IAccountService::class.impl().session.value.accountId ?: return false
    return CourseCalendarReminderSettings.isEnabled(accountId) &&
      AndroidCourseCalendarStore.hasCalendarPermissions(appContext)
  }

  override fun enable(): Boolean {
    val accountService = IAccountService::class.impl()
    val session = accountService.session.value
    val accountId = session.accountId ?: return false
    if (!AndroidCourseCalendarStore.hasCalendarPermissions(appContext)) return false
    ensureStarted()
    CourseCalendarReminderSettings.setEnabled(accountId, true)
    refreshRevision.update { it + 1 }
    return true
  }

  override fun disable() {
    val accountId = IAccountService::class.impl().session.value.accountId ?: return
    CourseCalendarReminderSettings.setEnabled(accountId, false)
    refreshRevision.update { it + 1 }
    deleteCalendarAsync(accountId)
  }

  override fun clearBeforeLogout() {
    val accountId = IAccountService::class.impl().session.value.accountId ?: return
    CourseCalendarReminderSettings.setEnabled(accountId, false)
    refreshRevision.update { it + 1 }
    deleteCalendarAsync(accountId)
  }

  /**
   * 启动进程级观察任务。
   *
   * 重复调用是幂等的；账号切换或关闭开关会由 collectLatest 取消旧课程流，后续 Provider 写入还会再次复核代次。
   */
  internal fun ensureStarted() {
    if (lifecycleJob?.isActive == true) return
    lifecycleJob = appCoroutineScope.launch {
      val accountService = IAccountService::class.impl()
      val lessonService = ILessonService2::class.impl()
      var previousAccountId: String? = null
      combine(accountService.session, refreshRevision) { session, _ -> session }
        .collectLatest { session ->
          // 切换或退出账号时也清理旧投影，不仅依赖设置页的退出入口。
          previousAccountId?.takeIf { it != session.accountId }?.let(::deleteCalendarAsync)
          previousAccountId = session.accountId
          val accountId = session.accountId ?: return@collectLatest
          if (!CourseCalendarReminderSettings.isEnabled(accountId)) return@collectLatest
          if (!AndroidCourseCalendarStore.hasCalendarPermissions(appContext)) return@collectLatest

          combine(
            lessonService.observeLesson(accountId),
            SchoolCalendar.observeFirstMonDayNullable().filterNotNull(),
          ) { lessons, firstMonday ->
            firstMonday to lessons
          }.distinctUntilChanged().collectLatest { (firstMonday, lessons) ->
            val events = CourseReminderEventPlanner.plan(firstMonday, lessons)
            runCatchingCoroutine {
              withContext(Dispatchers.IO) {
                calendarWriteMutex.withLock {
                  val writeContext = currentCoroutineContext()
                  AndroidCourseCalendarStore(appContext).replaceEvents(
                    accountId = accountId,
                    events = events,
                    ensureAuthorized = {
                      // 每个批次前检查 collectLatest 的取消，不能继续写入已过期的课程快照。
                      writeContext.ensureActive()
                      check(accountService.session.value === session) {
                        "Course reminder account session changed"
                      }
                      check(CourseCalendarReminderSettings.isEnabled(accountId)) {
                        "Course reminder was disabled"
                      }
                      check(AndroidCourseCalendarStore.hasCalendarPermissions(appContext)) {
                        "Calendar permissions are unavailable"
                      }
                    },
                  )
                }
              }
            }.onFailure { throwable ->
              Log.e(TAG, "同步课表系统日历失败", throwable)
            }
          }
        }
    }
  }

  /** 清理使用冻结学号，不依赖即将被注销的账号协程作用域。 */
  private fun deleteCalendarAsync(accountId: String) {
    appCoroutineScope.launch(Dispatchers.IO) {
      runCatchingCoroutine {
        calendarWriteMutex.withLock {
          // 清理排队期间可能已重新开启；此时保留新投影，由最新同步任务替换内容。
          val currentAccountId = IAccountService::class.impl().session.value.accountId
          if (currentAccountId != accountId || !CourseCalendarReminderSettings.isEnabled(accountId)) {
            AndroidCourseCalendarStore(appContext).deleteManagedCalendar(accountId)
          }
        }
      }.onFailure { throwable ->
        Log.e(TAG, "清理课表系统日历失败", throwable)
      }
    }
  }

  private const val TAG = "CourseCalendarReminder"
}

/** 应用主进程启动后恢复已开启账号的课表日历观察任务；不会自行申请系统权限。 */
@ImplProvider(clazz = InitialService::class, name = "CourseCalendarReminderInitialService")
object CourseCalendarReminderInitialService : InitialService {
  override fun onMainProcess() {
    CourseCalendarReminderServiceImpl.ensureStarted()
  }
}
