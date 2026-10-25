package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/** ICS 保存对话框的稳定结果；取消不属于错误，调用方不应弹失败提示。 */
internal sealed interface ScheduleIcsFileSaveResult {
  /** 文件已经导出；[displayMessage] 是平台生成的完整提示语，不参与后续文件访问。 */
  data class Saved(val displayMessage: String) : ScheduleIcsFileSaveResult
  data object Cancelled : ScheduleIcsFileSaveResult
  data class Failed(val cause: Throwable) : ScheduleIcsFileSaveResult
  data object Unsupported : ScheduleIcsFileSaveResult
}

/**
 * 设置页持有的平台文件保存入口。
 *
 * [isSupported] 供不具备保存能力的平台直接禁用 UI；[launch] 只接收已经生成的文本，不读取账号或仓库。
 */
@Stable
internal class ScheduleIcsFileSaveLauncher(
  val isSupported: Boolean,
  private val onLaunch: (fileName: String, content: String) -> Unit,
) {
  /** 打开平台保存对话框；文件名必须以 .ics 结尾。 */
  fun launch(fileName: String, content: String) {
    require(fileName.endsWith(".ics", ignoreCase = true)) { "ICS file name must end with .ics" }
    onLaunch(fileName, content)
  }
}

/** 记住平台文件保存入口，并把最终保存、取消或失败结果返回设置页。 */
@Composable
internal expect fun rememberScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher
