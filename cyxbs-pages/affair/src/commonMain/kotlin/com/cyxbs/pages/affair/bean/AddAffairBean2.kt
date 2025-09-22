package com.cyxbs.pages.affair.bean

import com.cyxbs.components.utils.network.IApiStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
@Serializable
data class AddAffairBean2(
  @SerialName("id")
  val id: Int,
  @SerialName("info")
  override val info: String,
  @SerialName("state")
  val state: Int,
  @SerialName("status")
  override val status: Int
) : IApiStatus