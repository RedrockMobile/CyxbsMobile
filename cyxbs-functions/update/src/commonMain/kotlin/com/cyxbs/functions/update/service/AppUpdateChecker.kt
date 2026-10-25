package com.cyxbs.functions.update.service

import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AppUpdateChecker(
  private val scope: CoroutineScope,
  private val requestInfo: suspend () -> UpdateInfo,
  private val isNewVersion: (UpdateInfo) -> Boolean,
) {
  private val mutableStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Checking)
  private val mutableInfo = MutableStateFlow<UpdateInfo?>(null)
  val status = mutableStatus.asStateFlow()
  val info = mutableInfo.asStateFlow()

  private val mutex = Mutex()
  private var request: Deferred<CheckResult>? = null

  private data class CheckResult(val status: AppUpdateStatus.Result, val info: UpdateInfo?)

  suspend fun checkUpdate(): AppUpdateStatus.Result = check().status

  private suspend fun check(): CheckResult {
    // 页面离开只取消等待者；共享请求仍会完成，避免状态永久停在 Checking。
    val deferred = mutex.withLock {
      request?.takeUnless { it.isCompleted } ?: scope.async {
        mutableStatus.value = AppUpdateStatus.Checking
        mutableInfo.value = null
        val result = try {
          val updateInfo = requestInfo()
          val outdated = isNewVersion(updateInfo)
          mutableInfo.value = updateInfo
          val status = if (outdated) AppUpdateStatus.Result.Dated(updateInfo) else AppUpdateStatus.Result.Valid
          CheckResult(status, updateInfo)
        } catch (error: CancellationException) {
          mutableStatus.value = AppUpdateStatus.Result.Error(error)
          throw error
        } catch (error: Exception) {
          CheckResult(AppUpdateStatus.Result.Error(error), null)
        }
        mutableStatus.value = result.status
        result
      }.also { request = it }
    }
    return deferred.await()
  }
}
