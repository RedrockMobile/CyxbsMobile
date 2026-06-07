plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.store.api)
      implementation(projects.cyxbsPages.food.api)
      implementation(projects.cyxbsPages.home.api)
      implementation(projects.cyxbsPages.map.api)
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