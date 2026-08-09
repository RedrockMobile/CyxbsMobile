plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(projects.config)
  implementation(projects.plugin.checker)
  implementation(projects.plugin.kmp)

  implementation(libs.android.gradlePlugin)
  implementation(libs.kotlin.gradlePlugin)
  implementation(libs.ksp.gradlePlugin)
  implementation(libs.kmp.ktProvider.gradlePlugin)
  implementation(libs.kmp.ktorfit.gradlePlugin)

  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.gson)

  testImplementation(kotlin("test"))
  testImplementation(gradleTestKit())
}
