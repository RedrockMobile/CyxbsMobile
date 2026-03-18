package com.cyxbs.pages.emptyroom.network

import com.cyxbs.components.utils.network.ApiWrapper
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST

/**
 * description ：空教室的api接口
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/3/3 16:43
 */
interface EmptyRoomApiService {

    @FormUrlEncoded
    @POST("magipoke-jwzx/roomEmpty")
    suspend fun getEmpyRooms(
        @Field("weekDayNum") weekday: String,
        @Field("sectionNum") section: String,
        @Field("buildNum") buildNum: String,
        @Field("week") week: String
    ): ApiWrapper<List<String>>

}