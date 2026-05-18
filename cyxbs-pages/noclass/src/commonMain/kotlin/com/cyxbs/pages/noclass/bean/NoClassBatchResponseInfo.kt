package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoClassBatchResponseInfo(
    @SerialName("isWrong") val isWrong: Boolean,
    @SerialName("errList") val errList: List<String>,
    @SerialName("normal") val normal: List<Normal>? = null,
    @SerialName("repeat") val repeat: List<BatchStudent>? = null
) {
    @Serializable
    data class Normal(
        @SerialName("stu_num") val id: String,
        @SerialName("real_name") val name: String
    )

    @Serializable
    data class BatchStudent(
        @SerialName("stunum") val id: String,
        @SerialName("name") val name: String,
        @SerialName("major") val major: String,
        @SerialName("depart") val depart: String,
        @SerialName("classnum") val classNum: String,
        var isSelected: Boolean = false
    )
}
