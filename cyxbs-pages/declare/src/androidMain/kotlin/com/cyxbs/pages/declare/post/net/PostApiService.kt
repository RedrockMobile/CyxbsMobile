package com.cyxbs.pages.declare.post.net

import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.pages.declare.post.bean.PostReqBean
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.components.utils.network.api
import io.reactivex.rxjava3.core.Single
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * com.mredrock.cyxbs.declare.pages.post.net.PostApiService.kt
 * CyxbsMobile_Android
 *
 * @author 寒雨
 * @since 2023/3/17 下午12:02
 */
interface PostApiService : IApi {
    @POST("magipoke-attitude/declare")
    @Headers("Content-Type: application/json")
    fun postVote(
        @Body
        body: RequestBody
    ): Single<ApiStatus>

    companion object : PostApiService by PostApiService::class.api {

        private val gson = defaultGson
        private val json = "application/json".toMediaType()

        fun postVote(title: String, choices: List<String>): Single<ApiStatus> {
            return postVote(gson.toJson(PostReqBean(title, choices)).toRequestBody(json))
        }
    }
}