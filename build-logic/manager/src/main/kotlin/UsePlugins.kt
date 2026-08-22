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
 *
 * @param isNeedKsp 是否启用 KtProvider 的 KSP 生成器；关闭时只添加运行时 API 依赖。
 * @param kspTargets KSP 生成器需要参与的 KMP 编译目标，默认保持所有目标参与的既有行为。
 * @param dependencySourceSetName KtProvider API 写入的源集；协议模块可指定 `noWebMain`，避免依赖
 * 进入只消费协议的 JS/Wasm 目标。
 */
fun Project.useKtProvider(
  isNeedKsp: Boolean = !name.startsWith("api"),
  kspTargets: Set<Multiplatform.KspTarget> = Multiplatform.KspTarget.ALL,
  dependencySourceSetName: String = "commonMain",
) {
  if (isNeedKsp) {
    // kapt 按需引入
    apply(plugin = libsEx.plugins.ksp)
    apply(plugin = libsEx.plugins.ktProvider)
    val ktProvider = extensions.getByName("ktProvider") as KtProviderExtensions
    kspMultiplatform(ktProvider.ksp, kspTargets)
  }
  // AGP9 后 application 插件不能与 multiplatform 共存
  // 所以这里需要先判断 KotlinMultiplatformExtension 是否存在 (仅 application 模块需要，lib 模块默认包含多平台插件，无需这样配置)
  extensions.findByType(KotlinMultiplatformExtension::class)?.apply {
    sourceSets.getByName(dependencySourceSetName).dependencies {
      implementation(libsEx.`kmp-ktProvider-api`)
    }
  } ?: dependencies {
    "implementation"(libsEx.`kmp-ktProvider-api`)
  }
}

/**
 * 为普通 `manager.lib` 模块启用 npm JavaScript Host 侧代码生成。
 *
 * 协议模块应直接使用 `manager.npmJsBridge`；只有 Host 实现位于普通业务 lib 的特殊场景才调用
 * 本函数。它只向非 Web 目标安装 KSP 与 KtProvider，不创建 JS target，也不参与 npm 发布。
 */
fun Project.useNpmJsHost() {
  useKtProvider(
    isNeedKsp = true,
    kspTargets = Multiplatform.KspTarget.NON_WEB,
    dependencySourceSetName = "noWebMain",
  )
  kspMultiplatform(
    dependencyNotation = project(":cyxbs-compiler:ksp-npm-js-host"),
    targets = Multiplatform.KspTarget.NON_WEB,
  )
}

/**
 * 使用 Room，已默认支持与 Kt 协程一起使用
 *
 * @param rxjava 依赖 room-rxjava
 * @param paging 依赖 room-paging
 * @param kspTargets Room 编译器参与的 KMP 编译目标；当前项目默认只在 Android 使用 Room。
 * @param dependencySourceSetName Room 运行时依赖写入的源集，默认保持现有 `androidMain` 行为。
 */
fun Project.useRoom(
  rxjava: Boolean = false,
  paging: Boolean = false,
  kspTargets: Set<Multiplatform.KspTarget> = setOf(Multiplatform.KspTarget.ANDROID),
  dependencySourceSetName: String = "androidMain",
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
    sourceSets.getByName(dependencySourceSetName).dependencies {
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
  kspMultiplatform(libsEx.`androidx-room-compiler`, kspTargets)
}

/**
 * 使用网络请求
 *
 * - 编译期会自动关联上 KtProvider，所以需要同时引入 useKtProvider()
 *
 * @param kspTargets 网络接口生成器参与的 KMP 编译目标。
 * @param dependencySourceSetName Ktor 等跨平台网络依赖写入的源集；Android 专属实现仍固定写入
 * `androidMain`。
 */
fun Project.useNetwork(
  kspTargets: Set<Multiplatform.KspTarget> = Multiplatform.KspTarget.ALL,
  dependencySourceSetName: String = "commonMain",
) {
  // ksp 按需引入
  apply(plugin = libsEx.plugins.ksp)
  apply(plugin = libsEx.plugins.ktorfit)
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName(dependencySourceSetName).dependencies {
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
  kspMultiplatform(project(":cyxbs-compiler:ksp-network"), kspTargets)
}

/**
 * 使用 navigation3
 *
 * - 编译期会自动关联上 KtProvider，所以需要同时引入 useKtProvider()
 * - 每个 @AppNav 类会输出输出一份 deeplink 报告，最后会汇总到 `<app>/AppNav.md`
 *
 * @param isNeedKsp 是否启用 Navigation KSP；API 模块默认只依赖导航协议。
 * @param kspTargets Navigation 生成器参与的 KMP 编译目标。
 * @param dependencySourceSetName Navigation 运行时依赖写入的源集。
 */
fun Project.useNavigation(
  isNeedKsp: Boolean = !name.startsWith("api"),
  kspTargets: Set<Multiplatform.KspTarget> = Multiplatform.KspTarget.ALL,
  dependencySourceSetName: String = "commonMain",
) {
  if (isNeedKsp) {
    apply(plugin = libsEx.plugins.ksp)
    extensions.configure<KspExtension> {
      arg("appNav.modulePath", project.path)
    }
    kspMultiplatform(project(":cyxbs-compiler:ksp-navigation"), kspTargets)
  }
  extensions.findByType(KotlinMultiplatformExtension::class)?.apply {
    sourceSets.getByName(dependencySourceSetName).dependencies {
      implementation(project(":cyxbs-components:navigation"))
    }
  }
}

/**
 * 为约定插件和显式工具函数向指定 KMP 目标添加同一份 KSP 处理器。
 *
 * [targets] 表示编译目标而非中间源集；不存在的目标 configuration 会被跳过。非 KMP 工程仍使用
 * 标准 `ksp` configuration，此时目标集合不参与筛选。
 */
internal fun Project.kspMultiplatform(
  dependencyNotation: Any,
  targets: Set<Multiplatform.KspTarget> = Multiplatform.KspTarget.ALL,
) {
  val isMultiplatform = extensions.findByType(KotlinMultiplatformExtension::class) != null
  dependencies {
    if (isMultiplatform) {
      if (Multiplatform.KspTarget.ANDROID in targets &&
        configurations.findByName("kspAndroid") != null
      ) {
        "kspAndroid"(dependencyNotation)
      }
      if (Multiplatform.KspTarget.IOS in targets) {
        if (configurations.findByName("kspIosArm64") != null) {
          "kspIosArm64"(dependencyNotation)
        }
        if (configurations.findByName("kspIosSimulatorArm64") != null) {
          "kspIosSimulatorArm64"(dependencyNotation)
        }
      }
      if (Multiplatform.KspTarget.JS in targets && configurations.findByName("kspJs") != null) {
        "kspJs"(dependencyNotation)
      }
      if (Multiplatform.KspTarget.WASM_JS in targets &&
        configurations.findByName("kspWasmJs") != null
      ) {
        "kspWasmJs"(dependencyNotation)
      }
      if (Multiplatform.KspTarget.DESKTOP in targets &&
        configurations.findByName("kspDesktop") != null
      ) {
        "kspDesktop"(dependencyNotation)
      }
    } else {
      "ksp"(dependencyNotation)
    }
  }
}
