package com.cyxbs.pages.qa.home.model.bean

import com.google.gson.annotations.SerializedName

/**
 * description ： Qa返回数据类
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 14:46
 */


data class QaData(

    @SerializedName("info")
    val info: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("items")  // 直接使用 items 字段，不再使用 data
    val items: List<Item>
)

data class Item(
    @SerializedName("CreatedAt")
    val CreatedAt: String,
    @SerializedName("DeleteAt")
    val DeletedAt: Any?,
    @SerializedName("ID")
    val ID: Int,
    @SerializedName("UpdateAt")
    val UpdatedAt: String,
    @SerializedName("a")
    val a: String,
    @SerializedName("a_time")
    val a_time: String,
    @SerializedName("is_like")
    var is_like : Boolean,
    @SerializedName("like_count")
    var like_count: Int,
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
