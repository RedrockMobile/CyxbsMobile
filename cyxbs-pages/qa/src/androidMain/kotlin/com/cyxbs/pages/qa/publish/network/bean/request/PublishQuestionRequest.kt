package com.cyxbs.pages.qa.publish.network.bean.request

import com.google.gson.annotations.SerializedName

/**
 * description ：发布问题的请求体数据类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 18:37
 */
/**
 * 发布问题接口请求体
 *
 * @param q 问题内容
 * @param tags 标签 (产品该改需求了，现在只支持单标签)
 */
data class PublishQuestionRequest(
    @SerializedName("q")
    val q: String,
    @SerializedName("tags")
    val tags: String
)