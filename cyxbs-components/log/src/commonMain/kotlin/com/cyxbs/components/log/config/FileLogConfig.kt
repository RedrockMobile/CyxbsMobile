package com.cyxbs.components.log

/**
 * 宿主需要决定的文件日志策略。
 *
 * [directory] 与 [exportDirectory] 必须是应用私有或由宿主明确授权的目录，且由当前进程独占；
 * 多进程应用必须为每个进程提供不同目录。业务调用方不应自行拼接或覆盖这两个路径。
 */
data class FileLogConfig(
  val directory: String,
  val exportDirectory: String,
  val minLevel: LogLevel = LogLevel.Info,
  val maxTotalBytes: Long = 24L * 1_024 * 1_024,
  val maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1_000,
) {
  init {
    require(directory.isNotBlank()) { "directory must not be blank" }
    require(exportDirectory.isNotBlank()) { "exportDirectory must not be blank" }
    require(directory != exportDirectory) { "directory and exportDirectory must differ" }
    require(maxTotalBytes >= MIN_TOTAL_BYTES) { "maxTotalBytes must be at least $MIN_TOTAL_BYTES" }
    require(maxAgeMillis > 0) { "maxAgeMillis must be positive" }
  }

  private companion object {
    const val MIN_TOTAL_BYTES = 256L * 1_024
  }
}
