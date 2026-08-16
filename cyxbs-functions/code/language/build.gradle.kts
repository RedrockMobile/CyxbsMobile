plugins {
  id("manager.lib")
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(libs.kotlinx.serialization)
    }

    noWebMain.dependencies {
      implementation(projects.cyxbsFunctions.code.js)
      implementation(projects.cyxbsFunctions.code.npm)
      implementation(libs.kotlinx.coroutines)
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
