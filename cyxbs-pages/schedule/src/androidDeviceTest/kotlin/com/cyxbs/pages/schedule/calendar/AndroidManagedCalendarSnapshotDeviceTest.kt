package com.cyxbs.pages.schedule.calendar

import kotlin.test.Test
import kotlin.test.assertSame

/**
 * 验证 Schedule instrumentation runner 可在真实 Android 设备加载当前日历快照模型。
 *
 * 本测试不创建 Context、不请求权限、不读取 Calendar Provider、不打开 Room，也不发起网络请求；它只作为
 * 覆盖安装后的安全 smoke test。真实 Provider 的缺失日历语义由 [AndroidCalendarProviderInstrumentedTest] 验证。
 */
class AndroidManagedCalendarSnapshotDeviceTest {
  /** 缺失日历的单例值必须能通过 Android test APK 正常加载。 */
  @Test
  fun mapsCalendarAbsentWithoutSystemAccess() {
    val snapshot: AndroidManagedCalendarSnapshot = AndroidManagedCalendarSnapshot.CalendarAbsent

    assertSame(AndroidManagedCalendarSnapshot.CalendarAbsent, snapshot)
  }
}
