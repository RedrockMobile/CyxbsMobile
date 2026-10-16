plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kmp.kermit)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
    noWebMain.dependencies {
      implementation(libs.okio)
    }
  }
}
