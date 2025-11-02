package com.cyxbs.functions.update.service

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.utils.get.getAppVersionCode
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.functions.update.api.UpdateInfo
import com.cyxbs.functions.update.dialog.UpdateInfoArgument
import com.cyxbs.functions.update.network.AppUpdateApiService
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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

  private val stateFlow: MutableStateFlow<AppUpdateStatus> = MutableStateFlow(AppUpdateStatus.Checking)

  // 用于 mock 当前处于过期状态，检测能否正常触发更新弹窗
  private var mockDated = false

  init {
    appCoroutineScope.launch {
      checkUpdateInternal()
    }
  }

  override fun getUpdateStatus(): StateFlow<AppUpdateStatus> {
    return stateFlow
  }

  override suspend fun checkUpdate(): AppUpdateStatus.Result {
    if (stateFlow.value == AppUpdateStatus.Checking) {
      // 当前状态处于 CHECKING 状态，则等待请求结果
      return stateFlow.filterIsInstance<AppUpdateStatus.Result>().first()
    }
    return checkUpdateInternal()
  }

  override fun noticeUpdate(newVersion: UpdateInfo) {
    UpdateInfoArgument(
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
    mockDated = true
    tryNoticeUpdate(needFrequency = false)
  }

  private suspend fun checkUpdateInternal(): AppUpdateStatus.Result {
    stateFlow.value = AppUpdateStatus.Checking
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
    }.map {
      val appVersionCode = getAppVersionCode()
      when {
        mockDated -> AppUpdateStatus.Result.Dated(it)
        it.versionCode == appVersionCode -> {
          val name = getAppVersionName()
          if (name != it.versionName) {
            // 名字不相等，说明安装的版本有问题，可能是测试版
            AppUpdateStatus.Result.Dated(it)
          } else AppUpdateStatus.Result.Valid
        }
        it.versionCode < appVersionCode -> {
          AppUpdateStatus.Result.Valid
        }
        else -> AppUpdateStatus.Result.Dated(it)
      }
    }.getOrElse {
      AppUpdateStatus.Result.Error(it)
    }.also {
      stateFlow.value = it
    }
  }
}