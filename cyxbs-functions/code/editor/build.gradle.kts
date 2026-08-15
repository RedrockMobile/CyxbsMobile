plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider() // 提供编辑器测试页面
useNavigation() // 注册编辑器测试页面

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.view)
    }
    noWebMain.dependencies {
      implementation(projects.cyxbsFunctions.code.js)
      implementation(projects.cyxbsFunctions.code.js.quickjs)
      implementation(projects.cyxbsFunctions.code.language)
      implementation(libs.kmp.kodemirror.view)
      implementation(libs.kmp.kodemirror.basicSetup)
      implementation(libs.kmp.kodemirror.autocomplete)
      implementation(libs.kmp.kodemirror.lezerCommon)
    }
    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
      implementation(libs.compose.activity)
    }

    // 工作台只存在于 noWebMain；测试层级保持一致，避免 Web 目标编译不具备的 QuickJS 编辑能力。
    val noWebTest = create("noWebTest") {
      dependsOn(commonTest.get())
      dependencies {
        implementation(kotlin("test"))
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
