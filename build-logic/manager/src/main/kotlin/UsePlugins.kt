import com.g985892345.provider.plugin.gradle.extensions.KtProviderExtensions
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension


/**
 * 使用 KtProvider
 * 985892345 的 KMP 多平台服务提供框架
 * 单独给每个模块都添加而不是直接在 build-logic 中全部添加的原因:
 * - 为了按需引入 ksp
 * - 部分 lib 模块只使用依赖，不包含注解，只需要依赖
 */
fun Project.useKtProvider(isNeedKsp: Boolean = !name.startsWith("api")) {
  if (isNeedKsp) {
    // kapt 按需引入
    apply(plugin = "com.google.devtools.ksp")
    apply(plugin = libsEx.plugins.ktProvider)
    val ktProvider = extensions.getByName("ktProvider") as KtProviderExtensions
    kspMultiplatform(ktProvider.ksp)
    ktProvider.configurations.add("debugImplementation") // debug 模块需要单独添加配置
  }
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
      implementation(libsEx.`kmp-ktProvider-api`)
    }
  }
}

/**
 * 使用 Room，已默认支持与 Kt 协程一起使用
 * @param rxjava 依赖 room-rxjava
 * @param paging 依赖 room-paging
 */
fun Project.useRoom(
  rxjava: Boolean = false,
  paging: Boolean = false,
) {
  // ksp 按需引入
  apply(plugin = "com.google.devtools.ksp")
  extensions.configure<KspExtension> {
    arg("room.schemaLocation", "${project.projectDir}/schemas") // room 的架构导出目录
    // https://developer.android.com/jetpack/androidx/releases/room#compiler-options
    // 启用 Gradle 增量注释处理器
    arg("room.incremental", "true")
  }
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.androidMain.dependencies {
      implementation(libsEx.`androidx-room`)
      implementation(libsEx.`androidx-room-ktx`)
      if (rxjava) {
        implementation(libsEx.`androidx-room-rxjava`)
      }
      if (paging) {
        implementation(libsEx.`androidx-room-paging`)
      }
    }
  }
  dependencies {
    "kspAndroid"(libsEx.`androidx-room-compiler`)
  }
}

/**
 * 使用网络请求
 */
fun Project.useNetwork() {
  // ksp 按需引入
  apply(plugin = "com.google.devtools.ksp")
  apply(plugin = libsEx.plugins.ktorfit)
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
      implementation(libsEx.`ktor-core`)
      implementation(libsEx.`kmp-ktorfit`)
    }
    sourceSets.androidMain.dependencies {
      implementation(libsEx.retrofit)
      implementation(libsEx.okhttp)
      implementation(libsEx.gson)
      implementation(libsEx.rxjava)
      implementation(libsEx.`rxjava-android`)
      implementation(libsEx.`rxjava-kotlin`)
    }
  }
}

private fun Project.kspMultiplatform(dependencyNotation: Any) {
  dependencies {
    "kspAndroid"(dependencyNotation)
    if (Multiplatform.enableIOS(project)) {
      "kspIosX64"(dependencyNotation)
      "kspIosArm64"(dependencyNotation)
      "kspIosSimulatorArm64"(dependencyNotation)
    }
    if (Multiplatform.enableWasm(project)) {
      "kspWasmJs"(dependencyNotation)
    }
    if (Multiplatform.enableDesktop(project)) {
      "kspDesktop"(dependencyNotation)
    }
  }
}
