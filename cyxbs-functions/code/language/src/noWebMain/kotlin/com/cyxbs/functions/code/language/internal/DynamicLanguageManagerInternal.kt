package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.language.DynamicLanguageCatalog
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.DynamicLanguageProtocolException
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.npm.NpmPackageAssetLoader
import com.cyxbs.functions.code.npm.NpmJsServiceLoader

/** Manager 与具体 npm 加载器之间的内部测试边界。 */
internal interface DynamicLanguagePackageLoader {

  /** 加载固定 Catalog npm 包中的 JSON 文本。 */
  suspend fun loadCatalog(): String

  /** 加载一个 Catalog 已校验坐标对应的语言 Service。 */
  suspend fun loadLanguage(packageName: String): DynamicLanguageService
}

/**
 * 使用静态 npm 资源加载器读取 Catalog，并用通用 Service Loader 加载语言实现。
 *
 * Loader 惰性创建，确保构造 [com.cyxbs.functions.code.language.DynamicLanguageManager] 时不会因
 * JavaScript Runtime Provider 尚未安装而提前失败；实际加载异常保持 npm 层原始类型并直接透传。
 */
internal class NpmDynamicLanguagePackageLoader(
  initialServiceLoader: NpmJsServiceLoader? = null,
  private val assetLoader: NpmPackageAssetLoader = NpmPackageAssetLoader(),
) : DynamicLanguagePackageLoader {

  private val serviceLoader: NpmJsServiceLoader by lazy {
    initialServiceLoader ?: NpmJsServiceLoader()
  }

  override suspend fun loadCatalog(): String {
    return assetLoader.loadText(
      packageName = CATALOG_PACKAGE_NAME,
      assetPath = CATALOG_ASSET_PATH,
    )
  }

  override suspend fun loadLanguage(packageName: String): DynamicLanguageService {
    return serviceLoader.load(
      serviceClass = DynamicLanguageService::class,
      packageName = packageName,
    )
  }

  private companion object {
    /** 客户端唯一写死的动态语言 npm 坐标。 */
    const val CATALOG_PACKAGE_NAME = "@cyxbs-mobile/language-catalog"

    /** Catalog 静态 npm 包中约定的目录文件。 */
    const val CATALOG_ASSET_PATH = "catalog.json"
  }
}

/** 校验 Catalog 快照并复制其集合，避免调用方修改 Manager 的缓存内容。 */
internal fun DynamicLanguageCatalog.validatedLanguages(): List<DynamicLanguageInfo> {
  if (languages.isEmpty()) {
    throw DynamicLanguageProtocolException("Dynamic language Catalog is empty.")
  }

  val identities = mutableSetOf<String>()
  val packageNames = mutableSetOf<String>()
  return languages.mapIndexed { index, language ->
    validateLanguageInfo(index, language)
    val allIdentities = listOf(language.languageId) + language.aliases
    allIdentities.forEach { identity ->
      if (!identities.add(identity)) {
        throw DynamicLanguageProtocolException(
          "Dynamic language Catalog contains duplicate id or alias '$identity'.",
        )
      }
    }
    if (!packageNames.add(language.npmPackageName)) {
      throw DynamicLanguageProtocolException(
        "Dynamic language Catalog contains duplicate package '${language.npmPackageName}'.",
      )
    }
    language.copy(
      aliases = language.aliases.toList(),
      fileExtensions = language.fileExtensions.toList(),
    )
  }
}

/** 校验单个 Catalog 语言定义的稳定身份和可展示字段。 */
private fun validateLanguageInfo(index: Int, language: DynamicLanguageInfo) {
  if (!LANGUAGE_ID_REGEX.matches(language.languageId)) {
    throw DynamicLanguageProtocolException(
      "Dynamic language at index $index has an invalid language id.",
    )
  }
  if (language.displayName.isBlank() || language.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
    throw DynamicLanguageProtocolException(
      "Dynamic language '${language.languageId}' has an invalid display name.",
    )
  }
  if (!LANGUAGE_PACKAGE_REGEX.matches(language.npmPackageName)) {
    throw DynamicLanguageProtocolException(
      "Dynamic language '${language.languageId}' has an invalid npm package name.",
    )
  }
  if (language.aliases.distinct().size != language.aliases.size ||
    language.aliases.any { !LANGUAGE_ID_REGEX.matches(it) }
  ) {
    throw DynamicLanguageProtocolException(
      "Dynamic language '${language.languageId}' has invalid or duplicate aliases.",
    )
  }
  if (language.fileExtensions.distinct().size != language.fileExtensions.size ||
    language.fileExtensions.any { !FILE_EXTENSION_REGEX.matches(it) }
  ) {
    throw DynamicLanguageProtocolException(
      "Dynamic language '${language.languageId}' has invalid or duplicate file extensions.",
    )
  }
}

private const val MAX_DISPLAY_NAME_LENGTH = 128
private val LANGUAGE_ID_REGEX = Regex("[a-z][a-z0-9-]{0,63}")
private val FILE_EXTENSION_REGEX = Regex("[a-z0-9][a-z0-9+_-]{0,31}")
private val LANGUAGE_PACKAGE_REGEX = Regex("@cyxbs-mobile/language-[a-z0-9][a-z0-9._~-]*")
