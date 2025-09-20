import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * .
 *
 * @author 985892345
 * 2025/3/30 22:14
 */
class KspNetworkSymbolProcessProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return KspNetworkSymbolProcess(
      environment.codeGenerator,
      environment.logger,
      KspNetworkOptions(environment.options),
    )
  }
}