plugins {
  alias(libs.plugins.androidLibrary)
}

android {
  namespace = "com.mredrock.cyxbs.common"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  compileOptions {
    val javaVersion = libs.versions.javaTarget.get()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
  }
  lint {
    abortOnError = false // 编译遇到错误不退出，可以一次检查多个错误，并且已执行的 task 下次执行会直接走缓存
    targetSdk = libs.versions.android.targetSdk.get().toInt()
  }
  buildFeatures {
    buildConfig = true
  }
}

dependencies {
  implementation(projects.cyxbsComponents.init)
  implementation(projects.cyxbsComponents.base)
  implementation(projects.cyxbsComponents.view)
  implementation(projects.cyxbsComponents.utils)
  implementation(projects.cyxbsComponents.config)
  implementation(projects.cyxbsComponents.account.api)
  implementation(projects.cyxbsPages.login.api)

  implementation(libs.bundles.projectBase)
  implementation(libs.bundles.views)
  implementation(libs.glide)
  implementation(libs.rxpermissions)

  implementation(libs.retrofit)
  implementation(libs.rxjava)
}

/*
* lib_common 模块已被分离为 base、utils、config 模块
* 后续不要再依赖该模块，在完成迁移后会进行删除
*
* */