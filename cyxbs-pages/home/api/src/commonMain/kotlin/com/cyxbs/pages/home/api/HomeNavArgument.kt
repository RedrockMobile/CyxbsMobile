package com.cyxbs.pages.home.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 主页的路由参数
 */
@Serializable
data class HomeNavArgument(
  @SerialName("page")
  val page: String = "discover", // discover、fairground、mine
) : AppNavArgument