package com.cyxbs.pages.news.network.download

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Author: Hosigus
 * Date: 2018/9/23 19:23
 * Description: 将原ResponseBody拦截转换成RedResponseBody
 */
class RedDownloadInterceptor(private val listener: RedDownloadListener) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        return response.newBuilder().body(RedResponseBody(body, listener)).build()
    }
}