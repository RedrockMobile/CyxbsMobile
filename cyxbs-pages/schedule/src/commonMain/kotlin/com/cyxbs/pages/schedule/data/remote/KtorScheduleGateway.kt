package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.components.utils.network.ApiWrapper
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone

private const val MAX_DIAGNOSTIC_RESPONSE_CHARS = 2_000

/** 单次 API 调用结果；失败不会在网关内排队、重试或生成 receipt。 */
internal sealed interface ScheduleCallResult<out T> {
  /** HTTP 200 且业务外壳为 10000；逐资源拒绝保留在 data 内。 */
  data class Completed<T>(val wrapper: ApiWrapper<T>) : ScheduleCallResult<T>

  /** HTTP 200，但统一外壳返回了 Schedule 合同之外的业务状态。 */
  data class ApiFailure(val status: Int, val info: String) : ScheduleCallResult<Nothing>

  /** HTTP 400 表示请求不满足服务端合同，不能当作业务 REJECTED。 */
  data class RequestInvalid(val body: String) : ScheduleCallResult<Nothing>

  /** 认证、服务端、解码和连接失败都没有可证明的业务执行结论。 */
  data class TransportFailure(val status: Int?, val cause: Throwable? = null) : ScheduleCallResult<Nothing>
}

/**
 * Schedule 的最小 Ktorfit 适配器。
 *
 * [api] 由 KtProvider 提供的 Ktorfit 实现注入。本类只校验账号绑定并分类网络结果；逐资源拒绝由上层从
 * [MutationResponse] 或 [SyncResponse] 处理，pending 持久化和后续同步触发仍由 repository 负责。
 */
