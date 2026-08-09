plugins {
  id("manager.npmJsApiBridge")
}

version = "0.1.0"

kotlin {
  sourceSets {
    jsMain.dependencies {
      implementation(libs.kotlinx.coroutines)
    }
  }
}
