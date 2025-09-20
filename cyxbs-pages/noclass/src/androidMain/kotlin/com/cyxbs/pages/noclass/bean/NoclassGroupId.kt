package com.cyxbs.pages.noclass.bean

import kotlinx.serialization.SerialName
import java.io.Serializable

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.bean
 * @ClassName:      NoclassGroupId
 * @Author:         Yan
 * @CreateDate:     2022年08月27日 20:50:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description:
 */
@kotlinx.serialization.Serializable
data class NoclassGroupId(
    @SerialName("group_id")
    val id : Int
) : Serializable