package com.cyxbs.functions.update.service

import com.cyxbs.functions.update.api.UpdateInfo

/**
 * .
 *
 * @author 985892345
 * @date 2026/9/24
 */
expect object AppUpdatePlatform {

  suspend fun requestUpdateInfo(): UpdateInfo
  fun isNewVersion(info: UpdateInfo): Boolean
  fun clickDownload(downloadUrl: String)
}