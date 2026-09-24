package com.cyxbs.functions.update.service

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.functions.update.api.UpdateInfo
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual object AppUpdatePlatform {
  actual suspend fun requestUpdateInfo(): UpdateInfo {
    val response = HttpClientNoToken.get("https://itunes.apple.com/lookup") {
      parameter("id", APP_STORE_ID)
      parameter("country", "cn")
      parameter("entity", "software")
    }
    // Lookup 返回 text/javascript，不能依赖 application/json 的 ContentNegotiation。
    return defaultJson.decodeFromString<AppStoreLookupResult>(response.bodyAsText()).toUpdateInfo()
  }

  actual fun isNewVersion(info: UpdateInfo): Boolean {
    return isNewerAppStoreVersion(
      remote = info.versionName,
      installed = getAppVersionName(),
    )
  }

  actual fun clickDownload(downloadUrl: String) {
    // 该入口只负责掌邮自身更新，始终打开固定的产品页。
    val url = NSURL.URLWithString("itms-apps://apps.apple.com/cn/app/id$APP_STORE_ID") ?: run {
      "无法打开 App Store，请稍后重试".toast()
      return
    }
    UIApplication.sharedApplication.openURL(
      url = url,
      options = emptyMap<Any?, Any>(),
      completionHandler = { success ->
        if (!success) "无法打开 App Store，请稍后重试".toast()
      },
    )
  }

  private fun isNewerAppStoreVersion(remote: String, installed: String): Boolean {
    val remoteParts = remote.split('.').map(String::toInt)
    val installedParts = installed.split('.').map(String::toInt)
    for (index in 0 until maxOf(remoteParts.size, installedParts.size)) {
      val comparison = (remoteParts.getOrNull(index) ?: 0)
        .compareTo(installedParts.getOrNull(index) ?: 0)
      if (comparison != 0) return comparison > 0
    }
    return false
  }
}