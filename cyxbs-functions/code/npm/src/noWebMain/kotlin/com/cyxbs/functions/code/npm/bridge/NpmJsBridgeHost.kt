package com.cyxbs.functions.code.npm.bridge

import com.cyxbs.functions.code.js.runtime.JsRuntimeBridge
import com.cyxbs.functions.code.js.runtime.JsRuntimeBridgeBinding
import com.cyxbs.functions.code.js.runtime.JsRuntimeCallback
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgeHostAbi
import com.cyxbs.functions.code.npm.js.bridge.NpmJsBridgePackageScope
import com.g985892345.provider.manager.KtProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * KSP 生成的宿主桥工厂。
 *
 * 工厂由 KtProvider 自动发现；[bridgeId] 取接口限定名，[packageScope] 在生成期完成静态校验。
 */
interface NpmJsBridgeHostFactory {
  val bridgeId: String
  val methodNames: Set<String>
  val packageScope: NpmJsBridgePackageScope
  val packageNames: Set<String>

  /** 为一个隔离 Runtime 创建实现和分发器。 */
  fun create(context: NpmJsBridgeContext): NpmJsBridgeHostDispatcher
}

/** KSP 生成的单 Runtime 桥分发器。 */
interface NpmJsBridgeHostDispatcher {
  val bridgeId: String
  val methodNames: Set<String>

  /** 解码参数、调用端上实现并编码返回值。 */
  suspend fun invoke(methodName: String, argumentsJson: String): String
}

/**
 * 端上桥实现可使用的 Runtime 上下文。
 *
 * [entryPackageName] 由 Loader 根据真实入口包固定，不能由 JavaScript 伪造；[runtime] 只允许执行
 * 回调，不提供注册宿主函数的能力。
 */
class NpmJsBridgeContext internal constructor(
  val entryPackageName: String,
  val runtime: JsRuntimeCallback,
)

/**
 * 一个 npm Runtime 唯一安装的桥网关。
 *
 * 网关在构造时按真实入口包过滤 factory，describe 与 invoke 共用同一可见集合；未授权桥与未安装
 * 桥返回完全相同的结果，避免动态包枚举宿主能力。
 */
internal class NpmJsBridgeGateway(
  private val entryPackageName: String,
) : JsRuntimeBridge(NpmJsBridgeHostAbi.GATEWAY) {
  private var runtimeCallback: JsRuntimeCallback? = null
  private var dispatchers: Map<String, NpmJsBridgeHostDispatcher>? = null

  override val binding: JsRuntimeBridgeBinding =
    JsRuntimeBridgeBinding.AsyncFunction(::handleInvocation)

  override fun onRuntimeReady(callback: JsRuntimeCallback) {
    runtimeCallback = callback
  }

  /** 解析固定位置参数并执行 capability 查询或方法调用。 */
  private suspend fun handleInvocation(arguments: Array<Any?>): String {
    val operation = arguments.getOrNull(0) as? String
      ?: return error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Missing bridge operation.")
    val bridgeId = arguments.getOrNull(1) as? String
      ?: return error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Missing bridge id.")
    return when (operation) {
      NpmJsBridgeHostAbi.DESCRIBE -> {
        if (arguments.size != 2) {
          error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Invalid describe arguments.")
        } else {
          val dispatcher = dispatcher(bridgeId)
            ?: return error(NpmJsBridgeHostAbi.ERROR_BRIDGE_NOT_INSTALLED)
          buildJsonObject {
            put("ok", true)
            put("methods", JsonArray(dispatcher.methodNames.sorted().map(::JsonPrimitive)))
          }.toString()
        }
      }
      NpmJsBridgeHostAbi.INVOKE -> {
        val methodName = arguments.getOrNull(2) as? String
          ?: return error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Missing method name.")
        val argumentsJson = arguments.getOrNull(3) as? String
          ?: return error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Missing method arguments.")
        if (arguments.size != 4) {
          return error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Invalid invoke arguments.")
        }
        val dispatcher = dispatcher(bridgeId)
          ?: return error(NpmJsBridgeHostAbi.ERROR_BRIDGE_NOT_INSTALLED)
        if (methodName !in dispatcher.methodNames) {
          return error(
            code = NpmJsBridgeHostAbi.ERROR_METHOD_NOT_IMPLEMENTED,
            methodName = methodName,
          )
        }
        try {
          val resultJson = dispatcher.invoke(methodName, argumentsJson)
          buildJsonObject {
            put("ok", true)
            put("result", resultJson)
          }.toString()
        } catch (exception: CancellationException) {
          throw exception
        } catch (_: Throwable) {
          // 实现异常可能包含平台路径或内部类型，只返回稳定错误，不跨 npm 边界泄露细节。
          error(NpmJsBridgeHostAbi.ERROR_INVOCATION_FAILED, "Bridge invocation failed.")
        }
      }
      else -> error(NpmJsBridgeHostAbi.ERROR_INVALID_REQUEST, "Unknown bridge operation.")
    }
  }

  /** 延迟创建当前入口包可见的全部桥实现，确保 Context 已收到 Runtime 回调。 */
  private fun dispatcher(bridgeId: String): NpmJsBridgeHostDispatcher? {
    val current = dispatchers ?: createDispatchers().also { dispatchers = it }
    return current[bridgeId]
  }

  private fun createDispatchers(): Map<String, NpmJsBridgeHostDispatcher> {
    val callback = checkNotNull(runtimeCallback) { "JavaScript Runtime bridge is not ready." }
    val context = NpmJsBridgeContext(entryPackageName, callback)
    return buildMap {
      KtProvider.allImpl(NpmJsBridgeHostFactory::class).values.forEach { provider ->
        val factory = provider.get()
        if (!factory.isVisibleTo(entryPackageName)) return@forEach
        val previous = put(factory.bridgeId, factory.create(context))
        check(previous == null) {
          "Multiple npm JavaScript bridge implementations target '" + factory.bridgeId + "'."
        }
      }
    }
  }

  /** package scope 在 KSP 已校验；运行时只按入口包执行不可绕过的最终过滤。 */
  private fun NpmJsBridgeHostFactory.isVisibleTo(packageName: String): Boolean {
    return packageScope == NpmJsBridgePackageScope.ALL_PACKAGES || packageName in packageNames
  }

  private fun error(
    code: String,
    message: String? = null,
    methodName: String? = null,
  ): String = buildJsonObject {
    put("ok", false)
    put("code", code)
    message?.let { put("message", it) }
    methodName?.let { put("methodName", it) }
  }.toString()
}
