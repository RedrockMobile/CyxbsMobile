@file:OptIn(ExperimentalForeignApi::class)

package com.cyxbs.components.config.log

import com.cyxbs.components.log.CyxbsLog
import com.cyxbs.components.log.LogLevel
import com.cyxbs.components.log.LogContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSLock
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.MetricKit.MXDiagnosticPayload
import platform.MetricKit.MXMetricManager
import platform.MetricKit.MXMetricManagerSubscriberProtocol
import platform.darwin.NSObject

internal object MetricKitDiagnostics {
  private val lock = NSLock()
  private var subscriber: MetricKitDiagnosticSubscriber? = null

  fun start(expectedPolicyVersion: Long) {
    if (!supportsDiagnosticPayloads()) return
    AppLogManager.withCurrentDiagnosticCollectionPolicy(expectedPolicyVersion) {
      lock.lock()
      try {
        if (subscriber?.policyVersion == expectedPolicyVersion) return@withCurrentDiagnosticCollectionPolicy
        subscriber?.let(MXMetricManager.sharedManager::removeSubscriber)
        val newSubscriber = MetricKitDiagnosticSubscriber(expectedPolicyVersion)
        MXMetricManager.sharedManager.addSubscriber(newSubscriber)
        subscriber = newSubscriber
      } finally {
        lock.unlock()
      }
    }
  }

  fun stop() {
    lock.lock()
    try {
      val currentSubscriber = subscriber ?: return
      MXMetricManager.sharedManager.removeSubscriber(currentSubscriber)
      subscriber = null
    } finally {
      lock.unlock()
    }
  }

  private fun supportsDiagnosticPayloads(): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
      cValue<NSOperatingSystemVersion> {
        majorVersion = 14
        minorVersion = 0
        patchVersion = 0
      }
    )
}

private class MetricKitDiagnosticSubscriber(
  val policyVersion: Long,
) : NSObject(), MXMetricManagerSubscriberProtocol {
  override fun didReceiveDiagnosticPayloads(payloads: List<*>) {
    AppLogManager.withCurrentDiagnosticCollectionPolicy(policyVersion) {
      val diagnostics = payloads.filterIsInstance<MXDiagnosticPayload>()
      if (diagnostics.isEmpty()) return@withCurrentDiagnosticCollectionPolicy

      var crashCount = 0L
      var hangCount = 0L
      var cpuCount = 0L
      var diskCount = 0L
      diagnostics.forEach { payload ->
        crashCount += payload.crashDiagnostics?.size?.toLong() ?: 0L
        hangCount += payload.hangDiagnostics?.size?.toLong() ?: 0L
        cpuCount += payload.cpuExceptionDiagnostics?.size?.toLong() ?: 0L
        diskCount += payload.diskWriteExceptionDiagnostics?.size?.toLong() ?: 0L
      }

      CyxbsLog.log(
        level = LogLevel.Info,
        tag = "MetricKitDiagnostics",
        context = LogContext(operation = "ios.metric_kit_diagnostics"),
      ) {
        "MetricKit diagnostics: crash=$crashCount, hang=$hangCount, " +
          "cpu=$cpuCount, disk=$diskCount"
      }
    }
  }
}
