package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import java.io.Serializable

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/19
 * @Description:
 *
 */
@kotlinx.serialization.Serializable
data class NoClassBatchResponseInfo(
    @SerialName("isWrong")
    val isWrong : Boolean,
    @SerialName("errList")
    val errList: List<String>,
    @SerialName("normal")
    val normal: List<Normal>? = null,
    @SerialName("repeat")
    val repeat: List<Student>? = null
): Serializable {
    @kotlinx.serialization.Serializable
    data class Normal(
        @SerialName("stu_num")
        val id: String,
        @SerialName("real_name")
        val name:String
    )
    @kotlinx.serialization.Serializable
    data class Student(
        @SerialName("stunum")
        val id: String,             // 学号
        @SerialName("name")
        val name:String,            // 姓名
        @SerialName("major")
        val major: String,          // 专业名称
        @SerialName("depart")
        val depart: String,         // 学院名称
        @SerialName("classnum")
        val classNum: String,       // 班级号
        var isSelected: Boolean = false
    )
}