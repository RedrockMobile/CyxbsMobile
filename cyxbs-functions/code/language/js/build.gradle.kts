plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
}

version = "0.1.0"

kotlin {
  js {
    nodejs()
    binaries.library()
    useEsModules()
    generateTypeScriptDefinitions()
    compilations["main"].packageJson {
      name = "@cyxbs-mobile/language-javascript"
      version = "0.1.0"
      customField("type", "module")
    }
  }
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.apiBridge)
    }
    jsTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

useNpmJsService()
