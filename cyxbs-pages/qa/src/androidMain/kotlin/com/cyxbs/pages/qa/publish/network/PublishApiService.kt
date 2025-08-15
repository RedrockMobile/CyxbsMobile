package com.cyxbs.pages.qa.publish.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.pages.qa.publish.network.bean.request.PublishQuestionRequest
import com.cyxbs.pages.qa.publish.network.bean.response.PublishQuestionBean
import com.cyxbs.pages.qa.publish.network.bean.response.SearchBean
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * description ： 关于发布问题的API
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 18:24
 */
interface PublishApiService : IApi {
    /**
     * 发布问题
     */
    @POST("/magipoke-qa/api/v1/mobile/publish")
    fun publishQuestion(
        @Body request: PublishQuestionRequest
    ): Single<ApiWrapper<PublishQuestionBean>>


    /**
     * 搜索问题(模糊搜索)
     */
    @POST("/magipoke-qa/api/v1/mobile/search")
    fun searchQuestion(
        @Query("q") question: String
    ): Single<ApiWrapper<SearchBean>>


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