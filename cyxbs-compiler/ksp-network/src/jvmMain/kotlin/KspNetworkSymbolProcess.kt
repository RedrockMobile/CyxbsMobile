import com.g985892345.provider.api.annotation.ImplProvider
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * 简化 Ktorfit 的使用方式，更多查看 [ApiGenerator] 的注释
 *
 * ```
 * runCatchingCoroutine {
 *   CourseApiService::class.impl().getStuLesson(stuNum) // 直接使用 XXXApi::class.impl() 就可以直接获取到实现类
 * }.mapCatching {
 *   it.throwApiExceptionIfFail()
 *   it
 * }.onSuccess {
 *   // 网络请求返回结果
 * }
 * ```
 *
 * Ktorfit 有以下缺点：
 * - 每次使用都要先触发 KSP task 才会生成，非常不便捷
 * - 不会在自定义的 mobileMain 源集生成引用，只有 commonMain 和对应平台源集才能引用实现类
 * 
 * 改进方式：
 * - 编译时找到实现类使用 @ImplProvider 注解绑定到接口的默认实现，后续只需要 XXXApi::class.impl() 就可以直接获取到实现类
 *
 * @author 985892345
 * 2025/3/30 22:14
 */
class KspNetworkSymbolProcess(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: KspNetworkOptions,
) : SymbolProcessor {

  companion object {
    private const val ANNOTATION = "kotlin.OptIn"
    private val KTORFIT_INSTANCE =
      MemberName("com.cyxbs.components.utils.network", "Network")
  }

  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver.getSymbolsWithAnnotation(ANNOTATION) // Ktorfit 实现类默认带 @OptIn(InternalKtorfitApi::class)
      .filterIsInstance<KSClassDeclaration>()
      .filter { it.annotations.first().arguments.firstOrNull()?.value.toString() == "[InternalKtorfitApi]" }
      .forEach {
        generate(it)
      }
    return emptyList()
  }

  private fun generate(declaration: KSClassDeclaration) {
    val className = declaration.simpleName.asString() + "_KtProvider"
    val apiServiceClassName = declaration.superTypes.first().resolve().toClassName()
    FileSpec.builder(declaration.packageName.asString(), className)
      .addType(
        TypeSpec.objectBuilder(className)
          .addAnnotation(
            // 添加 @ImplProvider 注解
            AnnotationSpec.builder(ImplProvider::class)
              .addMember("%T::class", apiServiceClassName)
              .addMember("%S", "")
              .build()
          )
          .addSuperinterface(
            apiServiceClassName,
            CodeBlock.builder() // 添加接口代理 by Network.createXXXApiService()
              .add("%M.create%T()", KTORFIT_INSTANCE, apiServiceClassName)
              .build()
          )
          .build()
      ).build().apply {
        writeTo(codeGenerator, false, listOf(declaration.containingFile!!))
      }
  }
}