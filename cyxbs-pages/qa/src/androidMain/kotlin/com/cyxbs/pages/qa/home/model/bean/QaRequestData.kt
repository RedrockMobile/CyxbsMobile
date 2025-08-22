package com.cyxbs.pages.qa.home.model.bean

import com.google.gson.annotations.SerializedName

/**
 * description ： Qa获取主页数据的body
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 15:09
 */
data class QaRequestData(
    @SerializedName("page")
    val page: Int,
    @SerializedName("page_size")
    val page_size: Int,
    @SerializedName("tags")
    val tags: String
)