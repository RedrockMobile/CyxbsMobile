package com.cyxbs.components.utils.utils.get

import com.cyxbs.components.utils.BuildConfig

/**
 * 获取版本号
 */
fun getAppVersionCode(): Long {
    return BuildConfig.VERSION_CODE
}

/**
 * 获取版本名字
 */
fun getAppVersionName(): String {
    return BuildConfig.VERSION_NAME
}

/**
 * 获取版本更新内容
 */
fun getAppUpdateContent(): String {
    return BuildConfig.VERSION_UPDATE_CONTENT
}