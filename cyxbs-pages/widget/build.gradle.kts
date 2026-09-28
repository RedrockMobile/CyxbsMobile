plugins {
  id("manager.lib")
  id("kmp.compose")
  alias(libs.plugins.buildconfig)
}

useKtProvider() // api 模块服务提供
useUnitTest() // Glance 快照投影使用 Android Host Test 验证纯映射逻辑
useNavigation() // 小组件目录页通过 Navigation3 独立承载

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsPages.widget.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(libs.kotlinx.coroutines)
      implementation(libs.kotlinx.datetime)
    }
    androidMain.dependencies {
      implementation(libs.compose.activity)
      implementation(libs.androidx.glance)
      implementation(libs.androidx.glance.appwidget)
      implementation(libs.kotlinx.serialization)
    }
  }
}

if (Multiplatform.enableIOS(project)) {
  buildConfig {
    packageName("com.cyxbs.pages.widget")
    useKotlinOutput()
    sourceSets.named("iosMain") {
      // 生成值来自扩展 entitlement；同时确保主 App 和 Swift 读取端使用同一个组。
      buildConfigField("APP_GROUP_ID", IosAppGroup.validatedWidgetExtensionId(project))
    }
  }
}
