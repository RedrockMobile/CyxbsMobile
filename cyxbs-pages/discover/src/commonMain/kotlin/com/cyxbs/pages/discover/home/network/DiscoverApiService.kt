package com.cyxbs.pages.discover.home.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.discover.home.bean.JwNewsItemBean
import com.cyxbs.pages.discover.home.bean.RollerBannerBean
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/**
 * 发现首页网络接口（KMP ktorfit 版）
 *
 * 业务侧通过 `DiscoverApiService::class.impl()` 获取。
 */
interface DiscoverApiService {

  /** 首页 banner */
  @GET("magipoke-text/banner/get")
  suspend fun getBanner(): ApiWrapper<List<RollerBannerBean>>

  /** 教务在线新闻列表 */
  @GET("magipoke-jwzx/jwNews/list")
  suspend fun getJwNews(@Query("page") page: Int): ApiWrapper<List<JwNewsItemBean>>
}
