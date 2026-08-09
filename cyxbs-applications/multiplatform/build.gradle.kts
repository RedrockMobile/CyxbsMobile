plugins {
  id("manager.app-multiplatform")
}

useKtProvider()

// 测试使用，设置 multiplatform 暂时不依赖的模块
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
            && it.name != "lib_common" // lib_common 由其他模块间接依赖
      }.forEach { candidate ->
        val dependency = api(candidate)
        // withPlugin 不受项目配置顺序影响；纯 JS 发布模块应用插件后会自动退出壳依赖图。
        candidate.pluginManager.withPlugin("manager.npmJs") {
          configurations["commonMainApi"].dependencies.remove(dependency)
        }
      }
    }
    desktopMain.dependencies {
      implementation(libs.filekit.core)
    }
  }
}

tasks.all {
  if (name == "wasmJsBrowserDistribution"
    || name == "wasmJsBrowserProductionRun"
    || name == "wasmJsBrowserProductionWebpack"
    || name == "wasmJsBrowserDevelopmentRun"
  ) {
    // 抑制 channelRelease 不能缓存的报错
    notCompatibleWithConfigurationCache("suppres configuration cache")
  }
}
