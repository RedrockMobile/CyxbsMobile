package com.cyxbs.functions.update.service

import com.cyxbs.components.config.Platform
import com.cyxbs.components.config.appPlatform
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.utils.get.getAppVersionCode
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.functions.update.api.UpdateInfo
import com.cyxbs.functions.update.dialog.UpdateInfoNavArgument
import com.cyxbs.functions.update.network.AppUpdateApiService
import com.cyxbs.functions.update.network.getAppStoreUpdateInfo
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/2
 */
@ImplProvider
object AppUpdateService : IAppUpdateService {

  private val checker = AppUpdateChecker(
    scope = appCoroutineScope,
    requestInfo = {
      if (appPlatform == Platform.IOS) getAppStoreUpdateInfo() else getAndroidUpdateInfo()
    },
    isNewVersion = { remoteInfo ->
      isUpdateAvailable(
        platform = appPlatform,
        remoteInfo = remoteInfo,
        installedVersionName = getAppVersionName(),
        installedVersionCode = getAppVersionCode(),
      )
    },
  )

  init {
    // iOS 由关于页触发检查，避免首次进入时与初始化请求重复。
    if (appPlatform != Platform.IOS) {
      appCoroutineScope.launch { checkUpdate() }
    }
  }

  override fun getUpdateStatus(): StateFlow<AppUpdateStatus> = checker.status
  override fun getUpdateInfo(): StateFlow<UpdateInfo?> = checker.info
  override suspend fun checkUpdate(): AppUpdateStatus.Result = checker.checkUpdate()

  override fun noticeUpdate(newVersion: UpdateInfo) {
    UpdateInfoNavArgument(
      versionName = newVersion.versionName,
      updateContent = newVersion.updateContent,
      downloadUrl = newVersion.apkUrl,
    ).navigate()
  }

  override fun tryNoticeUpdate(needFrequency: Boolean) {
    val nowTime = Clock.System.now().toEpochMilliseconds().milliseconds
    if (needFrequency) {
      val lastNoticeTime = defaultSettings.getLong("上次提醒更新时间", 0L).milliseconds
      if (nowTime - lastNoticeTime < 12.hours) return // 如果有更新，则每隔 12 个小时提醒一次更新
    }
    appCoroutineScope.launch(Dispatchers.Main) {
      val status = checkUpdate() as? AppUpdateStatus.Result.Dated ?: return@launch
      noticeUpdate(status.newVersion)
      defaultSettings.putLong("上次提醒更新时间", nowTime.inWholeMilliseconds)
    }
  }

  override fun debug() {
    appCoroutineScope.launch(Dispatchers.Main.immediate) {
      val info = checker.checkPreviewInfo()
      if (info != null) noticeUpdate(info) else "检查更新失败，请稍后重试".toast()
    }
  }

  private suspend fun getAndroidUpdateInfo(): UpdateInfo {
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
}
