package com.cyxbs.pages.qa.detail.bean

import com.google.gson.annotations.SerializedName

data class DetailBean(
    @SerializedName("data")
    val data: Data,
    @SerializedName("info")
    val info: String,
    @SerializedName("status")
    val status: Int
)