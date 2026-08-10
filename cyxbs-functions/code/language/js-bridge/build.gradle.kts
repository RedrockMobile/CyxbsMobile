plugins {
  id("manager.npmJsBridge")
  alias(libs.plugins.kotlinSerialization)
}

version = "0.1.0"

npmJsPackage {
  // 各语言分析器共享本协议包，稳定坐标避免模块移动破坏已发布语言包的依赖图。
  packageName.set("@cyxbs-mobile/language-service-bridge")
}
