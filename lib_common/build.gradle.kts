plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.login.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.bundles.network)
      implementation(libs.glide)
      implementation(libs.rxpermissions)
      implementation(libs.lPhotoPicker)
      implementation(libs.photoView)
    }
  }
}

/*
* lib_common 模块已被分离为 base、utils、config 模块
* 后续不要再依赖该模块，在完成迁移后会进行删除
*
* */