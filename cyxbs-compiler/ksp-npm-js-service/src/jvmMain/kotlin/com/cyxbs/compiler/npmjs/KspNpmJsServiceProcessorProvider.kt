package com.cyxbs.compiler.npmjs

import com.google.devtools.ksp.processing.JsPlatformInfo
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** 为当前 KSP 目标创建 npm JavaScript Service 处理器。 */
class KspNpmJsServiceProcessorProvider : SymbolProcessorProvider {

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return KspNpmJsServiceProcessor(
      codeGenerator = environment.codeGenerator,
      logger = environment.logger,
      isJsTarget = environment.platforms.any { it is JsPlatformInfo },
    )
  }
}
