package com.cyxbs.functions.code.language.java.compiler.backend.js

/** 阶段 0 生成单个 Java ES Module 时使用的稳定模块与导出名称。 */
internal object JavaModuleLayout {
  /** 交给宿主 Module Loader 注册的唯一 Java 程序模块名。 */
  const val ENTRY_MODULE_NAME: String = "java-program.mjs"

  /** 宿主调用 Java 静态入口时使用的稳定导出名称。 */
  const val ENTRY_EXPORT_NAME: String = "__cyxbs_java_entry__"
}
