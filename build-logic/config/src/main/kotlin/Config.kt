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
  // 发版有单独的 gradle task，请全局搜索 ReleaseAppTask
  const val versionCode = 90 // 线上89，开发90
  const val versionName = "6.10.2-alpha" // 线上6.10.1，开发6.10.2-alpha，自己打包 -alpha，内测 -beta

  val composeDesktopVersion: String // compose desktop 只能是 x.y.z 形式，不能带 -
    get() = versionName.substringBeforeLast("-")

  val releaseAbiFilters = listOf("arm64-v8a")
  val debugAbiFilters = listOf("arm64-v8a","x86_64")

  // 线上版本更新内容，注意缩进统一
  val updateContent = """
    [bugfix]
    1.修复 6.10.0 初次打开课表白屏问题
    
    [feature]
    1.新增2025年招新banner，让更多邮子认识红岩工作室
    2.全面升级表态广场，Q and A问答为邮子全面解决生活、学习等问题
    3.新增重邮地图导航功能，为邮子探索校园提供全新助力
  """.trimIndent()

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
