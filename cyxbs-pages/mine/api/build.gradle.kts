plugins {
  id("manager.lib")
  id("kmp.base")
}

useKtProvider() // 历史设置的可选服务接口

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
    }
  }
}
