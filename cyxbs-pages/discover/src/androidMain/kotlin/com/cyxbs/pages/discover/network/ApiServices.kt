package com.cyxbs.pages.discover.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.discover.API_ROLLER_VIEW
import com.cyxbs.pages.discover.bean.NewsListItem
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Created by zxzhu
 *   2018/9/7.
 *   enjoy it !!
 */
interface ApiServices {

    @GET(API_ROLLER_VIEW)
    fun getRollerViewInfo(): Observable<ApiWrapper<List<RollerViewInfo>>>

    @GET("/magipoke-jwzx/jwNews/list")
    fun getNewsList(@Query("page") page: Int): Observable<ApiWrapper<List<NewsListItem>>>
}