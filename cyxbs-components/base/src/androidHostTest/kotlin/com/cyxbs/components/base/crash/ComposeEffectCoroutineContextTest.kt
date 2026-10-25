package com.cyxbs.components.base.crash

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 用真实 Recomposer 和 Composition 验证监督层，而非只验证上下文替换结果。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeEffectCoroutineContextTest {

  /** effect 失败只结束自身，兄弟任务与之后的状态重组仍能继续。 */
  @Test
  fun failedEffectDoesNotStopSiblingOrRecomposition() = runTest {
    val errors = mutableListOf<Throwable>()
    val frameClock = BroadcastFrameClock()
    val handler = CoroutineExceptionHandler { _, error -> errors.add(error) }
    val context = ComposeEffectCoroutineContext(
      StandardTestDispatcher(testScheduler) + frameClock + handler
    )
    // 模拟窗口工厂先加入暂停帧时钟的组合，确保包装不会在到达 Recomposer 前丢失。
    val recomposer = Recomposer(context + PausableMonotonicFrameClock(frameClock))
    val supervisor = assertNotNull(context.supervisor)
    val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
    val composition = Composition(EmptyApplier(), recomposer)
    val value = mutableStateOf(0)
    val failure = IllegalStateException("测试 effect 失败")
    var siblingStarted = false
    var siblingCancelled = false
    var renderedValue = -1
    try {
      composition.setContent {
        val current = value.value
        SideEffect { renderedValue = current }
        LaunchedEffect(Unit) { throw failure }
        LaunchedEffect(Unit) {
          siblingStarted = true
          try {
            awaitCancellation()
          } finally {
            siblingCancelled = true
          }
        }
      }
      runCurrent()
      assertEquals(1, errors.size)
      assertSame(failure, errors.single())
      assertTrue(supervisor.isActive)
      assertTrue(siblingStarted)
      assertFalse(siblingCancelled)

      value.value = 1
      Snapshot.sendApplyNotifications()
      runCurrent()
      frameClock.sendFrame(1L)
      runCurrent()
      assertEquals(1, renderedValue)
      assertTrue(runner.isActive)

      // 默认 Android 窗口退出走 cancel，原 effectJob 必须继续向下取消监督层及兄弟任务。
      recomposer.cancel()
      runCurrent()
      assertTrue(supervisor.isCancelled)
      assertTrue(siblingCancelled)
      recomposer.join()
    } finally {
      composition.dispose()
      recomposer.cancel()
      runner.cancel()
    }
  }

  /** 完成拦截后不能替换普通 launch 加入的 Job，也不能通过旧包装再次插入监督层。 */
  @Test
  fun hookIsOneShotAndDoesNotReplaceLaterJobs() = runTest {
    val context = ComposeEffectCoroutineContext(StandardTestDispatcher(testScheduler))
    val recomposer = Recomposer(context)
    val supervisor = assertNotNull(context.supervisor)
    val taskJob = Job()
    try {
      assertSame(taskJob, (recomposer.effectCoroutineContext + taskJob)[Job])
      assertSame(taskJob, (context + taskJob)[Job])
      assertSame(supervisor, context.supervisor)
    } finally {
      taskJob.cancel()
      recomposer.cancel()
    }
  }

  /** 按窗口工厂的构造顺序验证：旧上下文、runner scope 和 runner 任务均不能误入监督层。 */
  @Test
  fun windowRunnerKeepsItsOwnJobHierarchy() = runTest {
    val frameClock = BroadcastFrameClock()
    val context = ComposeEffectCoroutineContext(
      StandardTestDispatcher(testScheduler) + frameClock
    )
    val windowContext = context + PausableMonotonicFrameClock(frameClock)
    val recomposer = Recomposer(windowContext)
    val supervisor = assertNotNull(context.supervisor)
    // Recomposer 构造只生成新的 effects 上下文，不能把 Job 写回工厂保存的旧上下文。
    assertNull(windowContext[Job])
    val runRecomposeScope = CoroutineScope(windowContext)
    val scopeJob = assertNotNull(runRecomposeScope.coroutineContext[Job])
    var runningJob: Job? = null
    val runner = runRecomposeScope.launch(start = CoroutineStart.UNDISPATCHED) {
      runningJob = coroutineContext[Job]
      recomposer.runRecomposeAndApplyChanges()
    }
    try {
      assertNotSame(supervisor, scopeJob)
      assertNull(scopeJob.parent)
      assertSame(runner, runningJob)
      assertSame(scopeJob, runner.parent)
      assertFalse(supervisor.children.any { it === scopeJob || it === runner })
      assertSame(supervisor, context.supervisor)

      // 窗口退出仍由 Recomposer 取消正在运行的重组任务，不依赖把 runner 挂到 effects 下。
      recomposer.cancel()
      runCurrent()
      runner.join()
      recomposer.join()
      assertTrue(runner.isCompleted)
      assertTrue(supervisor.isCancelled)
    } finally {
      recomposer.cancel()
      scopeJob.cancel()
    }
  }

  /** 支持显式正常关闭时必须先 complete 新增监督层，不能等待父任务完成后再清理。 */
  @Test
  fun explicitCompletionAllowsRecomposerToClose() = runTest {
    val context = ComposeEffectCoroutineContext(StandardTestDispatcher(testScheduler))
    val recomposer = Recomposer(context)
    assertNotNull(context.supervisor).complete()
    recomposer.close()
    recomposer.join()
    assertEquals(Recomposer.State.ShutDown, recomposer.currentState.value)
  }

  /** 无 UI 节点的测试 Applier，允许 Composition 运行 effects 和重组而不依赖 Android 窗口。 */
  private class EmptyApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
  }
}
