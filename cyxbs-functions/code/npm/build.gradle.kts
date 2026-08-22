plugins {
  id("manager.lib")
}

useNpmJsHost()

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsFunctions.code.npm.jsBridge)
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.utils)
    }

    noWebMain.dependencies {
      api(projects.cyxbsFunctions.code.js)
      implementation(projects.cyxbsComponents.config)
      implementation(libs.kotlinx.coroutines)
      implementation(libs.kotlinx.serialization)
      implementation(libs.filekit.core)
      implementation(libs.ktor.core)
      implementation(libs.kmp.ktProvider.api)
      implementation(libs.kmp.ktProvider.manager)
      implementation(libs.okio)
    }

    val noWebTest = create("noWebTest") {
      dependsOn(commonTest.get())
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    desktopTest {
      dependsOn(noWebTest)
      dependencies {
        implementation(projects.cyxbsFunctions.code.js.quickjs)
        implementation(libs.kmp.ktProvider.testing)
        implementation(libs.ktor.client.okhttp)
      }
    }
    iosArm64Test {
      dependsOn(noWebTest)
    }
    iosSimulatorArm64Test {
      dependsOn(noWebTest)
    }
  }
}
