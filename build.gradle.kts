buildscript {
  dependencies {
    classpath(libs.vasdolly.gradlePlugin)
  }
}

plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.kotlinAndroid) apply false
  alias(libs.plugins.kotlinCompose) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinAtomicfu) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeHotReload) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.ktProvider) apply false
}

// 管理 git 提交规范的脚本
apply(from = "git-hook.gradle.kts")

