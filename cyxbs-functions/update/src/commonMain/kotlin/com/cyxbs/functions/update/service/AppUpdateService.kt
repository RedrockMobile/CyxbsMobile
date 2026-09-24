package com.cyxbs.functions.update.service

import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.functions.update.api.UpdateInfo
import com.cyxbs.functions.update.dialog.UpdateInfoNavArgument
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

  // 是否用于 mock 更新数据用于测试
  // 这将控制版本的判断逻辑，最后显示为线上的更新信息的弹窗
  private var isMockTestUpdate = false

  private val checker = AppUpdateChecker(
    scope = appCoroutineScope,
    requestInfo = {
      AppUpdatePlatform.requestUpdateInfo()
    },
    isNewVersion = { remoteInfo ->
      AppUpdatePlatform.isNewVersion(remoteInfo) || isMockTestUpdate
    },
  )

  init {
    appCoroutineScope.launch {
      checkUpdate()
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
      isMockTestUpdate = false
    }
  }

  override fun debug() {
    isMockTestUpdate = true
    tryNoticeUpdate(needFrequency = false)
  }
}
