package com.cyxbs.components.utils.logger

import com.cyxbs.components.utils.BuildConfig
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.logger.bean.TrackingResultBean
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.components.utils.logger.network.TrackingApiService
import com.cyxbs.components.utils.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 埋点上报工具
 *
 * 统一支持两种场景：
 * 1. trackExposureEvent   —— 点击 / 曝光即上报，time 固定为 1
 * 2. trackStayEvent       —— 页面停留上报，time = 停留秒数
 *
 * 后端接口：POST /data-middle-office/stats
 * 必填参数：
 *   id         埋点 ID
 *   user_id    加密后的用户 ID
 *   time       次数或时长（秒）
 *   start_time 页面打开时间戳（毫秒）
 *
 * 返回体 status 字段与 [TrackingResultBean.status] 对应
 */
object TrackingUtils {

  private const val TAG = "TrackingUtils"

  /* ---------- 对外 API ---------- */

  /**
   * 点击 / 曝光类上报（time = 1）
   */
  suspend fun trackExposureEvent(
    event: ClickEvent
  ): Result<TrackingResultBean?> =
    trackEventInternal(
      event           = event,
      timeValue       = 1,                         // 点击/曝光只记一次
      startTimeMillis = System.currentTimeMillis()
    )

  /**
   * 页面停留时长上报
   *
   * @param stayDurationMillis 页面停留时长（毫秒）
   * @param startTimeMillis    页面打开时间戳（毫秒）
   */
  suspend fun trackStayEvent(
    event: ClickEvent,
    stayDurationMillis: Long,
    startTimeMillis: Long
  ): Result<TrackingResultBean?> =
    trackEventInternal(
      event           = event,
      timeValue       = (stayDurationMillis / 1000).toInt(), // 后端约定用秒
      startTimeMillis = startTimeMillis
    )

  /* ---------- 内部实现 ---------- */

  private suspend fun trackEventInternal(
    event: ClickEvent,
    timeValue: Int,
    startTimeMillis: Long
  ): Result<TrackingResultBean?> =
    withContext(Dispatchers.IO) {
      runCatching {
        // 构造公共参数
        val params = mapOf(
          "id"         to event.id,
          "user_id"    to getEncodedUserId(),
          "time"       to timeValue.toString(),
          "start_time" to startTimeMillis.toString()
        )

        // 发起网络请求
        val status = TrackingApiService.INSTANCE
          .trackEvent(params)
          .data
          .status

        LogUtils.d(TAG, "trackEvent id=${event.id}, status=$status")

        // 匹配状态码
        TrackingResultBean.values().find { it.status == status }.also { bean ->
          if (bean != TrackingResultBean.SUCCESS) toastWhenDebug(bean)
        }
      }.onFailure {
        // 网络或解析异常
        it.printStackTrace()
        toastWhenDebug(null)
      }
    }

  /* ---------- 辅助方法 ---------- */

  private fun toastWhenDebug(bean: TrackingResultBean?) {
    if (BuildConfig.DEBUG) {
      val msg = bean?.msg ?: "埋点上报失败，请检查网络请求日志！"
      msg.toast()
    }
  }

  /** @return 已加密的用户 ID —— 请替换为正式实现 */
  private fun getEncodedUserId(): String =
    /* AccountManager.getEncodeUid() */ "test_user"
}
