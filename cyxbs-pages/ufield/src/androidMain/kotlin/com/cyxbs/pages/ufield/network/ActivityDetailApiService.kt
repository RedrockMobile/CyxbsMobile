package com.cyxbs.pages.ufield.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.pages.ufield.bean.ActivityBean
import io.reactivex.rxjava3.core.Single
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 *
 * author : 苟云东
 * email : 2191288460@qq.com
 * date : 2023/8/24 11:19
 */
interface ActivityDetailApiService : IApi {

    @GET("/magipoke-ufield/activity/")
    fun getActivityData(@Query("activity_id") id: Int): Single<ApiWrapper<ActivityBean>>

    @PUT("/magipoke-ufield/activity/action/watch/")
    fun wantToSee(@Query("activity_id") id: Int): Single<ApiWrapper<ApiStatus>>

    @PUT("/magipoke-ufield/activity/addTodo/")
    fun isAdd(@Query("activity_id") id: Int): Single<ApiWrapper<ApiStatus>>

}