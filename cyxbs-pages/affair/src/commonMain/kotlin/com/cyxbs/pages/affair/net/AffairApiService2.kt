package com.cyxbs.pages.affair.net

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApiStatus
import com.cyxbs.pages.affair.bean.AffairBean2
import com.cyxbs.pages.affair.bean.AffairWhenBean
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
interface AffairApiService2 {

  @POST("magipoke-reminder/Person/addTimeTransaction")
  @Headers("Content-Type: application/json")
  suspend fun addAffair(
    @Body request: AddAffairRequest,
  ): ApiWrapper<AddAffairResponse>

  @POST("magipoke-reminder/Person/getTimeTransaction")
  @Headers("App-Version:74")
  suspend fun getAffair(): ApiWrapper<GetAffairResponse>

  @POST("magipoke-reminder/Person/editTimeTransaction")
  @Headers("Content-Type: application/json")
  suspend fun updateAffair(
    @Body request: UpdateAffairRequest,
  ): ApiStatus

  @POST("magipoke-reminder/Person/deleteTimeTransaction")
  @FormUrlEncoded
  @Headers("App-Version:74")
  suspend fun deleteAffair(
    @Field("id")
    remoteId: Int
  ): ApiStatus
}

@Serializable
data class GetAffairResponse(
  @SerialName("data")
  val data: List<AffairBean2>,
)

@Serializable
data class AddAffairRequest(
  @SerialName("time")
  val remindTime: Int, // 提醒时间
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("when")
  val whenList: List<AffairWhenBean>, // 时间段
)

@Serializable
data class AddAffairResponse(
  @SerialName("id")
  val id: Int,
)

@Serializable
data class UpdateAffairRequest(
  @SerialName("id")
  val remoteId: Int,
  @SerialName("time")
  val remindTime: Int, // 提醒时间
  @SerialName("title")
  val title: String,
  @SerialName("content")
  val content: String,
  @SerialName("when")
  val whenList: List<AffairWhenBean>, // 时间段
)
