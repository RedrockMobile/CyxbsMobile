plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsPages.store.api)
      implementation(projects.cyxbsPages.food.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.dialog)
      implementation(libs.ucrop)
      implementation(libs.smartRefreshLayout.header.classics)
      implementation(libs.smartRefreshLayout.footer.classics)
      implementation(libs.smartRefreshLayout)

      // PickerView https://github.com/Bigkoo/Android-PickerView
      // TODO 该库已停止更新
      implementation("com.contrarywind:Android-PickerView:4.1.9")
    }
  }
}


/*
* ufield: u＝邮, field=场地
* */


