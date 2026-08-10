import com.google.devtools.ksp.gradle.KspExtension
import npm.configureNpmJsPackaging
import npm.createNpmJsPackageExtension
import npm.validateNpmPackageName
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

plugins {
  kotlin("multiplatform")
  id(libsEx.plugins.ksp)
}

// withPlugin 会同时命中已经应用和之后应用的插件，模块无法通过调整 plugins 声明顺序绕过隔离。
listOf("kmp.compose", "manager.lib").forEach { incompatiblePluginId ->
  pluginManager.withPlugin(incompatiblePluginId) {
    throw GradleException(
      "Plugin 'manager.npmJs' cannot be used together with '$incompatiblePluginId' in $path.",
    )
  }
}

val npmJsPackage = createNpmJsPackageExtension()

/**
 * 可发布到 npm、供端上 JavaScript Runtime 加载的纯 Kotlin/JS 模块约定。
 *
 * npm 包名默认由 Gradle 模块路径生成，也可以通过 `npmJsPackage.packageName` 固定为不受模块移动
 * 影响的稳定坐标；版本固定读取 `project.version`。插件同时启用 npm Service 所需的 KSP、
 * Serialization 与稳定桥协议，模块不得再叠加 `kmp.compose` 或
 * `manager.lib`，避免把端上业务依赖带入 JavaScript 发布物。
 *
 * 发布文件限制为运行代码与类型声明，source map 继续保留在 CI 构建产物中，但不会进入客户端
 * 下载的 npm tgz。
 *
 * 应用插件后会把当前模块自动加入 `:cyxbs-functions:code:npm:distribution` 的聚合编译，并提供：
 * - `prepareNpmJsPackage`：仅生成最终发布目录，用于检查分包结果；不调用 npm、不联网。
 * - `packNpmJsPackage`：按依赖拓扑生成 Runtime、项目依赖与当前包的独立 tgz；不访问 Registry。
 * - `installDebugNpmBundle`：按依赖拓扑生成带时间版本的测试 tgz，ADB 替换私有 debug 源并重启。
 * - `publishNpmJsPackage`：按依赖拓扑保证 Runtime 与项目依赖已发布，再发布当前包。
 *
 * 正常发布只需执行最后一个任务；真机验证执行 install，prepare/pack 用于检查产物与 CI 制品留存。
 */
kotlin {
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

  sourceSets {
    jsTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

kotlin {
  sourceSets.commonMain.dependencies {
    api(project(":cyxbs-functions:code:npm:js-bridge"))
    implementation(libsEx.`kotlinx-serialization`)
  }
}
kspMultiplatform(
  dependencyNotation = project(":cyxbs-compiler:ksp-npm-js-service"),
  targets = setOf(Multiplatform.KspTarget.JS),
)
extensions.configure<KspExtension> {
  // Provider 延迟到模块 build.gradle 完成配置后读取，保证显式稳定包名同时进入 KSP 协议。
  arg("npmJsService.packageName", npmJsPackage.packageName)
}

afterEvaluate {
  val npmPackageName = validateNpmPackageName(npmJsPackage.packageName.get())
  val npmPackageVersion = project.version.toString()
  if (npmPackageVersion == Project.DEFAULT_VERSION) {
    throw GradleException("$path must declare project.version when using manager.npmJs.")
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
