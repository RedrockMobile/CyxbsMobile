package com.cyxbs.pages.noclass.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 分组详情页的导航参数
 * @author summer_palace2
 * @date 2026/5/3
 */
@Serializable
class GroupDetailArgument(
    @SerialName("group_id")
    val groupId: String = "",
    @SerialName("group_name")
    val groupName: String = "",
)
