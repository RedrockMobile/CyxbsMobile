package com.cyxbs.pages.schedule.data.repository.v2

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleNetworkRecoverySyncTriggerTest {

  @Test
  fun initialAvailableDoesNotRequestSync() {
    var syncCount = 0
    val trigger = ScheduleNetworkRecoverySyncTrigger(
      hasPending = { true },
      requestSync = { syncCount += 1 },
    )

    trigger.onNetworkAvailabilityChanged(true)

    assertEquals(0, syncCount)
  }

  @Test
  fun recoveryWithoutPendingDoesNotRequestSync() {
    var syncCount = 0
    val trigger = ScheduleNetworkRecoverySyncTrigger(
      hasPending = { false },
      requestSync = { syncCount += 1 },
    )

    trigger.onNetworkAvailabilityChanged(false)
    trigger.onNetworkAvailabilityChanged(true)

    assertEquals(0, syncCount)
  }

  @Test
  fun recoveryWithPendingRequestsSyncOnce() {
    var hasPending = true
    var syncCount = 0
    val trigger = ScheduleNetworkRecoverySyncTrigger(
      hasPending = { hasPending },
      requestSync = { syncCount += 1 },
    )

    trigger.onNetworkAvailabilityChanged(false)
    trigger.onNetworkAvailabilityChanged(true)
    trigger.onNetworkAvailabilityChanged(true)
    assertEquals(1, syncCount)

    hasPending = false
    trigger.onNetworkAvailabilityChanged(false)
    trigger.onNetworkAvailabilityChanged(true)
    assertEquals(1, syncCount)

    hasPending = true
    trigger.onNetworkAvailabilityChanged(false)
    trigger.onNetworkAvailabilityChanged(true)
    assertEquals(2, syncCount)
  }
}
