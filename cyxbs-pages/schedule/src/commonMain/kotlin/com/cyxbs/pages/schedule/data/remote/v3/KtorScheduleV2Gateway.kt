package com.cyxbs.pages.schedule.data.remote.v3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.utils.network.ApiWrapper
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.CancellationException

/** 单次 API 调用结果；失败不会在网关内排队、重试或生成 receipt。 */
internal sealed interface ScheduleV2CallResult<out T> {
  /** HTTP 200 且业务外壳为 10000 或 20101；两者都保留公共 [ApiWrapper] 的原始 data。 */
  data class Completed<T>(val wrapper: ApiWrapper<T>) : ScheduleV2CallResult<T>

  /** HTTP 200，但统一外壳返回了 Schedule 合同之外的业务状态。 */
  data class ApiFailure(val status: Int, val info: String) : ScheduleV2CallResult<Nothing>

  /** HTTP 400 表示请求不满足服务端合同，不能当作业务 REJECTED。 */
  data class RequestInvalid(val body: String) : ScheduleV2CallResult<Nothing>

  /** 认证、服务端、解码和连接失败都没有可证明的业务执行结论。 */
  data class TransportFailure(val status: Int?, val cause: Throwable? = null) : ScheduleV2CallResult<Nothing>
}

/**
 * Schedule v2 的最小 Ktorfit 适配器。
 *
 * [api] 由 KtProvider 提供的 Ktorfit 实现注入。本类只校验账号绑定、分类一次调用结果，并保留 20101 中可处理的
 * data；pending 持久化和后续同步触发由上层 repository 负责。
 */
internal class KtorScheduleV2Gateway(
  private val api: ScheduleV2ApiService,
  private val boundSession: AccountSession,
) {
  private val boundAccountId: String = requireNotNull(boundSession.accountId) {
    "KtorScheduleV2Gateway requires a logged-in AccountSession"
  }.also { require(boundSession.state is AccountState.Login) }

  /** 首次进入或网络恢复时提交完整 typed inventory 与 pending。 */
  suspend fun sync(accountId: String, request: SyncRequest): ScheduleV2CallResult<SyncResponse> =
    call(accountId) { api.sync(request, boundSession) }

  /** 日常新增上传一个 Schedule 聚合原子批次。 */
  suspend fun createSchedule(accountId: String, input: AtomicBatch): ScheduleV2CallResult<AtomicBatchResult> =
    call(accountId) { api.createSchedule(input, boundSession) }

  /** 日常更新上传一个 Schedule 聚合原子批次。 */
  suspend fun updateSchedule(accountId: String, input: AtomicBatch): ScheduleV2CallResult<AtomicBatchResult> =
    call(accountId) { api.updateSchedule(input, boundSession) }

  /** 日常删除上传包含 parent/child DELETE 的 Schedule 聚合原子批次。 */
  suspend fun deleteSchedule(accountId: String, input: AtomicBatch): ScheduleV2CallResult<AtomicBatchResult> =
    call(accountId) { api.deleteSchedule(input, boundSession) }

  /**
   * 执行一次 Ktorfit 调用并保留统一外壳。
   *
   * 20101 是带 typed data 的业务拒绝，不访问会抛 [com.cyxbs.components.utils.network.ApiException] 的
   * [ApiWrapper.data]；其他非成功业务状态没有可应用结果，单独返回 [ScheduleV2CallResult.ApiFailure]。
   */
  private suspend fun <T> call(
    accountId: String,
    request: suspend () -> ApiWrapper<T>,
  ): ScheduleV2CallResult<T> {
    require(accountId == boundAccountId) { "accountId must match the gateway's bound AccountSession" }
    val wrapper = try {
      request()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (timeout: HttpRequestTimeoutException) {
      return ScheduleV2CallResult.TransportFailure(status = null, cause = timeout)
    } catch (invalid: ClientRequestException) {
      val body = try {
        invalid.response.bodyAsText()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Throwable) {
        "HTTP 400"
      }
      return if (invalid.response.status.value == 400) {
        ScheduleV2CallResult.RequestInvalid(body)
      } else {
        ScheduleV2CallResult.TransportFailure(invalid.response.status.value, invalid)
      }
    } catch (response: ResponseException) {
      return ScheduleV2CallResult.TransportFailure(response.response.status.value, response)
    } catch (invalidJson: JsonConvertException) {
      return ScheduleV2CallResult.TransportFailure(status = 200, cause = invalidJson)
    } catch (failure: Throwable) {
      return ScheduleV2CallResult.TransportFailure(status = null, cause = failure)
    }

    return when (wrapper.status) {
      NORMAL_STATUS, BUSINESS_REJECTED_STATUS -> {
        if (wrapper.rawData == null) {
          ScheduleV2CallResult.TransportFailure(
            status = 200,
            cause = IllegalArgumentException("Schedule v2 status=${wrapper.status} requires data"),
          )
        } else {
          ScheduleV2CallResult.Completed(wrapper)
        }
      }
      else -> ScheduleV2CallResult.ApiFailure(wrapper.status, wrapper.info)
    }
  }

  private companion object {
    const val NORMAL_STATUS = 10000
    const val BUSINESS_REJECTED_STATUS = 20101
  }
}
