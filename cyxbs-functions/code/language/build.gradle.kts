plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsFunctions.code.language.jsBridge)
    }

    noWebMain.dependencies {
      implementation(projects.cyxbsFunctions.code.js)
      implementation(libs.okio)
      implementation(libs.kotlinx.serialization)
    }

    val noWebTest = create("noWebTest") {
      dependsOn(commonTest.get())
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.cyxbsFunctions.code.js.quickjs)
      }
    }
    desktopTest {
      dependsOn(noWebTest)
    }
    iosArm64Test {
      dependsOn(noWebTest)
    }
    iosSimulatorArm64Test {
      dependsOn(noWebTest)
    }
  }
}
