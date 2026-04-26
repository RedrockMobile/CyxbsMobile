package com.cyxbs.components.utils.utils.get

import CyxbsMobile.cyxbs_components.utils.BuildConfig

/**
 * @Desc : App信息
 * @Author : zzx
 * @Date : 2025/10/29 15:24
 */

fun getAppVersionCode(): Long {
  return BuildConfig.VERSION_CODE
}

fun getAppVersionName(): String {
  return BuildConfig.VERSION_NAME
}

fun getAppUpdateContent(): String {
  return BuildConfig.VERSION_UPDATE_CONTENT
}