plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.libCommon) // TODO common 模块不再使用，新模块请依赖 base 和 utils 模块
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsFunctions.update.api)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.login.api)
      implementation(projects.cyxbsPages.store.api)
      implementation(projects.cyxbsPages.course.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.bundles.network)
      implementation(libs.androidx.work)
      implementation(libs.lPhotoPicker)
      implementation(libs.glide)
      implementation(libs.dialog)
      implementation(libs.ucrop)

      // PickerView https://github.com/Bigkoo/Android-PickerView
      // TODO 该库已停止更新
      implementation("com.contrarywind:Android-PickerView:4.1.9")
      // https://github.com/kyleduo/SwitchButton
      implementation("com.kyleduo.switchbutton:library:2.1.0")
    }
  }
}
