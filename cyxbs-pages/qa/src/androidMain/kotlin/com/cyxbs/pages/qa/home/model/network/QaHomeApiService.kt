package com.cyxbs.pages.qa.home.model.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.pages.qa.home.model.bean.QaData
import com.cyxbs.pages.qa.home.model.bean.QaRequestData
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * description ： Qa主页以及搜索页面的Api
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 15:04
 */
interface QaHomeApiService : IApi {
    /**
     * Qa主页信息
     */
    @POST("/magipoke-qa/api/v1/mobile/list")
    suspend fun getQacontentData(
        @Body requestData: QaRequestData
    ): ApiWrapper<QaData>


    /**
     * Qa点赞
     */
    @POST("/magipoke-qa/api/v1/mobile/like")
    fun getLike(
        @Query("id") id: Int
    ): Single<ApiStatus>

    /**
     * Qa取消点赞
     */
    @POST("/magipoke-qa/api/v1/mobile/unlike")
    fun getUnlike(
        @Query("id") id: Int
    ):Single<ApiStatus>

    /**
     * Qa搜索
     */
    @POST("/magipoke-qa/api/v1/mobile/search")
    fun getSearch(
        @Query("q") q: String
    ): Single<ApiWrapper<QaData>>

}