package com.cyxbs.components.utils.logger.network

import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.ApiWrapper
import retrofit2.http.POST
import retrofit2.http.QueryMap

/**
 * @author : why
 * @time   : 2023/12/4 17:23
 * @bless  : God bless my code
 * 中央埋点上报接口
 *
 * POST /data-middle-office/stats
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

  @POST("/data-middle-offfice/stats")
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
