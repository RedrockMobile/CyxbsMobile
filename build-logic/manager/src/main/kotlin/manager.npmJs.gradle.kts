import npm.configureNpmJsPackaging

plugins {
  kotlin("multiplatform")
}

/**
 * 可发布到 npm、供端上 JavaScript Runtime 加载的纯 Kotlin/JS 模块约定。
 *
 * 这里只统一编译产物形态；npm 坐标和业务依赖仍由具体模块声明。发布文件限制为运行代码与类型声明，
 * source map 继续保留在 CI 构建产物中，但不会进入客户端下载的 npm tgz。
 *
 * 应用插件后会把当前模块自动加入 `:cyxbs-functions:code:npm:distribution` 的聚合编译，并提供：
 * - `prepareNpmJsPackage`：仅生成最终发布目录，用于检查分包结果；不调用 npm、不联网。
 * - `packNpmJsPackage`：本地生成 Runtime 和当前业务包的 tgz；不访问 Registry。
 * - `publishNpmJsPackage`：先联网保证 Runtime 已发布，再发布当前业务包。
 *
 * 正常发布只需执行最后一个任务，前两个任务主要用于本地调试、安装验证和 CI 制品留存。
 */
kotlin {
  js {
    nodejs()
    binaries.library()
    useEsModules()
    generateTypeScriptDefinitions()
    compilations["main"].packageJson {
      customField("type", "module")
      customField("files", listOf("**/*.mjs", "**/*.d.mts"))
    }
  }

  sourceSets {
    jsTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

configureNpmJsPackaging()
