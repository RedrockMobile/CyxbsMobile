package com.cyxbs.functions.update.service

import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.update.api.UpdateInfo

actual object AppUpdatePlatform {
  actual suspend fun requestUpdateInfo(): UpdateInfo {
    return UpdateInfo()
  }

  actual fun isNewVersion(info: UpdateInfo): Boolean {
    return false
  }

  actual fun clickDownload(downloadUrl: String) {
    toast("当前平台暂不支持更新")
  }
}