plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    noWebMain.dependencies {
      api(projects.cyxbsFunctions.code.js)
      implementation(libs.kotlinx.coroutines)
      implementation(libs.kotlinx.serialization)
      implementation(libs.ktor.core)
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
