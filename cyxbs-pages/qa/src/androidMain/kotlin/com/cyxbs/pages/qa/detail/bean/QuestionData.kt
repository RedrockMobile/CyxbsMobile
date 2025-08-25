package com.cyxbs.pages.qa.detail.bean

import com.google.gson.annotations.SerializedName

data class QuestionData(
    @SerializedName("item")
    val item: QuestionItem
)

data class QuestionItem(
    @SerializedName("CreatedAt")
    val createdAt: String,
    @SerializedName("ID")
    val iD: Int,
    @SerializedName("UpdatedAt")
    val updatedAt: String,
    @SerializedName("a")
    val a: String,
    @SerializedName("a_time")
    val aTime: String,
    @SerializedName("is_like")
    val isLike: Boolean,
    @SerializedName("like_count")
    val likeCount: Long,
    @SerializedName("q")
    val q: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("stu_num")
    val stuNum: String,
    @SerializedName("tags")
    val tags: String,
    @SerializedName("view_count")
    val viewCount: Long
)