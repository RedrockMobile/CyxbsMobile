package com.cyxbs.functions.code.tutorials.internal

import com.cyxbs.functions.code.npm.NpmJsServiceLoader
import com.cyxbs.functions.code.npm.NpmPackageAssetLoader
import com.cyxbs.functions.code.tutorials.DynamicTutorialCatalog
import com.cyxbs.functions.code.tutorials.DynamicTutorialInfo
import com.cyxbs.functions.code.tutorials.DynamicTutorialProtocolException
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService

/** Manager 与 npm 实现之间的内部测试边界。 */
internal interface DynamicTutorialPackageLoader {
  /** 读取固定 Catalog npm 包中的 JSON。 */
  suspend fun loadCatalog(): String

  /** 加载已通过 Catalog 校验的语言教程 Service。 */
  suspend fun loadTutorial(packageName: String): LoadedDynamicTutorialPackage
}

/** 已初始化的教程代理及包池最终采用的根包版本。 */
internal data class LoadedDynamicTutorialPackage(
  val service: DynamicTutorialService,
  val npmPackageVersion: String,
)

/** 使用通用 npm 资源与 Service Loader 加载教程目录和语言教程实现。 */
internal class NpmDynamicTutorialPackageLoader(
  initialServiceLoader: NpmJsServiceLoader? = null,
  private val assetLoader: NpmPackageAssetLoader = NpmPackageAssetLoader(),
) : DynamicTutorialPackageLoader {
  private val serviceLoader by lazy { initialServiceLoader ?: NpmJsServiceLoader() }

  /** 读取不包含 Kotlin/JS Runtime 的静态 Catalog。 */
  override suspend fun loadCatalog(): String {
    return assetLoader.loadText(CATALOG_PACKAGE_NAME, CATALOG_ASSET_PATH)
  }

  /** 创建独立教程 Runtime，并保留最终 npm 版本供进度与诊断展示。 */
  override suspend fun loadTutorial(packageName: String): LoadedDynamicTutorialPackage {
    val loaded = serviceLoader.loadWithInfo(
      serviceClass = DynamicTutorialService::class,
      packageName = packageName,
    )
    return LoadedDynamicTutorialPackage(
      service = loaded.service,
      npmPackageVersion = loaded.entryPackage.version,
    )
  }

  private companion object {
    const val CATALOG_PACKAGE_NAME = "@cyxbs-mobile/tutorial-catalog"
    const val CATALOG_ASSET_PATH = "catalog.json"
  }
}

/** 校验 Catalog 并复制集合，避免外部可变列表污染 Manager 缓存。 */
internal fun DynamicTutorialCatalog.validatedTutorials(): List<DynamicTutorialInfo> {
  if (tutorials.isEmpty()) {
    throw DynamicTutorialProtocolException("Dynamic tutorial Catalog is empty.")
  }
  val identities = mutableSetOf<String>()
  val packageNames = mutableSetOf<String>()
  return tutorials.mapIndexed { index, tutorial ->
    if (!LANGUAGE_ID_REGEX.matches(tutorial.languageId)) {
      throw DynamicTutorialProtocolException("Tutorial at index $index has an invalid language id.")
    }
    if (tutorial.displayName.isBlank() || tutorial.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
      throw DynamicTutorialProtocolException(
        "Tutorial '${tutorial.languageId}' has an invalid display name.",
      )
    }
    if (!TUTORIAL_PACKAGE_REGEX.matches(tutorial.npmPackageName)) {
      throw DynamicTutorialProtocolException(
        "Tutorial '${tutorial.languageId}' has an invalid npm package name.",
      )
    }
    if (tutorial.aliases.distinct().size != tutorial.aliases.size ||
      tutorial.aliases.any { !LANGUAGE_ID_REGEX.matches(it) }
    ) {
      throw DynamicTutorialProtocolException(
        "Tutorial '${tutorial.languageId}' has invalid or duplicate aliases.",
      )
    }
    (listOf(tutorial.languageId) + tutorial.aliases).forEach { identity ->
      if (!identities.add(identity)) {
        throw DynamicTutorialProtocolException(
          "Dynamic tutorial Catalog contains duplicate id or alias '$identity'.",
        )
      }
    }
    if (!packageNames.add(tutorial.npmPackageName)) {
      throw DynamicTutorialProtocolException(
        "Dynamic tutorial Catalog contains duplicate package '${tutorial.npmPackageName}'.",
      )
    }
    tutorial.copy(aliases = tutorial.aliases.toList())
  }
}

private const val MAX_DISPLAY_NAME_LENGTH = 128
private val LANGUAGE_ID_REGEX = Regex("[a-z][a-z0-9-]{0,63}")
private val TUTORIAL_PACKAGE_REGEX = Regex("@cyxbs-mobile/tutorial-[a-z0-9][a-z0-9._~-]*")
