plugins {
  id("manager.npmJs")
}

version = "0.1.0"

npmJsPackage {
  // 语言目录对外发布该坐标，不能因 Gradle 模块重命名或移动而变化。
  packageName.set("@cyxbs-mobile/language-javascript")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(projects.cyxbsFunctions.code.language.lezer)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/javascript", "1.5.4"))
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
