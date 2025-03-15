plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider() // api 模块服务提供

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.account.api)
      implementation(libs.kmp.ktProvider.manager) // config 私有，其他模块通过 ::class.impl() 获取
    }
    androidMain.dependencies {
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.constraintlayout)
      implementation(libs.material)
      implementation(libs.dialog) // 因为要设置 MaterialDialog 主题所以依赖
      implementation(libs.rxjava)
      implementation(libs.lPhotoPicker)
    }
  }
}



