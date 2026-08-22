import com.google.devtools.ksp.gradle.KspExtension
import npm.configureNpmJsPackaging
import npm.createNpmJsPackageExtension
import npm.validateNpmPackageName
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.kotlin.dsl.configure
import rule.ModuleNamespaceCheckRule

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

// withPlugin 会同时命中已经应用和之后应用的插件，模块无法通过调整 plugins 声明顺序绕过隔离。
listOf("kmp.compose", "manager.lib").forEach { incompatiblePluginId ->
  pluginManager.withPlugin(incompatiblePluginId) {
    throw GradleException(
      "Plugin 'manager.npmJsBridge' cannot be used together with '$incompatiblePluginId' in $path.",
    )
  }
}

val npmJsPackage = createNpmJsPackageExtension()
val hostKspTargets = Multiplatform.KspTarget.NON_WEB
val serviceKspTargets = setOf(Multiplatform.KspTarget.JS)

/**
 * npm JavaScript 动态实现与端上 Kotlin 调用方共同依赖的跨平台协议桥约定。
 *
 * Bridge 只负责声明稳定接口和 DTO，因此不引入完整业务依赖。npm 包名默认由 Gradle 模块路径
 * 生成，也可以通过 `npmJsPackage.packageName` 固定为不受模块移动影响的稳定坐标；版本固定读取
 * `project.version`。插件自动启用 npm Service 所需的 Serialization、KSP、
 * KtProvider 与端上代理依赖，并禁止叠加 `kmp.compose` 或 `manager.lib`。
 * 底层 `:cyxbs-functions:code:npm:js-bridge` 只定义注解和基础协议，因此会跳过反向依赖自身的
 * Service 代理配置，但仍使用相同的平台及 npm 坐标约定。每个 Bridge 模块也会注册独立 npm
 * 分包任务，供其他 JavaScript 包以精确版本依赖，而不会把 Bridge 实现复制进自身产物。
 */
kotlin {
  android {
    namespace = ModuleNamespaceCheckRule.getCorrectNamespace(project)
    compileSdk = libsEx.versions.`android-compileSdk`.toInt()
    minSdk = libsEx.versions.`android-minSdk`.toInt()
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
    }
  }
  if (Multiplatform.enableDesktop(project)) {
    jvm("desktop") {
      compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
      }
    }
  }
  if (Multiplatform.enableIOS(project)) {
    iosArm64()
    iosSimulatorArm64()
  }
  js {
    nodejs()
    binaries.library()
    useEsModules()
    generateTypeScriptDefinitions()
    compilations["main"].packageJson {
      customField("type", "module")
      customField("files", listOf("**/*.mjs", "**/*.d.mts"))
    }
  }
  if (Multiplatform.enableWeb(project)) {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      nodejs()
    }
  }

  sourceSets {
    // 端上代理代码共享同一个 noWebMain，JS/Wasm 只消费 commonMain 中的稳定协议。
    val noWebMain = create("noWebMain") {
      dependsOn(commonMain.get())
    }
    androidMain { dependsOn(noWebMain) }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting { dependsOn(noWebMain) }
      val desktopTest by getting
      desktopTest.dependencies {
        implementation(kotlin("test"))
      }
    }
    if (Multiplatform.enableIOS(project)) {
      val iosMain = create("iosMain") {
        dependsOn(noWebMain)
      }
      iosArm64Main { dependsOn(iosMain) }
      iosSimulatorArm64Main { dependsOn(iosMain) }
    }
  }
}

if (path != ":cyxbs-functions:code:npm:js-bridge") {
  // 底层 js-bridge 定义注解和协议本身，不能反向依赖自己；业务 Bridge 才启用代理生成链路。
  useKtProvider(
    isNeedKsp = true,
    kspTargets = hostKspTargets,
    dependencySourceSetName = "noWebMain",
  )
  kotlin {
    sourceSets.commonMain.dependencies {
      api(project(":cyxbs-functions:code:npm:js-bridge"))
      implementation(libsEx.`kotlinx-serialization`)
    }
    sourceSets.getByName("noWebMain").dependencies {
      implementation(project(":cyxbs-functions:code:npm"))
    }
  }
  kspMultiplatform(
    dependencyNotation = project(":cyxbs-compiler:ksp-npm-js-service"),
    targets = serviceKspTargets,
  )
  kspMultiplatform(
    dependencyNotation = project(":cyxbs-compiler:ksp-npm-js-host"),
    targets = hostKspTargets,
  )
  extensions.configure<KspExtension> {
    // Provider 延迟到模块 build.gradle 完成配置后读取，保证显式稳定包名同时进入 KSP 协议。
    arg("npmJsService.packageName", npmJsPackage.packageName)
  }
} else {
  // 基础模块只声明跨端协议；JS 目标生成 Service 侧代码，不能反向依赖 Host 实现。
  apply(plugin = libsEx.plugins.ksp)
  kspMultiplatform(
    dependencyNotation = project(":cyxbs-compiler:ksp-npm-js-service"),
    targets = serviceKspTargets,
  )
}

afterEvaluate {
  val npmPackageName = validateNpmPackageName(npmJsPackage.packageName.get())
  val npmPackageVersion = project.version.toString()
  if (npmPackageVersion == Project.DEFAULT_VERSION) {
    throw GradleException("$path must declare project.version when using manager.npmJsBridge.")
  }
  kotlin {
    js {
      compilations["main"].packageJson {
        name = npmPackageName
        version = npmPackageVersion
      }
    }
  }
}

// Kotlin/JS production distribution 已完成声明后再注册读取该任务的分包链路。
configureNpmJsPackaging()
