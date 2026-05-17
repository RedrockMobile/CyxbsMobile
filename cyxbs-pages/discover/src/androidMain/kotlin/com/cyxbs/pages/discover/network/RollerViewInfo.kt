package com.cyxbs.pages.discover.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Created by zxzhu
 *   2018/9/7.
 *   enjoy it !!
 */

/**
 * picture_url : http://img.taopic.com/uploads/allimg/120727/201995-120HG1030762.jpg
 * picture_goto_url : www.baidu.com
 * keyword : test
 */
data class RollerViewInfo(
    @SerializedName("picture_url")
    val pictureUrl: String,
    @SerializedName("picture_goto_url")
    val pictureGotoUrl: String,
    @SerializedName("keyword")
    val keyword: String,
) : Serializable