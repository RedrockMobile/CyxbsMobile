plugins {
  alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
  jvm()
  sourceSets {
    jvmMain.dependencies {
      compileOnly(libs.ksp.api)
      implementation(project(":cyxbs-compiler:npm-js-codegen"))
    }
  }
}
