package com.cyxbs.compiler.npmjs

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * 将同一运行侧需要的多个协议生成器组合为一个 KSP 处理器。
 *
 * 该类型只承载轮次与生命周期转发，不注册 SPI；业务模块只能选择 JS Service 侧或 Host 侧
 * Provider，不能直接启用内部 codegen。
 */
class CompositeSymbolProcessor(
  private val processors: List<SymbolProcessor>,
) : SymbolProcessor {

  /** 依次执行各协议生成器，并合并仍需延迟处理的符号。 */
  override fun process(resolver: Resolver): List<KSAnnotated> =
    processors.flatMapTo(linkedSetOf()) { processor -> processor.process(resolver) }.toList()

  /** 将 KSP 正常结束事件转发给所有生成器，保证包级聚合代码可以完整生成。 */
  override fun finish() {
    processors.forEach(SymbolProcessor::finish)
  }

  /** 将 KSP 异常结束事件转发给所有生成器，释放各自持有的轮次状态。 */
  override fun onError() {
    processors.forEach(SymbolProcessor::onError)
  }
}
