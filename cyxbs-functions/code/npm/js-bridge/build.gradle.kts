plugins {
  id("manager.npmJsBridge")
  alias(libs.plugins.kotlinSerialization)
}

version = "0.2.0"

npmJsPackage {
  // npm Service 协议会被所有动态业务包依赖，使用与仓库目录无关的稳定坐标。
  packageName.set("@cyxbs-mobile/npm-service-bridge")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines)
      // 结果信封与 JSON 扩展的公开签名包含序列化类型，所有目标都需要该协议依赖。
      api(libs.kotlinx.serialization)
    }
    jsTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
