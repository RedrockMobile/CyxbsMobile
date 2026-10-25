package com.cyxbs.components.base.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopApplicationExitHandoffTest {
  @Test
  fun exitBeforeCompositionIsDeliveredWhenCallbackBinds() {
    val handoff = DesktopApplicationExitHandoff()
    var exits = 0

    handoff.onApplicationExit()
    assertEquals(0, exits)

    handoff.bind { exits++ }

    assertEquals(1, exits)
  }

  @Test
  fun latestHotReloadCallbackReceivesPendingExit() {
    val handoff = DesktopApplicationExitHandoff()
    val exits = mutableListOf<String>()

    handoff.bind { exits += "old" }
    handoff.bind { exits += "latest" }
    handoff.onApplicationExit()

    assertEquals(listOf("latest"), exits)
  }

  @Test
  fun repeatedExitSignalsDeliverExactlyOnce() {
    val handoff = DesktopApplicationExitHandoff()
    var exits = 0
    handoff.bind { exits++ }

    repeat(3) { handoff.onApplicationExit() }

    assertEquals(1, exits)
  }

  @Test
  fun callbackFailureIsAtMostOnceAndDoesNotRedeliverOnRebind() {
    val handoff = DesktopApplicationExitHandoff()
    var attempts = 0

    handoff.bind {
      attempts++
      error("exit callback failed")
    }

    assertFailsWith<IllegalStateException> { handoff.onApplicationExit() }
    handoff.bind { attempts++ }

    assertEquals(1, attempts)
  }
}
