package com.cyxbs.components.base.webview

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopApplicationExitTest {
  @Test
  fun synchronousShutdownFailureAllowsRetryWithoutReentrantOrDuplicateShutdown() = runTest {
    val failures = mutableListOf<Throwable>()
    val scope = CoroutineScope(
      SupervisorJob() + StandardTestDispatcher(testScheduler) +
        CoroutineExceptionHandler { _, failure -> failures += failure },
    )
    var checks = 0
    var attempts = 0
    var successfulShutdowns = 0
    lateinit var coordinator: DesktopApplicationExitCoordinator
    coordinator = DesktopApplicationExitCoordinator(scope, shutdown = {
      attempts++
      coordinator.requestExit()
      if (attempts == 1) error("shutdown failed synchronously")
      successfulShutdowns++
    })
    coordinator.registerInterceptor { checks++; true }

    try {
      coordinator.requestExit()
      testScheduler.advanceUntilIdle()
      assertEquals(1, checks)
      assertEquals(1, attempts)
      assertEquals(0, successfulShutdowns)

      coordinator.requestExit()
      testScheduler.advanceUntilIdle()
      repeat(10) { coordinator.requestExit() }
      testScheduler.advanceUntilIdle()
      assertEquals(2, checks)
      assertEquals(2, attempts)
      assertEquals(1, successfulShutdowns)
      assertEquals(emptyList(), failures)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun defaultTimeoutIsThirtySeconds() = runTest {
    var shutdowns = 0
    val coordinator = DesktopApplicationExitCoordinator(this, shutdown = { shutdowns++ })
    coordinator.registerInterceptor { delay(30_001); true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()

    assertEquals(30_000L, testScheduler.currentTime)
    assertEquals(0, shutdowns)
  }

  @Test
  fun cancelledScopeBeforeDispatchDoesNotCheckOrReleaseResources() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
    var checks = 0
    var shutdowns = 0
    val coordinator = DesktopApplicationExitCoordinator(scope, shutdown = { shutdowns++ })
    coordinator.registerInterceptor { checks++; true }
    job.cancel()

    repeat(2) { coordinator.requestExit() }
    testScheduler.advanceUntilIdle()

    assertEquals(0, checks)
    assertEquals(0, shutdowns)
  }

  @Test
  fun cancellingScopeDuringCheckingPreservesResources() = runTest {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
    var checks = 0
    var shutdowns = 0
    val coordinator = DesktopApplicationExitCoordinator(scope, shutdown = { shutdowns++ })
    coordinator.registerInterceptor { checks++; awaitCancellation() }

    coordinator.requestExit()
    testScheduler.runCurrent()
    scope.cancel()
    testScheduler.advanceUntilIdle()

    assertEquals(1, checks)
    assertEquals(0, shutdowns)
  }

  @Test
  fun interceptorCancellationPreservesResourcesAndAllowsRetry() = runTest {
    var cancel = true
    var followingChecks = 0
    var shutdowns = 0
    val coordinator = DesktopApplicationExitCoordinator(this, shutdown = { shutdowns++ })
    coordinator.registerInterceptor {
      if (cancel) throw kotlinx.coroutines.CancellationException("business cancelled")
      true
    }
    coordinator.registerInterceptor { followingChecks++; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(0, followingChecks)
    assertEquals(0, shutdowns)

    cancel = false
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(1, followingChecks)
    assertEquals(1, shutdowns)
  }

  @Test
  fun macTerminationSchedulesTheSharedExitRequestAndDefersNativeTermination() {
    val scheduled = mutableListOf<() -> Unit>()
    var requests = 0
    val handler = createDesktopApplicationTerminationHandler(
      operatingSystemName = "Mac OS X",
      scheduleOnEdt = { scheduled += it },
      requestExit = { requests++ },
    )

    assertEquals(true, handler.onBeforeTerminate())
    assertEquals(1, scheduled.size)
    assertEquals(0, requests)
    scheduled.single().invoke()
    assertEquals(1, requests)
  }

  @Test
  fun nonMacTerminationDoesNotScheduleAnExitRequest() {
    var scheduled = 0
    var requests = 0
    val handler = createDesktopApplicationTerminationHandler(
      operatingSystemName = "Linux",
      scheduleOnEdt = { scheduled++ },
      requestExit = { requests++ },
    )

    assertEquals(false, handler.onBeforeTerminate())
    assertEquals(0, scheduled)
    assertEquals(0, requests)
  }

  @Test
  fun windowAndCmdQRequestsMergeWhileChecking() = runTest {
    val decision = CompletableDeferred<Boolean>()
    var checks = 0
    var shutdowns = 0
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { shutdowns++ }
    coordinator.registerInterceptor { checks++; decision.await() }
    val windowClose = coordinator::requestExit
    val cmdQ = coordinator::requestExit

    windowClose()
    testScheduler.runCurrent()
    repeat(10) { cmdQ(); windowClose() }
    testScheduler.runCurrent()
    assertEquals(1, checks)
    assertEquals(0, shutdowns)

    decision.complete(true)
    testScheduler.advanceUntilIdle()
    assertEquals(1, shutdowns)
  }

  @Test
  fun requestsAfterClosingDoNotCheckOrShutdownAgain() = runTest {
    var checks = 0
    var shutdowns = 0
    lateinit var coordinator: DesktopApplicationExitCoordinator
    coordinator = DesktopApplicationExitCoordinator(this, 30_000) {
      shutdowns++
      coordinator.requestExit()
    }
    coordinator.registerInterceptor { checks++; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    repeat(10) { coordinator.requestExit() }
    testScheduler.advanceUntilIdle()

    assertEquals(1, checks)
    assertEquals(1, shutdowns)
  }

  @Test
  fun concurrentRequestsMergeBeforeTheCheckIsDispatched() = runTest {
    var checks = 0
    var shutdowns = 0
    val start = CompletableDeferred<Unit>()
    val decision = CompletableDeferred<Boolean>()
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { shutdowns++ }
    coordinator.registerInterceptor { checks++; decision.await() }

    withContext(Dispatchers.Default) {
      coroutineScope {
        repeat(64) { launch { start.await(); coordinator.requestExit() } }
        start.complete(Unit)
      }
    }
    testScheduler.runCurrent()
    assertEquals(1, checks)
    assertEquals(0, shutdowns)
    decision.complete(true)
    testScheduler.advanceUntilIdle()

    assertEquals(1, checks)
    assertEquals(1, shutdowns)
  }

  @Test
  fun registrationsAndUnregistrationsOnlyChangeTheNextSnapshot() = runTest {
    val events = mutableListOf<String>()
    val decision = CompletableDeferred<Boolean>()
    var allowExit = false
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { events += "shutdown" }
    coordinator.registerInterceptor { decision.await() }
    val interceptor: suspend () -> Boolean = { events += "registered"; true }
    val first = coordinator.registerInterceptor(interceptor)
    coordinator.registerInterceptor(interceptor)
    first.close()
    first.close()
    coordinator.registerInterceptor { events += "last"; allowExit }

    coordinator.requestExit()
    // 仍未调度检查：requestExit 已经取好本轮快照。
    val newest = coordinator.registerInterceptor { events += "new"; true }
    newest.close()
    val old = coordinator.registerInterceptor { events += "next"; true }
    testScheduler.runCurrent()
    old.close()
    coordinator.registerInterceptor { events += "future"; true }
    decision.complete(true)
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("registered", "last"), events)

    allowExit = true
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("registered", "last", "registered", "last", "future", "shutdown"), events)
  }

  @Test
  fun unregisteringAfterRequestDoesNotRemoveTheCurrentSnapshotEntry() = runTest {
    val events = mutableListOf<String>()
    var approved = false
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { events += "shutdown" }
    val old = coordinator.registerInterceptor { events += "old"; true }
    coordinator.registerInterceptor { approved }

    coordinator.requestExit()
    old.close()
    coordinator.registerInterceptor { events += "new"; true }
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("old"), events)

    approved = true
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("old", "new", "shutdown"), events)
  }

  @Test
  fun exceptionPreservesResourcesAndAllowsRetry() = runTest {
    val events = mutableListOf<String>()
    var fail = true
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { events += "shutdown" }
    coordinator.registerInterceptor {
      events += "gate"
      if (fail) error("business check failed")
      true
    }
    coordinator.registerInterceptor { events += "following"; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("gate"), events)

    fail = false
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("gate", "gate", "following", "shutdown"), events)
  }

  @Test
  fun timeoutPreservesResourcesAndAllowsRetry() = runTest {
    val events = mutableListOf<String>()
    var wait = true
    val coordinator = DesktopApplicationExitCoordinator(this, 100) { events += "shutdown" }
    coordinator.registerInterceptor {
      events += "gate"
      if (wait) delay(101)
      true
    }
    coordinator.registerInterceptor { events += "following"; true }

    coordinator.requestExit()
    testScheduler.runCurrent()
    testScheduler.advanceTimeBy(100)
    testScheduler.runCurrent()
    assertEquals(listOf("gate"), events)

    wait = false
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("gate", "gate", "following", "shutdown"), events)
  }

  @Test
  fun timeoutIsOneBudgetForTheEntireSnapshot() = runTest {
    val events = mutableListOf<String>()
    val coordinator = DesktopApplicationExitCoordinator(this, 100) { events += "shutdown" }
    coordinator.registerInterceptor { delay(60); events += "first"; true }
    coordinator.registerInterceptor { delay(60); events += "second"; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("first"), events)
    assertEquals(100L, testScheduler.currentTime)
  }

  @Test
  fun vetoStopsTheChainAndAllowsRetry() = runTest {
    val events = mutableListOf<String>()
    var approved = false
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { events += "shutdown" }
    coordinator.registerInterceptor { events += "gate"; approved }
    coordinator.registerInterceptor { events += "following"; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("gate"), events)

    approved = true
    coordinator.requestExit()
    testScheduler.advanceUntilIdle()
    assertEquals(listOf("gate", "gate", "following", "shutdown"), events)
  }

  @Test
  fun approvedInterceptorsRunInRegistrationOrderBeforeShutdown() = runTest {
    val events = mutableListOf<String>()
    val coordinator = DesktopApplicationExitCoordinator(this, 30_000) { events += "shutdown" }
    coordinator.registerInterceptor { events += "first"; true }
    coordinator.registerInterceptor { events += "second"; true }

    coordinator.requestExit()
    testScheduler.advanceUntilIdle()

    assertEquals(listOf("first", "second", "shutdown"), events)
  }
}
