plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.libCommon) // TODO common 模块不再使用，新模块请依赖 base 和 utils 模块
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.bundles.network)
      implementation(libs.lPhotoPicker)
      implementation(libs.glide)
      // https://github.com/davemorrissey/subsampling-scale-image-view?tab=readme-ov-file
      // 地图加载需要的大图浏览控件
      compileOnly("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
    }
  }
}
