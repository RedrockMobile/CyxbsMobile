package com.cyxbs.pages.schedule.calendar

import com.cyxbs.pages.schedule.domain.calendar.CalendarLinkDiscoverySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 Schedule instrumentation runner 可在真实 Android 设备加载纯日历映射。
 *
 * 本测试只调用无 I/O 的 W42 mapper，不创建 Context、不请求权限、不读取 Calendar Provider、不打开 Room，
 * 也不发起网络请求；它只作为首次覆盖安装后的安全 smoke test。
 */
class AndroidManagedCalendarSnapshotDeviceTest {
  /** 缺失日历的普通值必须可在设备端无副作用地映射为同一缺失语义。 */
  @Test
  fun mapsCalendarAbsentWithoutSystemAccess() {
    val mapped = AndroidManagedCalendarSnapshot.CalendarAbsent.toCalendarLinkDiscoverySnapshot()

    assertEquals(CalendarLinkDiscoverySnapshot.CalendarAbsent, mapped)
  }
}
