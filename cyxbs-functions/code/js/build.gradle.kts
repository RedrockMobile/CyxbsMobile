plugins {
  id("manager.lib")
}

kotlin {
  android {
    // QuickJS 的 Android 产物包含 .so，必须在设备环境中验证动态库加载。
    withDeviceTestBuilder {
      sourceSetTreeName = "test"
    }.configure {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  sourceSets {
    noWebMain.dependencies {
      implementation(libs.kmp.quickjs)
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
    }
    iosArm64Test {
      dependsOn(noWebTest)
    }
    iosSimulatorArm64Test {
      dependsOn(noWebTest)
    }
    getByName("androidDeviceTest") {
      dependsOn(noWebTest)
      dependencies {
        implementation(libs.androidx.test.runner)
      }
    }
  }
}
