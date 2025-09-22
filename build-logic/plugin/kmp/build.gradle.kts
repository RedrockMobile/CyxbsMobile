plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(projects.config)
  implementation(libs.android.gradlePlugin)
  implementation(libs.kotlin.gradlePlugin)
  api(libs.compose.gradlePlugin)
  api(libs.compose.compiler.gradlePlugin)
  api(libs.kotlinx.serialization.gradlePlugin)
  api(libs.kotlinx.atomicfu.gradlePlugin)
}