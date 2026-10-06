package com.cyxbs.components.utils.extensions

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/30 10:19
 */

/**
 * 如果你要看网络请求相关的示例代码，请移步 network/ApiFlow.kt
 *
 * 个人观点：
 *
 * 1、不是很建议大家大量使用 Flow，因为 Flow 目前不是很成熟，有很多 api 还处于实验阶段，而且对于复杂的流水处理比不上 Rxjava
 *
 * 2、目前 Flow 比 Rxjava 好的一点在于可以与生命周期相配合
 *
 * 3、更推荐在 Repository 使用 Rxjava，
 * - Room 层推荐 Rxjava (Single、Observable、Maybe)
 * - 网络层推荐使用 Rxjava 的 Single，(不知道为什么以前的学长很喜欢用 Observable)
 * - ViewModel 层可以 Flow 和 Rxjava 混用，(但 Rxjava 一定要注意及时关闭流，可以使用 BaseViewModel中的 safeSubscribeBy() 方法)
 *       但更推荐使用 Rxjava，Flow 因为是新产物，还不是很稳定
 * - Activity 层更推荐使用 Flow，因为一般不会涉及到很复杂的流处理
 *
 * 4、顺便说一下数据流动的原则（个人观点）：
 * - 推荐 Room 使用 Observable 来观察本地数据，百度：Room 响应式编程
 * - Room 需要的实体类强烈不建议直接使用网络请求的 Bean 类，因为这样以后不好维护
 * - Repository 层用于转换 网络请求的 Bean 数据为 Room 的实体类，然后统一暴露 Room 实体类给 ViewModel
 *       而不是暴露 网络请求的 Bean 类，做到 VM 层与网络层的分离，
 *       如果允许的话，可以考虑 VM 层再装换一遍数据为 XXXData 类给 View 层
 * - 如果网络请求数据简单，不需要本地化，可以省去 Repository 层，掌邮里面就基本没有 Repository 层
 *
 * 5、请小心使用 flowWithLifecycle()、lifecycle.repeatOnLifecycle()，他们会导致数据倒灌，
 *       不适用于绝大部分场景！！！（别看到很多博客推荐就直接拿来用！！！）
 */



/**
 * 使用优雅的 DSL 来拦截异常
 *
 * # 如果你是想看网络请求的用法，请移步于 network/ApiFlow.kt
 *
 * ## 注意
 * - 使用该方法后，下游将不会收到任何异常，即异常在此步被全部拦截，除非你手动调用 throw 或 onError() 再次抛出异常
 * - 网络请求中请使用 mapOrInterceptException 或 throwOrInterceptException 方法
 * - 更推荐使用 Rxjava，目前 Flow 很多 api 还不是很稳定，并且 Rxjava 中也有这个同名方法
 * ```
 *   .interceptException {
 *
 *       toast("捕获全部异常") // 直接写的话是处理全部异常
 *
 *       emitter.emit(...)   // 手动发送新的值给下游，Rxjava 使用 onSuccess() 或者 onNext() (注：这个需要手动调用 onComplete())
 *
 *       RuntimeException::class.catch {
 *           // 捕获 RuntimeException
 *
 *           val exception = it // 闭包中 it 为当前异常
 *           val emitter = this // 闭包中 this 为 发射器，可用于发送值给下游
 *       }
 *
 *       NullPointerException::class.catch {
 *           // 捕获 NullPointerException
 *       }
 *
 *       IndexOutOfBoundsException::class.catchOther { // 使用 catchOther 捕获除自身以外的其他异常
 *           throw it // 你甚至可以把异常再次抛给下游（这里使用 catchOther 取了一个反，就会把其他异常抛到下游去）
 *       }
 *
 *       // 这个闭包内按从上到下的顺序执行，每个 ::class.catch() 都是调用了一个方法
 *   }
 * ```
 */
fun <T> Flow<T>.interceptException(
  action: suspend ExceptionResult<FlowCollector<T>>.(Throwable) -> Unit
) : Flow<T> {
  return catch {
    ExceptionResult(it, this).action(it)
  }
}

/**
 * 使用 [Result] 来包裹一层数据，你可以配合网络请求使用：
 * ```
 * SportDetailApiService.INSTANCE
 *     .getSportDetailData()
 *     .mapOrThrowApiException()
 *     .interceptExceptionByResult {
 *         emitter.onSuccess(Result.failure(throwable)) // 发送包裹好的异常给下游
 *
 *         // 当然，这里面也可以使用上方 interceptException() 中的用法
 *
 *     }.unsafeSubscribeBy {
 *         // 这里的 it 就是 Result.success(data) 或者 Result.failure(throwable)
 *     }
 * ```
 */
fun <T> Flow<T>.interceptExceptionByResult(
  action: suspend ExceptionResult<FlowCollector<Result<T>>>.(Throwable) -> Unit
) : Flow<Result<T>> = map { Result.success(it) }.interceptException(action)

/**
 * 将 StateFlow 同步映射为只读 StateFlow，不创建额外的状态缓存或后台作用域。
 *
 * value 和 replayCache 每次读取都直接转换上游当前值；收集时转换上游发射值，按转换结果的 equals 合并相邻相等值。
 * 每次读取和每个订阅者都会独立执行转换，适合 `session.mapState { it.state }` 这样的简单属性投影。
 *
 * 官方未提供这类通用转换的讨论：
 * - [StateFlow 专用 map 的提议 #2081](https://github.com/Kotlin/kotlinx.coroutines/issues/2081)。
 *   维护者指出：挂起转换无法用于同步 value；即使不挂起，随机数等非确定转换也会使 value 与订阅结果不一致；
 *   filter 等操作还需要保存之前的结果，无法统一实现为当前值的直接投影。
 * - [StateFlow 操作符讨论 #2008](https://github.com/Kotlin/kotlinx.coroutines/issues/2008)。
 *   官方提供的通用方案是 `map { ... }.stateIn(scope, ...)`，通过共享协程计算并缓存派生结果。
 *   本方法仅覆盖确定的同步映射，不代替需要挂起、昂贵计算或共享计算结果的 stateIn。
 *
 * [StateFlow 官方文档](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)
 * 说明该接口不保证第三方继承稳定性，升级协程库时需复核此适配。
 *
 * @param transform 快速、非阻塞、无副作用的同步纯函数；结果只能依赖输入，不得读取随机数、当前时间或外部可变状态。
 * @return 从上游实时派生的只读状态流；转换异常向读取者或订阅者传播，取消订阅会取消对应的上游收集。
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> = object : StateFlow<R> {
  override val value: R
    get() = transform(this@mapState.value)

  override val replayCache: List<R>
    get() = listOf(value)

  override suspend fun collect(collector: FlowCollector<R>): Nothing {
    this@mapState.map { transform(it) }.distinctUntilChanged().collect(collector)
    // 上游 StateFlow 不会正常结束；此处补足 Nothing 返回类型，取消仍从上游 collect 直接传播。
    awaitCancellation()
  }
}
