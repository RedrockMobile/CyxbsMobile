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
  }
}
