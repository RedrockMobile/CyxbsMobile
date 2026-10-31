package com.cyxbs.components.config.log

import com.cyxbs.components.log.FileLogConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSURLFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformFileLogConfig(): FileLogConfig? {
  val support = NSSearchPathForDirectoriesInDomains(
    NSApplicationSupportDirectory,
    NSUserDomainMask,
    true,
  ).firstOrNull() as? String ?: return null
  val caches = NSSearchPathForDirectoriesInDomains(
    NSCachesDirectory,
    NSUserDomainMask,
    true,
  ).firstOrNull() as? String ?: return null
  val logDirectory = "$support/cyxbs-log"
  val exportDirectory = "$caches/cyxbs-log-export"
  return FileLogConfig(
    directory = logDirectory,
    exportDirectory = exportDirectory,
  )
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun preparePlatformFileLog(config: FileLogConfig) {
  withContext(Dispatchers.Default) {
    prepareProtectedDirectory(config.directory, excludedFromBackup = true)
    prepareProtectedDirectory(config.exportDirectory, excludedFromBackup = false)
  }
}

internal actual fun setPlatformDiagnosticCollectionEnabled(
  enabled: Boolean,
  expectedPolicyVersion: Long?,
) {
  if (enabled && expectedPolicyVersion != null) {
    MetricKitDiagnostics.start(expectedPolicyVersion)
  } else {
    MetricKitDiagnostics.stop()
  }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun onPlatformFileLogStarted(config: FileLogConfig) {
  withContext(Dispatchers.Default) {
    applyDirectoryMetadata(config.directory, excludedFromBackup = true)
    applyDirectoryMetadata(config.exportDirectory, excludedFromBackup = false)
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun prepareProtectedDirectory(path: String, excludedFromBackup: Boolean) {
  val fileManager = NSFileManager.defaultManager
  if (!fileManager.fileExistsAtPath(path)) {
    val created = memScoped {
      val failure = alloc<ObjCObjectVar<NSError?>>()
      failure.value = null
      fileManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = failure.ptr,
      ).also { success ->
        if (!success) throw platformFileFailure("create directory", failure.value)
      }
    }
    check(created) { "iOS log directory creation failed" }
  }
  applyFileProtection(path)
  if (excludedFromBackup) applyBackupExclusion(path)
}

@OptIn(ExperimentalForeignApi::class)
private fun applyDirectoryMetadata(path: String, excludedFromBackup: Boolean) {
  val children = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    failure.value = null
    NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error = failure.ptr)
      ?: throw platformFileFailure("list directory", failure.value)
  }.filterIsInstance<String>()
  (children.map { "$path/$it" } + path).forEach { managedPath ->
    applyFileProtection(managedPath)
    if (excludedFromBackup) applyBackupExclusion(managedPath)
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun applyFileProtection(path: String) {
  setResourceValueOrThrow(
    path = path,
    value = requireNotNull(NSURLFileProtectionCompleteUntilFirstUserAuthentication),
    key = requireNotNull(NSURLFileProtectionKey),
    operation = "apply file protection",
  )
}

@OptIn(ExperimentalForeignApi::class)
private fun applyBackupExclusion(path: String) {
  setResourceValueOrThrow(
    path = path,
    value = true,
    key = requireNotNull(NSURLIsExcludedFromBackupKey),
    operation = "exclude from backup",
  )
}

@OptIn(ExperimentalForeignApi::class)
private fun setResourceValueOrThrow(
  path: String,
  value: Any,
  key: String,
  operation: String,
) {
  val url = NSURL.fileURLWithPath(path)
  memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    failure.value = null
    val success = url.setResourceValue(
      value = value,
      forKey = key,
      error = failure.ptr,
    )
    if (!success) throw platformFileFailure(operation, failure.value)
  }
  val actualValue = memScoped {
    val resourceValue = alloc<ObjCObjectVar<Any?>>()
    val failure = alloc<ObjCObjectVar<NSError?>>()
    resourceValue.value = null
    failure.value = null
    val success = url.getResourceValue(
      value = resourceValue.ptr,
      forKey = key,
      error = failure.ptr,
    )
    if (!success) throw platformFileFailure("verify $operation", failure.value)
    resourceValue.value
  }
  val matches = when (value) {
    is Boolean -> actualValue == value || (actualValue as? NSNumber)?.boolValue == value
    else -> actualValue == value
  }
  check(matches) { "iOS log storage failed to verify $operation" }
}

private fun platformFileFailure(operation: String, failure: NSError?): IllegalStateException =
  IllegalStateException("iOS log storage failed to $operation: ${failure?.localizedDescription ?: "unknown error"}")

internal actual fun installPlatformLogLifecycle(onBackground: () -> Unit) {
  NSNotificationCenter.defaultCenter.addObserverForName(
    name = UIApplicationDidEnterBackgroundNotification,
    `object` = null,
    queue = null,
  ) { onBackground() }
}
