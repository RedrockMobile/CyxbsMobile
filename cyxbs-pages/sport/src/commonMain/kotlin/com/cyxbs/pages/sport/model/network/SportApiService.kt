package com.cyxbs.pages.sport.model.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.sport.model.NoticeItem
import com.cyxbs.pages.sport.model.SportDetailBean
import de.jensklingenberg.ktorfit.http.GET

/**
 * 体育打卡网络接口（KMP ktorfit 版）
 *
 * 业务侧通过 `SportApiService::class.impl()` 获取。
 */
interface SportApiService {

  /** 体育打卡详情数据 */
  @GET("magipoke-sport/sport")
  suspend fun getSportDetail(): ApiWrapper<SportDetailBean>

  /** 体育打卡信息说明（feed 说明弹窗内容，后端下发） */
  @GET("magipoke-sport/notice")
  suspend fun getSportNotice(): ApiWrapper<List<NoticeItem>>
}
