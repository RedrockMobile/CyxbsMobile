plugins {
  id("manager.npmJsApiBridge")
}

kotlin {
  sourceSets {
    jsMain.dependencies {
      implementation(libs.kotlinx.coroutines)
    }
  }
}
