import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatform)
}

kotlin {
  android {
    namespace = "com.cyxbs.functions.code.npm.api.bridge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get()))
    }
  }
  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get()))
    }
  }
  iosArm64()
  iosSimulatorArm64()
  js {
    nodejs()
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    nodejs()
  }

  sourceSets {
    jsMain.dependencies {
      implementation(libs.kotlinx.coroutines)
    }

    val iosMain = create("iosMain") {
      dependsOn(commonMain.get())
    }
    iosArm64Main { dependsOn(iosMain) }
    iosSimulatorArm64Main { dependsOn(iosMain) }
  }
}
