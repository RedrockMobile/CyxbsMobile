plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转
useRoom3() // Room3 KMP 持久化
useUnitTest(
  androidReturnDefaultValues = true,
  withRoom3 = true,
  withKtorFixture = true,
  withPersistentAndroidDeviceTest = true,
)

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.course.view)
      implementation(libs.okio)
    }
    // 移动端分享和 Desktop 保存面板由 FileKit dialogs 提供，Web 下载只需 core。
    noWebMain.dependencies {
      implementation(libs.filekit.dialogs.compose)
    }
    webMain.dependencies {
      implementation(libs.filekit.core)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.ktor.client.okhttp)
    }
    // Desktop durable Room owner 以 FileKit filesDir 固定业务数据库位置，不依赖 cwd 或临时目录。
    desktopMain.dependencies {
      implementation(libs.filekit.core)
      implementation(libs.ktor.client.okhttp)
    }
    if (Multiplatform.enableIOS(project)) {
      iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
      }
    }
  }
}
