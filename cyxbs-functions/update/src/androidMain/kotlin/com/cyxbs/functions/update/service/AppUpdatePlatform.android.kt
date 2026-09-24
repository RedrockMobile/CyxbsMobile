package com.cyxbs.functions.update.service

import android.content.Intent
import androidx.core.net.toUri
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.utils.get.getAppVersionCode
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.functions.update.api.UpdateInfo

actual object AppUpdatePlatform {
  actual suspend fun requestUpdateInfo(): UpdateInfo {
    val apiService = AppUpdateApiService::class.impl()
    return runCatching {
      apiService.getUpdateInfo()
    }.recoverCatching {
      // 兜底使用 github release 更新，但需要发版时需要遵循格式：vX.X.X-X
      val githubUpdateInfo = apiService.getUpdateInfoByGithub()
      if (githubUpdateInfo.tag.matches("v\\d+\\.\\d+\\.\\d+-\\d+".toRegex())){
        val strings = githubUpdateInfo.tag.split("-")
        val versionName = strings[0].removeRange(0,1)
        val versionCode = strings[1].toLong()
        UpdateInfo(
          apkUrl = githubUpdateInfo.assets.first().downloadUrl,
          updateContent = githubUpdateInfo.body,
          versionCode = versionCode,
          versionName = versionName,
        )
      }
      throw it
    }.getOrThrow()
  }

  actual fun isNewVersion(info: UpdateInfo): Boolean {
    val appVersionCode = getAppVersionCode()
    return when {
      info.versionCode == appVersionCode -> {
        val name = getAppVersionName()
        // 名字不相等，说明安装的版本有问题，可能是测试版
        name != info.versionName
      }
      info.versionCode < appVersionCode -> false
      else -> true
    }
  }

  actual fun clickDownload(downloadUrl: String) {
    /*
    * 22-8-30
    * 因为应用内更新有很多毛病，所以采用浏览器下载
    * */
    runCatching {
      appTopActivity.get()?.startActivity(
        Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
      )
    }.onFailure {
      // 如果 activity 找不到，可能链接存在问题，重定向到官网
      appTopActivity.get()?.startActivity(
        Intent(Intent.ACTION_VIEW, "https://app.redrock.team/#/download".toUri())
      )
    }
  }
}