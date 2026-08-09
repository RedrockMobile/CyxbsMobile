import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import org.gradle.internal.os.OperatingSystem
import java.util.Properties

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/21
 */
object Multiplatform {

  /**
   * KMP 中可独立挂载 KSP 处理器的编译目标。
   *
   * KSP 不会为 `noWebMain` 等中间源集创建 configuration，因此 [IOS] 会映射到当前项目支持的
   * 两个 iOS 架构，调用方应按目标集合选择处理器真正需要参与的编译链路。
   */
  enum class KspTarget {
    ANDROID,
    IOS,
    DESKTOP,
    JS,
    WASM_JS,
    ;

    companion object {
      /** 所有受支持的 KSP 编译目标，保持旧版无差别注册行为。 */
      val ALL: Set<KspTarget> = entries.toSet()

      /** Android、iOS 与 Desktop 组成的端上目标集合。 */
      val NON_WEB: Set<KspTarget> = ALL - setOf(JS, WASM_JS)
    }
  }

  fun enableIOS(project: Project): Boolean {
    if (!OperatingSystem.current().isMacOsX) return false // 目前只有 Mac 系统才能跑起来
    val key = "cyxbs.multiplatform.ios"
    return (project.localProperties[key] ?: project.rootProject.properties[key]) == "true"
  }

  fun enableWeb(project: Project): Boolean {
    val key = "cyxbs.multiplatform.web"
    return (project.localProperties[key] ?: project.rootProject.properties[key]) == "true"
  }

  fun enableDesktop(project: Project): Boolean {
    val key = "cyxbs.multiplatform.desktop"
    return (project.localProperties[key] ?: project.rootProject.properties[key]) == "true"
  }

  // 运行 Android 的任务
  fun runAndroid(project: Project): Boolean {
    return project.gradle.startParameter.taskNames.any {
      it.contains("assembleRelease")
          || it.contains("assembleDebug")
          || it == "channelRelease"
          || it == "channelDebug"
          || it == "cyxbsRelease"
    }
  }

  // 运行 Desktop 的任务
  fun runDesktop(project: Project): Boolean {
    return project.gradle.startParameter.taskNames.any {
      it.contains("desktop")
          || it.contains("package")
    }
  }

  // 运行 WasmJs 的任务
  fun runWasmJs(project: Project): Boolean {
    return project.gradle.startParameter.taskNames.any {
      it.contains("wasmJs")
    }
  }

}
