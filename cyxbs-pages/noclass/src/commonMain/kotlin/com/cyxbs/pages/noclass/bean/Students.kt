package com.cyxbs.pages.noclass.bean

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Students(
    @SerialName("classnum") val classNum: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("grade") val grade: String? = null,
    @SerialName("major") val major: String? = null,
    @SerialName("depart") val depart: String? = null,
    @SerialName("name") val name1: String? = null,
    @SerialName("stu_name") val name2: String? = null,
    @SerialName("stunum") val stunum1: String? = null,
    @SerialName("stu_num") val stunum2: String? = null,
) : NoClassItems, PlatformSerializable {
    val name: String get() = name1 ?: name2!!
    override val id: String get() = stunum1 ?: stunum2!!
}
