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


  private val Project.localProperties: Properties
    get() {
      val key = "Project.localProperties"
      if (rootProject.extra.has(key)) {
        return rootProject.extra.get(key) as Properties
      } else {
        val properties = Properties()
        val localPropertiesFile = rootProject.rootDir.resolve("local.properties")
        if (localPropertiesFile.exists()) {
          properties.load(localPropertiesFile.inputStream())
        }
        rootProject.extra.set(key, properties)
        return properties
      }
    }
}