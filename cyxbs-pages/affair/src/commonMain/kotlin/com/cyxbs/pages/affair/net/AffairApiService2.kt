package com.cyxbs.pages.affair.net

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.pages.affair.bean.AddAffairBean2
import com.cyxbs.pages.affair.bean.GetAffairBean
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
interface AffairApiService2 {

  @POST("magipoke-reminder/Person/addTransaction")
  @FormUrlEncoded
  @Headers("App-Version:74")
  suspend fun addAffair(
    @Field("time")
    time: Int, // 提醒时间
    @Field("title")
    title: String,
    @Field("content")
    content: String,
    @Field("date")
    dateJson: String // 为 json 序列化后的 string，结构详看 AffairDateBean
  ): AddAffairBean2

  @POST("magipoke-reminder/Person/getTransaction")
  @Headers("App-Version:74")
  suspend fun getAffair(): GetAffairBean

  @POST("magipoke-reminder/Person/editTransaction")
  @FormUrlEncoded
  @Headers("App-Version:74")
  suspend fun updateAffair(
    @Field("id")
    remoteId: Int,
    @Field("time")
    time: Int,
    @Field("title")
    title: String,
    @Field("content")
    content: String,
    @Field("date")
    dateJson: String
  ): ApiStatus

  @POST("magipoke-reminder/Person/deleteTransaction")
  @FormUrlEncoded
  @Headers("App-Version:74")
  suspend fun deleteAffair(
    @Field("id")
    remoteId: Int
  ): ApiStatus
}