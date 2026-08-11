import npm.generateDynamicLanguageCatalog

plugins {
  id("manager.npmStatic")
}

version = "0.1.0"

npmStaticPackage {
  // 端上只写死该坐标；模块路径和实现类调整不得改变 Catalog 的 npm 身份。
  packageName.set("@cyxbs-mobile/language-catalog")
}

// 每个语言 Project 自己通过 npmJsLanguage 声明元数据；这里仅维护正式发布语言的 Project 集合。
generateDynamicLanguageCatalog(
  project(":cyxbs-functions:code:language:javascript"),
)
