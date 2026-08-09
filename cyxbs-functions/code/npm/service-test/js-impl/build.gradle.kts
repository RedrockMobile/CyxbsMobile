plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  js {
    compilations["main"].packageJson {
      name = "@cyxbs-mobile/npm-service-test"
      version = "0.1.0"
    }
  }
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.npm.serviceTest.jsBridge)
    }
  }
}

useNpmJsService("@cyxbs-mobile/npm-service-test")
