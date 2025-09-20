plugins {
  id("manager.app")
}

useKtProvider()

// 测试使用，设置 test 暂时不依赖的模块
val excludeList = mutableListOf<String>(

)

kotlin {
  sourceSets {
    commonMain.dependencies {
      // 根 gradle 中包含的所有子模块
      project.rootProject.subprojects.filter {
        it.name !in excludeList
            && it != project
            && it.name != "debug" // lib_debug 单独依赖
            && !it.path.contains("cyxbs-applications")
            && !it.path.contains("cyxbs-compiler")
            && !it.name.startsWith("cyxbs-")
      }.forEach {
        api(it)
      }
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
    }
  }
}