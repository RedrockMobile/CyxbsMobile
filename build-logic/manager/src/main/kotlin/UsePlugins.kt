import com.g985892345.provider.plugin.gradle.extensions.KtProviderExtensions
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
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
    apply(plugin = libsEx.plugins.ksp)
    apply(plugin = libsEx.plugins.ktProvider)
    val ktProvider = extensions.getByName("ktProvider") as KtProviderExtensions
    kspMultiplatform(ktProvider.ksp)
  }
  // AGP9 后 application 插件不能与 multiplatform 共存
  // 所以这里需要先判断 KotlinMultiplatformExtension 是否存在 (仅 application 模块需要，lib 模块默认包含多平台插件，无需这样配置)
  extensions.findByType(KotlinMultiplatformExtension::class)?.apply {
    sourceSets.commonMain.dependencies {
      implementation(libsEx.`kmp-ktProvider-api`)
    }
  } ?: dependencies {
    "implementation"(libsEx.`kmp-ktProvider-api`)
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
  apply(plugin = libsEx.plugins.ksp)
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
 *
 * - 编译期会自动关联上 KtProvider，所以需要同时引入 useKtProvider()
 */
fun Project.useNetwork() {
  // ksp 按需引入
  apply(plugin = libsEx.plugins.ksp)
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
  // Ktorfit 每次使用都要先触发 KSP task 才会生成实现类
  // 这里编译期关联上 KtProvider，以后只需要 XXXApi::class.impl() 就可以直接获取到实现类了
  kspMultiplatform(project(":cyxbs-compiler:ksp-network"))
}

/**
 * 使用 navigation3
 *
 * - 编译期会自动关联上 KtProvider，所以需要同时引入 useKtProvider()
 * - 每个 @AppNav 类会输出输出一份 deeplink 报告，最后会汇总到 `<app>/AppNav.md`
 */
fun Project.useNavigation(isNeedKsp: Boolean = !name.startsWith("api")) {
  if (isNeedKsp) {
    apply(plugin = libsEx.plugins.ksp)
    extensions.configure<KspExtension> {
      arg("appNav.modulePath", project.path)
    }
    kspMultiplatform(project(":cyxbs-compiler:ksp-navigation"))
  }
  extensions.findByType(KotlinMultiplatformExtension::class)?.apply {
    sourceSets.commonMain.dependencies {
      implementation(project(":cyxbs-components:navigation"))
    }
  }
}

private fun Project.kspMultiplatform(dependencyNotation: Any) {
  val isMultiplatform = extensions.findByType(KotlinMultiplatformExtension::class) != null
  dependencies {
    if (isMultiplatform) {
      if (configurations.findByName("kspAndroid") != null) {
        "kspAndroid"(dependencyNotation)
      }
      if (configurations.findByName("kspIosArm64") != null) {
        "kspIosArm64"(dependencyNotation)
      }
      if (configurations.findByName("kspIosSimulatorArm64") != null) {
        "kspIosSimulatorArm64"(dependencyNotation)
      }
      if (configurations.findByName("kspJs") != null) {
        "kspJs"(dependencyNotation)
      }
      if (configurations.findByName("kspWasmJs") != null) {
        "kspWasmJs"(dependencyNotation)
      }
      if (configurations.findByName("kspDesktop") != null) {
        "kspDesktop"(dependencyNotation)
      }
    } else {
      "ksp"(dependencyNotation)
    }
  }
}
