import npm.npmJsTutorial

plugins {
  id("manager.npmJs")
  alias(libs.plugins.kotlinSerialization)
}

version = "0.1.0"

npmJsPackage {
  packageName.set("@cyxbs-mobile/tutorial-java")
}

npmJsTutorial {
  languageId.set("java")
  displayName.set("Java")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.tutorials.jsBridge)
    }
    jsMain.dependencies {
      // 教程进度由 npm 包通过通用桥保存，客户端不再维护独立进度文件。
      implementation(projects.cyxbsFunctions.code.npm.jsBridge)
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
      implementation(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(projects.cyxbsFunctions.code.language.java)
    }
  }
}
