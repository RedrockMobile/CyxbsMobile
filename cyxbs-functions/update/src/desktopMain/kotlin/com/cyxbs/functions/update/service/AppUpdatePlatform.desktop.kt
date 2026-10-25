package com.cyxbs.functions.update.service

import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.functions.update.api.UpdateInfo

actual object AppUpdatePlatform {
  actual suspend fun requestUpdateInfo(): UpdateInfo {
    return UpdateInfo(
      apkUrl = "https://github.com/RedrockMobile/CyxbsMobile/releases"
    )
  }

  actual fun isNewVersion(info: UpdateInfo): Boolean {
    return false
  }

  actual fun clickDownload(downloadUrl: String) {
    AppScheme.jump(downloadUrl)
  }
}