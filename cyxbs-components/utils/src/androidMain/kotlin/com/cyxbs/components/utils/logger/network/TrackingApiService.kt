package com.cyxbs.components.utils.logger.network

import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.ApiWrapper
import retrofit2.http.POST
import retrofit2.http.QueryMap


/**
 * 旧版埋点上报接口（对应旧接口 /data-middle-office/stats）
 */
internal interface OldTrackingApiService {
  companion object {
    /**
     * 旧接口实例（通过 Retrofit 生成）
     */
    val INSTANCE: OldTrackingApiService by lazy {
      ApiGenerator.getApiService(OldTrackingApiService::class)
    }
  }

  /**
   * 旧版埋点上报接口（POST）
   * @param params 旧接口需要的参数（如 page_name、stay_time 等）
   */
  @POST("/data-middle-office/stats")
  suspend fun trackEvent(
    @QueryMap(encoded = true)  // 参数需要 URL 编码
    params: Map<String, String>
  ): ApiWrapper<TrackingResponseBody>
}
/**
 * @author : why
 * @time   : 2023/12/4 17:23
 * @bless  : God bless my code
 * 中央埋点上报接口
 *
 * POST /magicchrist-statistic/trackingpoint/stats
 * ├─ id         埋点 ID             (Int → String)
 * ├─ user_id    加密后的用户 ID      (String)
 * ├─ time       次数 / 停留时长(秒)  (Int → String)
 * └─ start_time 页面打开时间戳(毫秒) (Long → String)
 *
 * 返回：
 * {
 *   "status": 10000,
 *   "info":   "success"
 * }
 */
internal interface TrackingApiService {

  @POST("/magicchrist-statistic/trackingpoint/stats")
  suspend fun trackEvent(
    @QueryMap(encoded = true)
    params: Map<String, String>
  ): ApiWrapper<TrackingResponseBody>

  companion object {
    /**
     * LoggerApiService的实例
     */
    val INSTANCE: TrackingApiService by lazy {
      ApiGenerator.getApiService(TrackingApiService::class)
    }
  }
}
