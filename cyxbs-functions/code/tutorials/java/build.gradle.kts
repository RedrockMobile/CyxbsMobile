import npm.npmJsTutorial

plugins {
  id("manager.npmJs")
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
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
