plugins {
  id("manager.npmJs")
}

version = "0.1.0"

npmJsPackage {
  // 多门语言会复用 Lezer 适配层，使用独立稳定包名以便客户端全局依赖池去重。
  packageName.set("@cyxbs-mobile/language-lezer")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsFunctions.code.language.jsBridge)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/highlight", "1.2.3"))
    }
  }
}
