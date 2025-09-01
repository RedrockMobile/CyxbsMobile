package com.cyxbs.pages.qa.utils

import java.util.Locale

/**
 * description ： 在QA模块中的一些关于String类型的工具函数
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/22 17:36
 */
//以防数字太大，导致显示不完整
fun longToWanString(num: Long): String{
    return when{
        num > 9999999L -> "999.9w+"
        num >= 10000L -> String.format(Locale.CHINA, "%.1fw", num / 10_000.0)
        else -> num.toString()
    }
}

fun String.truncateWithEllipsis(maxLength: Int = 12): String {
    return if (this.length > maxLength) {
        this.substring(0, maxLength) + "…"
    } else {
        this
    }
}