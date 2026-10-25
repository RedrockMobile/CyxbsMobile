package com.cyxbs.components.base.crash

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * 在窗口 Recomposer 构造时，为 effects 插入监督层，不修改 Compose 或协程内部字段。
 *
 * 包装只在构造阶段使用：先识别 Recomposer 加入的帧时钟，再拦截紧随其后的 Job。
 * 共享状态保证旧包装也不会再次拦截；普通任务启动时必须使用标准的上下文组合语义。
 * 仅用于按生命周期 cancel 的 Android 窗口；手动 close Recomposer 前需先 complete 监督层。
 */
internal class ComposeEffectCoroutineContext private constructor(
  private val delegate: CoroutineContext,
  private val state: HookState,
) : CoroutineContext {

  constructor(context: CoroutineContext) : this(context, HookState())

  /** 已插入的监督层；工厂可用它确认当前 Compose 版本仍采用预期的构造顺序。 */
  val supervisor: CompletableJob?
    get() = state.supervisor

  override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = delegate[key]

  override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R =
    delegate.fold(initial, operation)

  override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = delegate.minusKey(key)

  /**
   * 保留构造阶段的包装，直到 effectJob 加入；之后返回普通上下文，避免干涉协程自身的 Job。
   * 监督层仍以原 effectJob 为父级，因此窗口销毁时原有取消与完成回调继续生效。
   */
  override fun plus(context: CoroutineContext): CoroutineContext {
    val combined = delegate + context
    if (state.supervisor != null) return combined
    if (context is Job && state.awaitingEffectJob) {
      val supervisor = SupervisorJob(context)
      state.supervisor = supervisor
      return combined + supervisor
    }
    // Recomposer 按「原上下文 + broadcastFrameClock + effectJob」的顺序构造 effects 上下文，
    // 因此只把紧接 BroadcastFrameClock 加入的 Job 识别为 effectJob，避免误拦截普通 Job。
    // 用赋值保证中间若加入其他元素便清除标记；窗口的 PausableMonotonicFrameClock 不会触发它。
    // 此判断依赖 Compose 的构造顺序，升级版本时需核对源码并运行对应回归测试。
    state.awaitingEffectJob = context is BroadcastFrameClock
    return ComposeEffectCoroutineContext(combined, state)
  }

  /** 每个窗口独享的构造状态；只由创建该窗口的 UI 线程访问。 */
  private class HookState {
    var awaitingEffectJob = false
    var supervisor: CompletableJob? = null
  }
}
