@file:Suppress("ObjectPropertyName")

import org.gradle.api.Project
import java.util.regex.Pattern

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/26 15:13
 */
object Config {
  /** 从 [project] 的根工程读取 Android versionCode；缺失或非法时让配置阶段失败。 */
  fun versionCode(project: Project): Int = project.rootProject.providers
    .gradleProperty("cyxbs.versionCode").get().toInt()

  /** 从 [project] 的根工程读取版本名，供各应用模块共用。 */
  fun versionName(project: Project): String = project.rootProject.providers
    .gradleProperty("cyxbs.versionName").get()

  /** 从 [project] 根目录的独立文本文件读取发布文案，保留 PR 正文中的换行。 */
  fun updateContent(project: Project): String = project.rootProject.providers
    .fileContents(project.rootProject.layout.projectDirectory.file("build-logic/release-notes.txt"))
    .asText.get().trimEnd()

  /** 从 [project] 的版本名去掉预发布后缀，供 Compose Desktop 打包。 */
  fun composeDesktopVersion(project: Project): String = versionName(project).substringBefore("-")

  /**
   * 返回 release 包需要携带的 ABI。CI 通过显式参数构建 x86_64 测试包；正常发版始终只携带 arm64-v8a。
   * 该参数只允许作用于正式应用模块，避免改变其他模块的发布产物。
   */
  fun releaseAbiFilters(project: Project): List<String> {
    val ciX86Release = project.providers.gradleProperty("cyxbs.ciReleaseX86_64")
      .orNull?.toBooleanStrict() == true
    return if (ciX86Release && project.path == ":cyxbs-applications:pro") {
      listOf("x86_64")
    } else {
      listOf("arm64-v8a")
    }
  }
  val debugAbiFilters = listOf("arm64-v8a","x86_64")

  val resourcesExclude = listOf(
    "LICENSE.txt",
    "META-INF/DEPENDENCIES",
    "/META-INF/{AL2.0,LGPL2.1}",
    "META-INF/NOTICE",
    "META-INF/LICENSE",
    "META-INF/LICENSE.txt",
    "META-INF/services/javax.annotation.processing.Processor",
    "META-INF/MANIFEST.MF",
    "META-INF/NOTICE.txt",
    "META-INF/rxjava.properties",
    "**/schemas/**", // 用于取消数据库的导出文件
  )
  
  val jniExclude = listOf(
    "lib/armeabi/libAMapSDK_MAP_v6_9_4.so",
    "lib/armeabi/libsophix.so",
    "lib/armeabi/libBugly.so",
    "lib/armeabi/libpl_droidsonroids_gif.so",
    "lib/*/libRSSupport.so",
    "lib/*/librsjni.so",
    "lib/*/librsjni_androidx.so",
  )
  
  fun getApplicationId(project: Project): String {
    return when (project.path) {
      ":cyxbs-applications:pro" -> {
        if (project.gradle.startParameter.taskNames.any { it.contains("Release") }) {
          "com.mredrock.cyxbs"
        } else {
          // debug 状态下使用 debug 的包名，方便测试
          "com.mredrock.cyxbs.debug"
//          "com.mredrock.cyxbs" // 取消注释即可还原包名，但注意：取消注释后需要点一下右上角的大象刷新 gradle 才能生效
        }
      }
      else -> "com.mredrock.cyxbs.${project.name}"
    }
  }

  fun getBaseName(project: Project): String {
    return project.path.split(Pattern.compile("-|:|_")).joinToString("") { name ->
      name.replaceFirstChar { it.uppercaseChar() }
    }
  }
}
