package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoClassGroups(
    override val id: String,
    @SerialName("is_top") val isTop: Boolean = false,
    @SerialName("members") var members: List<Students>? = null,
    @SerialName("name") val name: String,
    var isOpen: Boolean = false,
) : NoClassItems, PlatformSerializable
