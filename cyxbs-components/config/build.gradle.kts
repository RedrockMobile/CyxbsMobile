plugins {
  id("manager.lib")
  id("kmp.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.account.api)
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



