package com.cyxbs.pages.sport.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.sport.bean.SportDetailBean
import de.jensklingenberg.ktorfit.http.GET

interface SportDetailApiService {
    /**
     * 获取体育打卡详情页面数据
     */
    @GET("/magipoke-sport/sport")
    suspend fun getSportDetailData(): ApiWrapper<SportDetailBean>
}