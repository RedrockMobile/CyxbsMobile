package com.cyxbs.pages.map.viewmodel

import com.eygraber.uri.Uri
import com.cyxbs.components.utils.extensions.toast
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openMapNavigation(endPlace: String) {
    val navigationUri = Uri.Builder()
      .scheme("baidumap")
      .authority("map")
      .appendPath("direction")
      .appendQueryParameter("origin", "我的位置")
      .appendQueryParameter("destination", endPlace)
      .appendQueryParameter("mode", "walking")
      .appendQueryParameter("src", "ios.redrock.cyxbs")
      .build()
    val navigationUrl = NSURL.URLWithString(navigationUri.toString()) ?: run {
      "无法打开导航链接".toast()
      return
    }
    UIApplication.sharedApplication.openURL(
      url = navigationUrl,
      options = emptyMap<Any?, Any>(),
      completionHandler = navigation@ { success ->
        if (!success) {
          // 未安装百度地图时，与 Android 一样打开目的地的网页版地图。
          val webUri = Uri.Builder()
            .scheme("https")
            .authority("api.map.baidu.com")
            .appendPath("geocoder")
            .appendQueryParameter("address", endPlace)
            .appendQueryParameter("output", "html")
            .appendQueryParameter("src", "webapp.baidu.openAPIdemo")
            .build()
          val webUrl = NSURL.URLWithString(webUri.toString()) ?: run {
            "无法打开网页版地图".toast()
            return@navigation
          }
          UIApplication.sharedApplication.openURL(
            url = webUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
          )
        }
      },
    )
  }
