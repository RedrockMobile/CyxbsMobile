plugins {
  alias(libs.plugins.kotlinMultiplatform) // 这里只使用 kotlinMultiplatform 的 jvm
}

kotlin {
  jvm()
  sourceSets {
    jvmMain.dependencies {
      compileOnly(libs.ksp.api)
      // https://square.github.io/kotlinpoet/
      implementation(libs.kotlinpoet)
      implementation("io.github.985892345:provider-api:${libs.versions.kmp.ktProvider.get()}")
    }
  }
}
