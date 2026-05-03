package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoClassTemporarySearchs(
    @SerialName("class") val `class`: Clss,
    @SerialName("group") val group: NoClassGroups,
    @SerialName("students") val students: List<Students>? = null,
    @SerialName("types") val types: List<String>?
)

@Serializable
data class Clss(
    @SerialName("id") override val id: String,
    @SerialName("members") val members: List<Students>?,
    @SerialName("name") val name: String? = null,
) : NoClassItems, PlatformSerializable
