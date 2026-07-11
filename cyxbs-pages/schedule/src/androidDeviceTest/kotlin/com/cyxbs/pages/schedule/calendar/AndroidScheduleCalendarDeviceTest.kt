package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.init.appApplication
import com.g985892345.provider.api.init.IKtProviderDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.cyxbs.components.navigation.AppNavCollector
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.ui.main._ScheduleMainNavEntry_KtProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机 CalendarContract 集成测试。
 *
 * 用独立测试 todoId 写入 Schedule 专用日历，并在每个用例前后清理，验证真实 Provider 中的导入、查询、
 * 修改同步、提醒和删除。该测试会短暂修改设备日历，必须通过 connectedAndroidDeviceTest 在测试设备运行。
 */
@RunWith(AndroidJUnit4::class)
class AndroidScheduleCalendarDeviceTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val today = Date.now()
  private val testTodoId = 8_888_888_001L
  private val testAccountName = "schedule-device-test"

  @Before
  fun setUp() {
    // 独立测试 APK 不会经过掌邮 Application/KtProvider 初始化，注入设备 Application 与假账号服务。
    appApplication = ApplicationProvider.getApplicationContext()
    registerTestProviders()
    assertEquals(
      PackageManager.PERMISSION_GRANTED,
      context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR),
    )
    // 上次测试若被进程终止，先移除可能残留的测试日历，再为当前用例按需重建。
    deleteTestCalendar()
  }

  @After
  fun tearDown() {
    AndroidScheduleCalendarReconciler.removeTodo(testTodoId)
    deleteTestCalendar()
  }

  /**
   * 删除真机测试创建的整个本地日历账号及其 tombstone，避免测试执行后在系统日历中残留账号。
   * 使用 sync-adapter URI 精确限定测试账号名与 LOCAL 类型，不会触碰用户的其他日历。
   */
  private fun deleteTestCalendar() {
    val uri = Calendars.CONTENT_URI.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(Calendars.ACCOUNT_NAME, testAccountName)
      .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
      .build()
    context.contentResolver.delete(
      uri,
      "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
      arrayOf(testAccountName, CalendarContract.ACCOUNT_TYPE_LOCAL),
    )
  }

  /** 首次导入后可按 CUSTOM_APP_URI 恢复映射；重复导入不会产生第二条事件。 */
  @Test
  fun import_and_reimport_are_idempotent_on_real_provider() {
    val todo = testTodo(title = "真机首次导入")

    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, todo, today))
    val first = AndroidScheduleCalendarProvider.getManagedEvents()
    val firstId = first["$testTodoId:single"]
    assertNotNull(firstId)
    assertEquals(1, countManagedEvents())

    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, todo, today))
    val secondId = AndroidScheduleCalendarProvider.getManagedEvents()["$testTodoId:single"]
    assertEquals(firstId, secondId)
    assertEquals(1, countManagedEvents())
  }

  /** 修改标题、时间和提醒后仍更新同一 eventId，并在真实 Reminders 表中替换提醒。 */
  @Test
  fun edit_updates_same_event_and_reminder() {
    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, testTodo(), today))
    val eventId = AndroidScheduleCalendarProvider.getManagedEvents().getValue("$testTodoId:single")

    val edited = testTodo(
      title = "真机修改后",
      endTime = dateTime(today.plusDays(2), 12, 30),
      remindMinutes = 30,
    )
    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, edited, today))

    val afterId = AndroidScheduleCalendarProvider.getManagedEvents().getValue("$testTodoId:single")
    assertEquals(eventId, afterId)
    assertEquals("真机修改后", queryEventTitle(eventId))
    assertEquals(30, queryReminderMinutes(eventId))
  }

  /** 关闭提醒、完成或删除 todo 后，真实 Provider 中对应事件都会被移除。 */
  @Test
  fun disable_reminder_complete_and_delete_remove_event() {
    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, testTodo(), today))
    assertTrue(
      AndroidScheduleCalendarReconciler.reconcileTodo(
        testTodoId, testTodo(remindMinutes = -1), today
      )
    )
    assertNull(AndroidScheduleCalendarProvider.getManagedEvents()["$testTodoId:single"])

    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, testTodo(), today))
    assertTrue(
      AndroidScheduleCalendarReconciler.reconcileTodo(
        testTodoId, testTodo(isDone = 1), today
      )
    )
    assertNull(AndroidScheduleCalendarProvider.getManagedEvents()["$testTodoId:single"])

    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, testTodo(), today))
    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, null, today))
    assertNull(AndroidScheduleCalendarProvider.getManagedEvents()["$testTodoId:single"])
  }

  /** 用户手动删除系统事件后，下一次 reconcile 会创建新 eventId 并恢复 deep link 映射。 */
  @Test
  fun provider_event_deleted_then_reconcile_recreates_it() {
    assertTrue(AndroidScheduleCalendarReconciler.reconcileTodo(testTodoId, testTodo(), today))
    val oldId = AndroidScheduleCalendarProvider.getManagedEvents().getValue("$testTodoId:single")
    assertTrue(AndroidScheduleCalendarProvider.delete(oldId))

    assertTrue(
      AndroidScheduleCalendarReconciler.reconcileTodo(
        testTodoId, testTodo(title = "恢复事件"), today
      )
    )
    val newId = AndroidScheduleCalendarProvider.getManagedEvents().getValue("$testTodoId:single")
    assertNotEquals(oldId, newId)
    assertEquals("恢复事件", queryEventTitle(newId))
  }

  /** 提醒偏移 0 是合法的准时提醒，真实 Provider 应保存 MINUTES=0。 */
  @Test
  fun zero_minute_reminder_is_persisted() {
    assertTrue(
      AndroidScheduleCalendarReconciler.reconcileTodo(
        testTodoId, testTodo(remindMinutes = 0), today
      )
    )
    val eventId = AndroidScheduleCalendarProvider.getManagedEvents().getValue("$testTodoId:single")
    assertEquals(0, queryReminderMinutes(eventId))
  }

  /** 为独立 instrumentation 进程幂等注册账号与 Schedule 导航收集器。 */
  private fun registerTestProviders() {
    if (IAccountService::class !in IKtProviderDelegate.ImplProviderMap) {
      IKtProviderDelegate.addImplProvider(IAccountService::class, "") {
        FakeAccountService(testAccountName)
      }
    }
    if (AppNavCollector::class !in IKtProviderDelegate.ImplProviderMap) {
      IKtProviderDelegate.addImplProvider(AppNavCollector::class, "schedule") {
        _ScheduleMainNavEntry_KtProvider
      }
    }
  }

  /** 真机测试使用的最小账号服务，学号作为 Schedule 专用日历的隔离账号名。 */
  private class FakeAccountService(stuNum: String) : IAccountService {
    override val state: StateFlow<AccountState> = MutableStateFlow(AccountState.Login(stuNum))
    override val accountCoroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
  }

  private fun testTodo(
    title: String = "真机日历测试",
    endTime: String = dateTime(today.plusDays(1), 10, 0),
    remindMinutes: Int = 10,
    isDone: Int = 0,
  ) = ScheduleEntity(
    todoId = testTodoId,
    title = title,
    endTime = endTime,
    remindMinutes = remindMinutes,
    isDone = isDone,
    lastModifyTime = 0,
  )

  /** 仅统计当前测试 todo 的事件，避免设备上其他 Schedule 数据影响断言。 */
  private fun countManagedEvents(): Int =
    AndroidScheduleCalendarProvider.getManagedEvents().keys.count {
      it.startsWith("$testTodoId:")
    }

  private fun queryEventTitle(eventId: Long): String? = context.contentResolver.query(
    Events.CONTENT_URI,
    arrayOf(Events.TITLE),
    "${Events._ID}=?",
    arrayOf(eventId.toString()),
    null,
  )?.use { if (it.moveToFirst()) it.getString(0) else null }

  private fun queryReminderMinutes(eventId: Long): Int? = context.contentResolver.query(
    Reminders.CONTENT_URI,
    arrayOf(Reminders.MINUTES),
    "${Reminders.EVENT_ID}=?",
    arrayOf(eventId.toString()),
    null,
  )?.use { if (it.moveToFirst()) it.getInt(0) else null }

  private fun dateTime(date: Date, hour: Int, minute: Int): String =
    "${date.year}年${date.monthNumber}月${date.dayOfMonth}日 " +
      "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
