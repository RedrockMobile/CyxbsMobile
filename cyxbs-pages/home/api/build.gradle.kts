plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation() // navigation 跳转

kotlin {
  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
