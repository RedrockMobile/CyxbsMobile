package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.DynamicLanguageIconCache
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * 为一个动态语言 Service 透明补充按实际 npm 版本失效的图标缓存。
 *
 * 除 [fileIcon] 外的所有能力和 [close][DynamicLanguageService.close] 都直接委托给底层 Service。
 * 图标只在业务首次请求时读取：缓存版本一致时不进入 JavaScript；首次请求或版本变化时调用底层
 * 接口并持久化。互斥锁保证同一会话并发请求图标时最多触发一次底层调用。
 */
internal class IconCachingDynamicLanguageService(
  private val delegate: DynamicLanguageService,
  private val language: DynamicLanguageInfo,
  private val npmPackageVersion: String,
  private val iconCache: DynamicLanguageIconCache,
) : DynamicLanguageService by delegate {
  private val iconMutex = Mutex()

  /**
   * 返回当前语言的文件图标，并对业务隐藏缓存命中和版本更新细节。
   *
   * @return 成功时为跨平台矢量图标；缓存与底层 Service 失败保持在 Result 中。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  override suspend fun fileIcon(): NpmJsResult<DynamicLanguageIcon> = npmJsCatching {
    iconMutex.withLock {
      val cached = iconCache.find(language)
      if (cached?.npmPackageVersion == npmPackageVersion) {
        return@withLock cached.icon
      }

      delegate.fileIcon().getOrThrow().also { icon ->
        iconCache.update(
          language = language,
          npmPackageVersion = npmPackageVersion,
          icon = icon,
        )
      }
    }
  }
}
