plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  sourceSets {
    jsMain.dependencies {
      // `configureNpmJsPackaging()` 会监听每个应用 manager.npmJs 的业务模块，并将它自动添加到
      // distribution 的 jsMainApi 配置中，因此这里无需手工维护语言模块列表。最终关系为：
      // distribution -> language:js -> language:js-bridge/npm:js-bridge。
      // 下方只显式保留所有业务包都需要长期复用的 Kotlin/JS 基础依赖。
      api(libs.kotlinx.coroutines)
      api(libs.kotlinx.serialization)
    }
  }
}

npmJsPackage {
  // Runtime 发布包保留基础 Module；其他 manager.npmJs 模块默认将它们外置为精确 npm 依赖。
  bundleKotlinRuntime.set(true)
}
