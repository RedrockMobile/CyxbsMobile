package com.cyxbs.pages.qa.publish.network.bean.response

import com.google.gson.annotations.SerializedName

/**
 * description ： 搜索的单条数据
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 18:57
 */

data class SearchBean(
    @SerializedName("items")
    val items: List<SearchData>?
)

data class SearchData(
    @SerializedName("a")
    val a: String,
    @SerializedName("a_time")
    val aTime: String,
    @SerializedName("CreatedAt")
    val createdAt: String,
    @SerializedName("ID")
    val id: Long,
    @SerializedName("like_count")
    val likeCount: Long,
    @SerializedName("q")
    val q: String,
    @SerializedName("status")
    val status: Long,
    @SerializedName("stu_num")
    val stuNum: String,
    @SerializedName("tags")
    val tags: String,
    @SerializedName("UpdatedAt")
    val updatedAt: String,
    @SerializedName("view_count")
    val viewCount: Long,
    @SerializedName("is_like")
    val isLike: Boolean
)



