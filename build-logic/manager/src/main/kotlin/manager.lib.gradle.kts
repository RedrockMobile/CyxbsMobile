import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import rule.ModuleNamespaceCheckRule

plugins {
  id("com.android.kotlin.multiplatform.library")
  id("kmp.base")
}

ProjectChecker.config(project) // 项目检查工具

kotlin {
  android {
    namespace = ModuleNamespaceCheckRule.getCorrectNamespace(project)
    compileSdk = libsEx.versions.`android-compileSdk`.toInt()
    minSdk = libsEx.versions.`android-minSdk`.toInt()
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
    }
    lint {
      abortOnError = false // 编译遇到错误不退出，可以一次检查多个错误，并且已执行的 task 下次执行会直接走缓存
      targetSdk = libsEx.versions.`android-targetSdk`.toInt()
    }
    androidResources {
      // 是否启动资源取决于是否还存在 /res 或 /composeResources 目录
      //noinspection WrongGradleMethod
      enable = project.projectDir.resolve("src").listFiles()
        ?.filter { it.name.lowercase().endsWith("main") && it.isDirectory }
        ?.any { it.resolve("res").exists() || it.resolve("composeResources").exists() }
        ?: false
      // 命名规范设置，因为多模块相同资源名在打包时会合并，所以必须强制开启
      val paths = project.path.split(":").drop(1)
      if (paths.size == 1) {
        resourcePrefix = project.name.substringAfter("_")
      } else if (paths.first().contains("cyxbs-")) {
        resourcePrefix = project.name
      } else {
        resourcePrefix = paths[paths.size - 2] + "_" + paths.last()
      }
    }
  }
}
