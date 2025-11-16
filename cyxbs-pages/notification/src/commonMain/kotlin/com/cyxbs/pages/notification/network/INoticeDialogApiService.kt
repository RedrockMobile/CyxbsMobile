package com.cyxbs.pages.notification.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.notification.bean.NoticeDialogBean
import de.jensklingenberg.ktorfit.http.GET
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 接口文档：https://rbtdi8ocgh.feishu.cn/docx/Dq6vdhQl2oW4UTxekS6cwInynrg
 *
 * @author 985892345
 * @date 2025/10/20
 */
interface INoticeDialogApiService {

  @GET("magipoke/person/message")
  suspend fun getNoticeDialog(): ApiWrapper<NoticeDialogResponse>
}

@Serializable
class NoticeDialogResponse(
  @SerialName("notice")
  val notice: List<NoticeDialogBean>
)