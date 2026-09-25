plugins {
  id("manager.lib")
}

useUnitTest() // 在 Android Host Test 中执行公共快照与点击协议测试，避免依赖全仓 JS Yarn 锁更新

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
