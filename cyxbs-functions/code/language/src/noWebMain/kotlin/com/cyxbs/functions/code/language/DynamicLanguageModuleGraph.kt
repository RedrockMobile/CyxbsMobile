package com.cyxbs.functions.code.language

/**
 * 已完成下载、完整性校验和依赖解析的不可变 ES Module 图。
 *
 * 该模型只描述动态语言 Runtime 的最小输入，不承诺后端清单、npm 包或本地缓存的具体格式。未来下载层
 * 应先在 QuickJS 锁外准备完整源码图，再创建本对象；Module Loader 运行期间只会读取内存副本。
 *
 * @param entryModule 入口 Module 名称，必须同时存在于 [moduleSources]。
 * @param moduleSources 以 Module 名称为键的完整源码图。
 */
class DynamicLanguageModuleGraph(
  val entryModule: String,
  moduleSources: Map<String, String>,
) {
  internal val moduleSources: Map<String, String> = moduleSources.toMap()

  /** 当前 Module 图全部源码的 UTF-8 大小。 */
  val sourceSizeBytes: Long = this.moduleSources.values.sumOf {
    it.encodeToByteArray().size.toLong()
  }

  init {
    require(entryModule.isNotBlank() && '\u0000' !in entryModule) {
      "Dynamic language entry module is invalid."
    }
    require(this.moduleSources.isNotEmpty()) {
      "Dynamic language module graph must not be empty."
    }
    require(this.moduleSources.keys.all { it.isNotBlank() && '\u0000' !in it }) {
      "Dynamic language module graph contains an invalid module name."
    }
    require(entryModule in this.moduleSources) {
      "Dynamic language entry module '$entryModule' is missing from the module graph."
    }
  }

  /** 返回入口 ESM 源码。 */
  internal fun entrySource(): String = moduleSources.getValue(entryModule)
}
