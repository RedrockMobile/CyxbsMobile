import npm.generateDynamicTutorialCatalog

plugins {
  id("manager.npmStatic")
}

version = "0.1.0"

npmStaticPackage {
  // 客户端只写死该坐标，Catalog 自身不携带 Kotlin/JS Runtime。
  packageName.set("@cyxbs-mobile/tutorial-catalog")
}

// 新增语言教程时只需在此加入对应 Project，npm 包名由项目自身配置自动读取。
generateDynamicTutorialCatalog(
  project(":cyxbs-functions:code:tutorials:java"),
)
