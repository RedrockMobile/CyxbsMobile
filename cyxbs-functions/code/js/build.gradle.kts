plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    noWebMain.dependencies {
      implementation(libs.okio)
      implementation(libs.kmp.ktProvider.manager)
    }

    val noWebTest = create("noWebTest") {
      dependsOn(commonTest.get())
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(project(":cyxbs-functions:code:js:quickjs"))
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
