plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  js {
    compilations["main"].packageJson {
      name = "@cyxbs-mobile/language-javascript"
      version = "0.1.0"
    }
  }
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.apiBridge)
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

useNpmJsService()
