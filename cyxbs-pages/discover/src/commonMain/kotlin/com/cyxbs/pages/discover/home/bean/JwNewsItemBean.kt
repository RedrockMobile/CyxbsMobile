package com.cyxbs.pages.discover.home.bean

import kotlinx.serialization.Serializable

/**
 * 教务在线一条新闻
 */
@Serializable
data class JwNewsItemBean(
  val date: String = "",
  val id: String = "",
  val title: String = "",
  val readCount: String = "",
)
