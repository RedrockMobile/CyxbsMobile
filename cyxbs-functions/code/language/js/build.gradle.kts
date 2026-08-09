plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.apiBridge)
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
