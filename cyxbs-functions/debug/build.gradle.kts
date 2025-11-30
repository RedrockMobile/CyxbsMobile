plugins {
  id("manager.lib")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
    }
    androidMain.dependencies {
      // 依赖 LeakCanary，检查内存泄漏 https://github.com/square/leakcanary
      implementation(libs.leakcanary)

      // 字节很好用调试工具 https://github.com/bytedance/CodeLocator
      implementation(libs.codeLocator.core)
    }
  }
}

