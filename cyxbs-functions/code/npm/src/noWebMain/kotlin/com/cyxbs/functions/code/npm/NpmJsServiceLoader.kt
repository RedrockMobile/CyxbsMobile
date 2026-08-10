package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.npm.diagnostic.NpmJsServiceLoadMetrics
import com.cyxbs.functions.code.npm.diagnostic.NpmJsServiceLoadStage
import com.cyxbs.functions.code.npm.model.NpmEntryRequest
import com.cyxbs.functions.code.npm.model.NpmEntryVersion
import com.cyxbs.functions.code.npm.module.NpmModuleGraphFactory
import com.cyxbs.functions.code.npm.pool.NpmPackagePool
import com.cyxbs.functions.code.npm.model.NpmRefreshPolicy
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceProtocolException
import com.cyxbs.functions.code.npm.internal.NpmGraphJsModuleLoader
import com.cyxbs.functions.code.npm.model.NpmException
import com.cyxbs.functions.code.npm.pool.NpmPreparedEntryLease
import com.cyxbs.functions.code.npm.service.NpmJsServiceProxyFactory
import com.cyxbs.functions.code.npm.service.NpmJsServiceSession
import com.g985892345.provider.manager.KtProvider
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass
import kotlin.time.TimeSource

/**
 * 以 commonMain Service 接口为入口，准备 npm 依赖并返回对应 Kotlin 代理。
 *
 * 调用方传入带 `@NpmJsService` 的接口、npm 包名和版本；协议摘要、JSON 编解码与代理创建由 KSP
 * 生成代码提供。每个成功加载的实例独占一个 JavaScript Runtime，使用完必须调用
 * [NpmJsServiceInstance.close]。工厂使用真实 [KClass] 建立索引，不依赖可能被混淆的运行时类名。
 *
 * @param packagePool App 级长生命周期 npm 全局包池；默认使用 [NpmPackagePool.Default]。
 * @param runtimeFactory JavaScript Runtime 实现，默认从 KtProvider 获取 QuickJS。
 * @param runtimeOptions 每个 Service Runtime 使用的资源限制和可选后备 Module Loader。
 */