internal class KtorScheduleGateway(
  private val api: ScheduleApiService,
  private val boundSession: AccountSession,
) {
  private val boundAccountId: String = requireNotNull(boundSession.accountId) {
    "KtorScheduleGateway requires a logged-in AccountSession"
  }.also { require(boundSession.state is AccountState.Login) }
  private val diagnosticTimeZone = TimeZone.currentSystemDefault()

  /** 首次进入或网络恢复时提交完整 typed inventory 与 pending。 */
  suspend fun sync(accountId: String, request: SyncRequest): ScheduleCallResult<SyncResponse> {
    request.logScheduleRequest(diagnosticTimeZone)
    val result = call(accountId, "SYNC") { api.sync(request, boundSession) }
    (result as? ScheduleCallResult.Completed)?.wrapper?.rawData
      ?.logScheduleResponse(diagnosticTimeZone)
    return result
  }

  /** 日常新增上传本次命令涉及的独立资源变更。 */
  suspend fun createSchedule(accountId: String, input: MutationRequest): ScheduleCallResult<MutationResponse> =
    callMutation(accountId, "CREATE", input) { api.createSchedule(input, boundSession) }

  /** 日常更新上传本次命令涉及的独立资源变更。 */
  suspend fun updateSchedule(accountId: String, input: MutationRequest): ScheduleCallResult<MutationResponse> =
    callMutation(accountId, "UPDATE", input) { api.updateSchedule(input, boundSession) }

  /** 日常删除上传本次命令涉及的独立资源变更。 */
  suspend fun deleteSchedule(accountId: String, input: MutationRequest): ScheduleCallResult<MutationResponse> =
    callMutation(accountId, "DELETE", input) { api.deleteSchedule(input, boundSession) }

  /** 输出一次日常变更请求及其逐资源响应，再复用统一网络结果分类。 */
  private suspend fun callMutation(
    accountId: String,
    operation: String,
    input: MutationRequest,
    request: suspend () -> ApiWrapper<MutationResponse>,
  ): ScheduleCallResult<MutationResponse> {
    input.logScheduleRequest("REQUEST $operation", diagnosticTimeZone)
    val result = call(accountId, operation, request)
    (result as? ScheduleCallResult.Completed)?.wrapper?.rawData
      ?.logScheduleResponse(diagnosticTimeZone)
    return result
  }

  /**
   * 执行一次 Ktorfit 调用并保留统一外壳。
   *
   * 合同内的逐资源拒绝仍使用 status=10000，并位于 typed data 中；其他非成功业务状态没有可应用结果，
   * 单独返回 [ScheduleCallResult.ApiFailure]。
   */
  private suspend fun <T> call(
    accountId: String,
    operation: String,
    request: suspend () -> ApiWrapper<T>,
  ): ScheduleCallResult<T> {
    require(accountId == boundAccountId) { "accountId must match the gateway's bound AccountSession" }
    log(NETWORK_LOG_TAG, "$operation started")
    val wrapper = try {
      request()
    } catch (cancelled: CancellationException) {
      log(NETWORK_LOG_TAG, "$operation cancelled")
      throw cancelled
    } catch (timeout: HttpRequestTimeoutException) {
      log(NETWORK_LOG_TAG, "$operation timeout")
      return ScheduleCallResult.TransportFailure(status = null, cause = timeout)
    } catch (invalid: ClientRequestException) {
      val body = try {
        invalid.response.bodyAsText()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Throwable) {
        "HTTP 400"
      }
      log(
        NETWORK_LOG_TAG,
        httpFailureLogText(
          operation = operation,
          status = invalid.response.status.value,
          contentType = invalid.response.headers[HttpHeaders.ContentType],
        ),
      )
      return if (invalid.response.status.value == 400) {
        ScheduleCallResult.RequestInvalid(body)
      } else {
        ScheduleCallResult.TransportFailure(invalid.response.status.value, invalid)
      }
    } catch (response: ResponseException) {
      log(NETWORK_LOG_TAG, "$operation HTTP ${response.response.status.value}")
      return ScheduleCallResult.TransportFailure(response.response.status.value, response)
    } catch (invalidJson: JsonConvertException) {
      log(NETWORK_LOG_TAG, "$operation decodeFailure type=${invalidJson::class.simpleName}")
      return ScheduleCallResult.TransportFailure(status = 200, cause = invalidJson)
    } catch (failure: Throwable) {
      // 只记录异常类型；异常 message 可能包含 URL、响应片段等，不写入长期诊断日志。
      log(NETWORK_LOG_TAG, "$operation transportFailure type=${failure::class.simpleName}")
      return ScheduleCallResult.TransportFailure(status = null, cause = failure)
    }

    log(NETWORK_LOG_TAG, "$operation completed businessStatus=${wrapper.status}")
    return when (wrapper.status) {
      NORMAL_STATUS -> {
        if (wrapper.rawData == null) {
          ScheduleCallResult.TransportFailure(
            status = 200,
            cause = IllegalArgumentException("Schedule status=${wrapper.status} requires data"),
          )
        } else {
          ScheduleCallResult.Completed(wrapper)
        }
      }
      else -> {
        log(
          NETWORK_LOG_TAG,
          "$operation businessFailure status=${wrapper.status} " +
              "info=${wrapper.info.toDiagnosticLogText()}",
        )
        ScheduleCallResult.ApiFailure(wrapper.status, wrapper.info)
      }
    }
  }

  private companion object {
    const val NETWORK_LOG_TAG = "ScheduleNetwork"
    const val NORMAL_STATUS = 10000
  }
}

/**
 * 将服务端错误响应压缩为单行并限制长度，便于在网络日志中直接定位拒绝原因，同时避免异常网关返回无限内容。
 * 该文本只来自响应 body/info，不包含请求 header、Authorization 或 token。
 */
private fun String.toDiagnosticLogText(): String =
  replace('\n', ' ').replace('\r', ' ').take(MAX_DIAGNOSTIC_RESPONSE_CHARS)

/**
 * 生成非成功 HTTP 响应的安全日志摘要。
 *
 * 响应正文可能是网关 HTML、包含实际输入值的错误页，甚至包含上游注入的敏感信息，因此这里刻意不接收
 * 正文，只保留定位网络链路所需的状态码与响应类型。HTTP 400 的正文仍由调用方保存在 [ScheduleCallResult.RequestInvalid]
 * 中，用于解析服务端明确下发的业务字段错误，但不会直接写入日志。
 */
internal fun httpFailureLogText(
  operation: String,
  status: Int,
  contentType: String?,
): String = "$operation HTTP $status contentType=${contentType?.substringBefore(';') ?: "unknown"}"
