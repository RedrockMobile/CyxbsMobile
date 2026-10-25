package com.cyxbs.pages.schedule.data.remote

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.plugin.EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Tag

/**
 * Schedule 的 Ktorfit 接口。
 *
 * 返回值统一使用项目公共 [ApiWrapper]；实现类由 Ktorfit 在编译期生成并注册到 KtProvider，调用方通过
 * `ScheduleApiService::class.impl()` 获取。每个请求都携带冻结的 [AccountSession] tag，使 TokenPlugin 在真正
 * 发包前校验 exact session，账号切换后不会退化为新账号或匿名请求。
 */
interface ScheduleApiService {

  /** 首次进入或网络恢复时提交完整 typed inventory 与当前 pending。 */
  @POST("magipoke-todo/schedule/sync")
  @Headers("Content-Type: application/json")
  suspend fun sync(
    @Body request: SyncRequest,
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<SyncResponse>

  /** 日常新增请求；三类资源逐项处理并返回对齐结果。 */
  @POST("magipoke-todo/schedule")
  @Headers("Content-Type: application/json")
  suspend fun createSchedule(
    @Body input: MutationRequest,
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<MutationResponse>

  /** 日常更新请求；完整资源由服务端按字段时间戳合并。 */
  @PUT("magipoke-todo/schedule")
  @Headers("Content-Type: application/json")
  suspend fun updateSchedule(
    @Body input: MutationRequest,
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<MutationResponse>

  /** 日常删除请求；各 DELETE 成员只上传服务端资源 ID。 */
  @HTTP(method = "DELETE", path = "magipoke-todo/schedule", hasBody = true)
  @Headers("Content-Type: application/json")
  suspend fun deleteSchedule(
    @Body input: MutationRequest,
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<MutationResponse>

  /**
   * 物理清空当前认证账号的全部 Schedule 数据。
   *
   * 接口不接受账号参数，服务端只能使用 token 对应的 owner；调用方必须在 UI 层完成三次明确确认。
   */
  @HTTP(method = "DELETE", path = "magipoke-todo/schedule/all", hasBody = false)
  suspend fun clearAllSchedules(
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<Boolean>
}
