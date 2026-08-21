plugins {
  id("manager.npmJsBridge")
  alias(libs.plugins.kotlinSerialization)
}

version = "0.1.0"

npmJsPackage {
  // 各语言教程共享本协议包；稳定坐标避免目录层级调整破坏已发布教程包。
  packageName.set("@cyxbs-mobile/tutorial-service-bridge")
}