class NpmJsServiceLoader(
  private val packagePool: NpmPackagePool = NpmPackagePool.Default,
  private val runtimeFactory: JsRuntimeFactory = JsRuntimeFactory.implOrNull()
    ?: throw NpmJsServiceProtocolException(
      "No JavaScript Runtime implementation is installed for npm Services.",
    ),
  private val runtimeOptions: JsRuntimeOptions = JsRuntimeOptions(),
  private val moduleGraphFactory: NpmModuleGraphFactory = NpmModuleGraphFactory(),
) {

  /** KtProvider 的字符串键只负责容纳多个工厂，实际查找始终使用工厂声明的 Service KClass。 */
  private val factories by lazy {
    buildMap<KClass<out NpmJsServiceInstance>, NpmJsServiceProxyFactory<*>> {
      KtProvider.allImpl(NpmJsServiceProxyFactory::class).values.forEach { provider ->
        val factory = provider.get()
        val previous = put(factory.serviceClass, factory)
        if (previous != null) {
          throw NpmJsServiceProtocolException(
            "Multiple generated npm JavaScript Service factories target the same interface.",
          )
        }
      }
    }
  }

  /**
   * 通过接口 [serviceClass] 加载指定 npm 包中的实现。
   *
   * `latest` 的远端检查、依赖下载和协议校验都在本方法返回前完成，运行期间不会切换依赖图。
   *
   * @param serviceClass 带 `@NpmJsService` 且已经生成代理工厂的 commonMain 接口。
   * @param packageName npm 完整包名，例如 `@cyxbs-mobile/language-javascript`。
   * @param version `latest` 或完整精确 semver；默认在每个包池实例首次使用入口时检查最新版。
   * @param refreshPolicy `AUTO` 按包池生命周期刷新并允许回退旧图；`FORCE` 要求本次刷新成功。
   * @param metrics 可选的单次加载诊断对象；调用结束后可读取各内部阶段耗时，不支持并发复用。
   * @return 由 KSP 生成且由独立 Runtime 支撑的业务接口代理。
   * @throws NpmJsServiceProtocolException 接口没有生成工厂、工厂身份不一致或远端协议不匹配。
   * @throws NpmJsServiceInvocationException Runtime 创建或 npm 入口初始化失败。
   * @throws NpmException npm 解析、下载、校验或存储失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmJsServiceProtocolException::class,
    NpmJsServiceInvocationException::class,
    NpmException::class,
    CancellationException::class,
  )
  suspend fun <T : NpmJsServiceInstance> load(
    serviceClass: KClass<T>,
    packageName: String,
    version: String = LATEST_VERSION,
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
    metrics: NpmJsServiceLoadMetrics? = null,
  ): T {
    val factory = metrics.measureStage(NpmJsServiceLoadStage.RESOLVE_PROXY_FACTORY) {
      findFactory(serviceClass).also { validateFactory(serviceClass, it) }
    }
    val serviceId = factory.serviceId
    val request = NpmEntryRequest(
      packageName = packageName,
      version = version.toEntryVersion(),
      entryName = "$serviceId|$packageName|$version",
    )
    val lease = metrics.measureStage(NpmJsServiceLoadStage.ACQUIRE_NPM_ENTRY) {
      packagePool.acquireEntry(request, refreshPolicy, metrics?.packagePoolMetrics)
    }
    val graph = try {
      metrics.measureStage(NpmJsServiceLoadStage.BUILD_MODULE_GRAPH) {
        moduleGraphFactory.create(lease.preparedEntry).also { createdGraph ->
          if (createdGraph.load(createdGraph.entryModuleName) == null) {
            throw NpmJsServiceProtocolException(
              "Prepared npm Service entry '${createdGraph.entryModuleName}' is missing from its Module graph.",
            )
          }
        }
      }
    } catch (throwable: Throwable) {
      lease.releaseAfterFailure(throwable)
    }
    val runtime = try {
      metrics.measureStage(NpmJsServiceLoadStage.CREATE_RUNTIME) {
        runtimeFactory.create(
          runtimeOptions.copy(
            moduleLoader = NpmGraphJsModuleLoader(
              graph = graph,
              fallback = runtimeOptions.moduleLoader,
            ),
          ),
        )
      }
    } catch (exception: CancellationException) {
      lease.releaseAfterFailure(exception)
    } catch (exception: JsRuntimeException) {
      lease.releaseAfterFailure(
        NpmJsServiceInvocationException(
          "Failed to create Runtime for npm JavaScript Service '$serviceId'.",
          exception,
        ),
      )
    } catch (throwable: Throwable) {
      lease.releaseAfterFailure(throwable)
    }
    val session = NpmJsServiceSession(
      runtime = runtime,
      lease = lease,
      serviceId = serviceId,
      schemaHash = factory.schemaHash,
    )
    return try {
      metrics.measureStage(NpmJsServiceLoadStage.INITIALIZE_SERVICE) {
        session.initialize(graph.entryModuleName, packageName)
      }
      metrics.measureStage(NpmJsServiceLoadStage.CREATE_SERVICE_PROXY) {
        factory.create(session)
      }
    } catch (throwable: Throwable) {
      session.closeAfterFailure(throwable)
    }
  }

  /** 以 KClass 查找工厂，唯一一次受控转换由相同的 [serviceClass] 身份保证。 */
  @Suppress("UNCHECKED_CAST")
  private fun <T : NpmJsServiceInstance> findFactory(
    serviceClass: KClass<T>,
  ): NpmJsServiceProxyFactory<T> {
    return factories[serviceClass] as? NpmJsServiceProxyFactory<T>
      ?: throw NpmJsServiceProtocolException(
        "No generated npm JavaScript Service factory was found for the requested interface.",
      )
  }

  /** 防止错误的生成工厂把一个接口代理成另一个协议。 */
  private fun <T : NpmJsServiceInstance> validateFactory(
    serviceClass: KClass<T>,
    factory: NpmJsServiceProxyFactory<T>,
  ) {
    if (factory.serviceClass != serviceClass) {
      throw NpmJsServiceProtocolException(
        "Generated npm JavaScript Service factory identity does not match its lookup key.",
      )
    }
    if (factory.serviceId.isBlank() || factory.schemaHash.isBlank()) {
      throw NpmJsServiceProtocolException(
        "Generated npm JavaScript Service factory is incomplete.",
      )
    }
  }

  /** 将业务传入的字符串转换为包池的显式版本策略。 */
  private fun String.toEntryVersion(): NpmEntryVersion {
    return if (this == LATEST_VERSION) {
      NpmEntryVersion.Latest
    } else {
      NpmEntryVersion.Exact(this)
    }
  }

  private companion object {
    const val LATEST_VERSION = "latest"
  }
}

/** 执行单个加载阶段；仅在调用方传入指标对象时读取单调时钟并记录成功状态。 */
private suspend fun <T> NpmJsServiceLoadMetrics?.measureStage(
  stage: NpmJsServiceLoadStage,
  block: suspend () -> T,
): T {
  val target = this ?: return block()
  val startedAt = TimeSource.Monotonic.markNow()
  var succeeded = false
  try {
    return block().also { succeeded = true }
  } finally {
    target.record(stage, startedAt.elapsedNow(), succeeded)
  }
}

/** 失败路径释放租约，并把释放异常附加到原始异常。 */
private suspend fun NpmPreparedEntryLease.releaseAfterFailure(
  failure: Throwable,
): Nothing {
  try {
    release()
  } catch (cleanupFailure: Throwable) {
    failure.addSuppressed(cleanupFailure)
  }
  throw failure
}

/** 失败路径关闭完整会话，并保留最初的初始化或代理创建异常。 */
private suspend fun NpmJsServiceSession.closeAfterFailure(failure: Throwable): Nothing {
  try {
    close()
  } catch (cleanupFailure: Throwable) {
    failure.addSuppressed(cleanupFailure)
  }
  throw failure
}
