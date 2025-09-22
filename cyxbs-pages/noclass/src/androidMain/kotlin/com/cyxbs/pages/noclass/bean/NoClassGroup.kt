package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import java.io.Serializable

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.bean
 * @ClassName:      Group
 * @Author:         Yan
 * @CreateDate:     2022年08月18日 17:56:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description:    每个分组的bean类
 */

@kotlinx.serialization.Serializable
data class NoClassGroup(
    @SerialName("id")
    override val id: String,
    @SerialName("is_top")
    val isTop: Boolean = false,
    @SerialName("members")
    var members: List<Student>? = null,
    @SerialName("name")
    val name: String,

    var isOpen : Boolean = false
) : Serializable, NoClassItem