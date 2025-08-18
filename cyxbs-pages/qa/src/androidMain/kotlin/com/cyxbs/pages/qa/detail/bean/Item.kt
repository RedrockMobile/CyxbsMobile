package com.cyxbs.pages.qa.detail.bean

import com.google.gson.annotations.SerializedName

data class Item(
    @SerializedName("CreatedAt")
    val CreatedAt: String,
    @SerializedName("ID")
    val ID: Int,
    @SerializedName("UpdatedAt")
    val UpdatedAt: String,
    @SerializedName("a")
    val a: String,
    @SerializedName("a_time")
    val a_time: String,
    @SerializedName("is_like")
    val is_like: Boolean,
    @SerializedName("like_count")
    val like_count: Int,
    @SerializedName("q")
    val q: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("stu_num")
    val stu_num: String,
    @SerializedName("tags")
    val tags: String,
    @SerializedName("view_count")
    val view_count: Int
)