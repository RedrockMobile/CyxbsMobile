import rule.ModuleNamespaceCheckRule

plugins {
  id("manager.app-multiplatform")
  alias(libs.plugins.buildconfig)
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
            && it.buildFile.isFile // 排除仅用于组织目录、没有构建脚本和产物的父 Project
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
        candidate.pluginManager.withPlugin("manager.npmStatic") {
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

val isDesktopDebugRun = project.gradle.startParameter.taskNames.any { taskName ->
  taskName.substringAfterLast(':') == "run" || taskName.substringAfterLast(':') == "hotRunDesktop"
}

buildConfig {
  packageName(ModuleNamespaceCheckRule.getCorrectNamespace(project))
  useKotlinOutput()
  sourceSets.named("desktopMain") {
    // ⚠️这里的配置如果其他模块需要统一写进 cyxbs-functions/init 模块的 DesktopProjectEnvironment 中
    buildConfigField(
      "APP_ID",
      "com.mredrock.cyxbs"
    )
    buildConfigField(
      "PROJECT_DIRECTORY",
      if (isDesktopDebugRun) {
        // 仅在 AS 中运行桌面端时才输出项目路径
        rootProject.layout.projectDirectory.asFile.absolutePath
      } else "",
    )
  }
}

// Desktop KSP 会扫描 BuildConfig 生成源码；显式声明顺序，避免 clean 构建时并发读取未生成目录。
tasks.matching { task -> task.name == "kspKotlinDesktop" }.configureEach {
  dependsOn("generateDesktopMainBuildConfigClasses")
}
