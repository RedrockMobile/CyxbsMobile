plugins {
  alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
  jvm()
  sourceSets {
    jvmMain.dependencies {
      compileOnly(libs.ksp.api)
      implementation(libs.kotlinpoet)
      implementation("io.github.985892345:provider-api:${libs.versions.kmp.ktProvider.get()}")
    }
  }
}
