package com.cyxbs.pages.qa.detail.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.pages.qa.detail.bean.QuestionData
import io.reactivex.rxjava3.core.Single
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 *description:api
 * author 王以飞
 * email 1206897770@qq.com
 * date 2025-8-15
 */
interface DetailApiService : IApi {
    /**
     * 获取qa详情的数据
     */
    @GET("/magipoke-qa/api/v1/mobile/detail")
    fun getDetail(@Query("id") id: Long): Single<ApiWrapper<QuestionData>>

    /**
     * 点赞问题
     */
    @POST("/magipoke-qa/api/v1/mobile/like")
    fun likeQuestion(
        @Query("id") id: Long
    ): Single<ApiStatus>

    /**
     * 取消点赞
     */
    @POST("/magipoke-qa/api/v1/mobile/unlike")
    fun unLikeQuestion(
        @Query("id") id: Long
    ): Single<ApiStatus>

}