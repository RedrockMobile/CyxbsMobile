package com.cyxbs.compiler.npmjs

import com.google.devtools.ksp.processing.JsPlatformInfo
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * 为 npm JavaScript Host 侧创建代码生成器。
 *
 * Host 侧既负责生成 `@NpmJsService` 客户端代理，也负责暴露 `@NpmJsBridgeImpl`
 * 客户端实现；这里只允许安装到非 JS 目标。
 */
class KspNpmJsHostProcessorProvider : SymbolProcessorProvider {

  /** 创建固定运行在客户端侧的组合处理器。 */
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    check(environment.platforms.none { it is JsPlatformInfo }) {
      "ksp-npm-js-host cannot be used by a Kotlin/JS target."
    }
    return CompositeSymbolProcessor(
      listOf(
        KspNpmJsServiceProcessor(
          codeGenerator = environment.codeGenerator,
          logger = environment.logger,
          isJsTarget = false,
          npmPackageName = null,
        ),
        KspNpmJsBridgeProcessor(
          codeGenerator = environment.codeGenerator,
          logger = environment.logger,
          isJsTarget = false,
        ),
      ),
    )
  }
}
