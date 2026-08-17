package com.cyxbs.pages.schedule.data.remote.v3

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.utils.network.ApiWrapper
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Tag

/**
 * Schedule v2 的 Ktorfit 接口。
 *
 * 返回值统一使用项目公共 [ApiWrapper]；实现类由 Ktorfit 在编译期生成并注册到 KtProvider，调用方通过
 * `ScheduleV2ApiService::class.impl()` 获取。每个请求都携带冻结的 [AccountSession] tag，使 TokenPlugin 在真正
 * 发包前校验 exact session，账号切换后不会退化为新账号或匿名请求。
 */
interface ScheduleV2ApiService {

  /** 首次进入或网络恢复时提交完整 typed inventory 与当前 pending。 */
  @POST("v2/schedule-mutations")
  suspend fun sync(
    @Body request: SyncRequest,
    @Tag(EXPECTED_ACCOUNT_SESSION_TAG) session: AccountSession,
  ): ApiWrapper<SyncResponse>

  /** 日常新增一个 Schedule 聚合批次，可同时携带关联 Category 与 OccurrenceOverride。 */
  @POST("v2/schedules")
  suspend fun createSchedule(
    @Body input: AtomicBatch,
    @Tag(EXPECTED_ACCOUNT_SESSION_TAG) session: AccountSession,
  ): ApiWrapper<AtomicBatchResult>

  /** 日常更新一个 Schedule 聚合批次；服务端返回批次涉及 identity 的 canonical 结果。 */
  @PUT("v2/schedules")
  suspend fun updateSchedule(
    @Body input: AtomicBatch,
    @Tag(EXPECTED_ACCOUNT_SESSION_TAG) session: AccountSession,
  ): ApiWrapper<AtomicBatchResult>

  /** 日常删除聚合批次；各 DELETE 成员仍只上传 identity 与 localModifiedAt。 */
  @HTTP(method = "DELETE", path = "v2/schedules", hasBody = true)
  suspend fun deleteSchedule(
    @Body input: AtomicBatch,
    @Tag(EXPECTED_ACCOUNT_SESSION_TAG) session: AccountSession,
  ): ApiWrapper<AtomicBatchResult>
}

/**
 * 必须与 TokenPlugin 的 ExpectedAccountSession attribute key 同名。
 *
 * Ktorfit 的 [Tag] 会以该名称写入本地 request attribute；它不会进入 header、query 或 JSON body。
 */
private const val EXPECTED_ACCOUNT_SESSION_TAG = "ExpectedAccountSession"
