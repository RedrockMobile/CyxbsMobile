package com.cyxbs.pages.sport.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.sport.bean.NoticeItem
import de.jensklingenberg.ktorfit.http.GET

interface SportNoticeApiService {
    /**
     * 获取体育打卡信息说明
     */
    @GET("/magipoke-sport/notice")
    suspend fun getSportNotice(): ApiWrapper<List<NoticeItem>>
}