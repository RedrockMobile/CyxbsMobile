import npm.npmJsLanguage

plugins {
  id("manager.npmJs")
}

version = "0.2.0"

npmJsPackage {
  packageName.set("@cyxbs-mobile/language-java")
}

npmJsLanguage {
  languageId.set("java")
  displayName.set("Java")
  fileExtensions.add("java")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(projects.cyxbsFunctions.code.language.lezer)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/java", "1.1.3"))
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
