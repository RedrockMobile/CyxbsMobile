plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider() // api 模块服务提供
useUnitTest() // Glance 快照投影使用 Android Host Test 验证纯映射逻辑

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsPages.widget.api)
      implementation(libs.kotlinx.coroutines)
      implementation(libs.kotlinx.datetime)
    }
    androidMain.dependencies {
      // 课程详情入口暂时只保留空 Compose Activity，后续弹窗完成分层后再接入内容。
      implementation(projects.cyxbsComponents.config)
      implementation(libs.compose.activity)
      implementation(libs.androidx.glance)
      implementation(libs.androidx.glance.appwidget)
      implementation(libs.kotlinx.serialization)
    }
  }
}
