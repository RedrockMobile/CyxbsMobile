package com.cyxbs.functions.update.service

import com.cyxbs.functions.update.api.UpdateInfo
import com.eygraber.uri.Uri
import kotlinx.serialization.Serializable

internal const val APP_STORE_ID = 974026615L
internal const val APP_STORE_BUNDLE_ID = "com.mredrock.cyxbs"

@Serializable
internal data class AppStoreLookupResult(
  val results: List<AppStoreUpdateInfo> = emptyList(),
) {
  fun toUpdateInfo(): UpdateInfo {
    val app = results.singleOrNull {
      it.trackId == APP_STORE_ID && it.bundleId == APP_STORE_BUNDLE_ID
    } ?: error("App Store 未返回掌上重邮的版本信息")
    val url = Uri.parse(app.trackViewUrl)
    require(url.scheme == "https" && url.host == "apps.apple.com") {
      "App Store 返回了无效的产品页地址"
    }
    require(app.version.isNotBlank()) { "App Store 未返回版本号" }
    return UpdateInfo(
      apkUrl = app.trackViewUrl,
      versionName = app.version,
      updateContent = buildString {
        append(app.releaseNotes.ifBlank { "暂无更新说明" })
        if (app.minimumOsVersion.isNotBlank()) {
          append("\n\n最低系统版本：iOS ${app.minimumOsVersion}")
        }
      },
    )
  }
}

@Serializable
internal data class AppStoreUpdateInfo(
  val trackId: Long,
  val bundleId: String,
  val version: String,
  val trackViewUrl: String,
  val releaseNotes: String = "",
  val minimumOsVersion: String = "",
)
