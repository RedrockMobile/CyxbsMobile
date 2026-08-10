plugins {
  id("manager.npmJsBridge")
}

version = "0.1.0"

npmJsPackage {
  // npm Service 协议会被所有动态业务包依赖，使用与仓库目录无关的稳定坐标。
  packageName.set("@cyxbs-mobile/npm-service-bridge")
}

kotlin {
  sourceSets {
    jsMain.dependencies {
      implementation(libs.kotlinx.coroutines)
    }
  }
}
