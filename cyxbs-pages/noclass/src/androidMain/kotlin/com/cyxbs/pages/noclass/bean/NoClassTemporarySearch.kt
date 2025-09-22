package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import java.io.Serializable


/**
 * 没课约临时分组搜索结果
 */
@kotlinx.serialization.Serializable
data class NoClassTemporarySearch(
    @SerialName("class")
    val `class`: Cls,
    @SerialName("group")
    val group: NoClassGroup,
    @SerialName("students")
    val students: List<Student>? = null,
    @SerialName("types")
    val types: List<String>?
) : Serializable

const val STUDENT_TYPE = "学生"
const val CLASS_TYPE = "班级"
const val GROUP_TYPE = "分组"

interface NoClassItem {
    val id: String
}

@kotlinx.serialization.Serializable
data class Cls(
    @SerialName("id")
    override val id: String,
    @SerialName("members")
    val members: List<Student>?,
    @SerialName("name")
    val name: String? = null,
) : Serializable, NoClassItem

