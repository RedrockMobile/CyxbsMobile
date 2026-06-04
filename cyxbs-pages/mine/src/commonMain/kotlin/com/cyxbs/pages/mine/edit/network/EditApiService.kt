package com.cyxbs.pages.mine.edit.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.mine.user.bean.DownMessageBean
import com.cyxbs.pages.mine.user.bean.DownMessageParams
import com.cyxbs.pages.mine.user.bean.UploadAvatarBean
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import io.ktor.client.request.forms.MultiPartFormDataContent

/**
 * .
 *
 * @author 985892345
 * @date 2026/6/4
 */
interface EditApiService {


  /**
   * 修改个人信息
   *
   * form-data 字段：nickname / photo_src / introduction / phone / qq / gender / birthday，
   * 均可选，未传字段保持后端原值。
   */
  @PUT("magipoke/person/info")
  suspend fun updatePersonInfo(@Body body: MultiPartFormDataContent): ApiStatus

  /**
   * 上传头像，返回新的 photo_src URL
   *
   * form-data 字段：stunum + fold（图片文件）。
   * 拿到 [UploadAvatarBean.photoSrc] 后需要再调一次 [updatePersonInfo] 把 URL 写回个人信息。
   */
  @PUT("magipoke/person/upload/avatar")
  suspend fun uploadAvatar(@Body body: MultiPartFormDataContent): ApiWrapper<UploadAvatarBean>

  /**
   * 通用下发文本（headers 必须显式指定 application/json）。
   *
   * 用于「头像使用协议」等下发型说明文案，传入 [DownMessageParams] 的 name 作为下发 key。
   */
  @Headers("Content-Type: application/json")
  @POST("magipoke-text/text/get")
  suspend fun getDownMessage(@Body params: DownMessageParams): ApiWrapper<DownMessageBean>
}