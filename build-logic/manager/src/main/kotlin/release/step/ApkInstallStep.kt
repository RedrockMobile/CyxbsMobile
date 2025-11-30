package release.step

import localProperties
import org.gradle.api.Project
import java.io.File
import java.util.Scanner

/**
 * 安装 apk 以保证能正常启动
 *
 * @author 985892345
 * @date 2025/9/21
 */
class ApkInstallStep(val project: Project) {

  fun execute(apk: File): Boolean {
    println("\n======================== 安装检查 ========================".purple())

    if (!installApk(apk)) {
      return false
    }
    if (!confirm()) {
      return false
    }
    return true
  }

  private val adb = project.localProperties["sdk.dir"].toString() + File.separator + "platform-tools" + File.separator + "adb"

  private fun installApk(apk: File): Boolean {
    while (true) {
      val installResult = project.providers.exec {
        // adb install 安装
        commandLine(adb, "install", "-r", apk)
        isIgnoreExitValue = true
      }
      if (installResult.result.get().exitValue != 0) {
        println(installResult.standardError.asText.get())
        println("❌ 安装失败，请检查设备是否正常连接".red())
        println()
        val sc = Scanner(System.`in`)
        println("\n是否再次尝试安装? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消重试".red())
          return false
        }
      } else {
        break
      }
    }
    return true
  }

  private fun confirm(): Boolean {
    println("✅ apk 已安装, ".bold() + "请按如下步骤进行严格的检查！！！".red())
    var index = 0
    var result = check("${index++}. 覆盖安装后能否继承登录状态?", listOf())
    result = result && check("${index++}. 首页轮播图是否正常加载出图片?", null)
    result = result && check("${index++}. 即将打开课表，请确认能否正常打开并显示?", listOf("-a", "com.mredrock.cyxbs.action.COURSE"))
    result = result && check("${index++}. 即将强制触发更新弹窗，请确认更新弹窗能正常弹出且能跳转下载? " + "(最核心功能)".red(), listOf("-a", "com.mredrock.cyxbs.action.TEST_UPDATE_DIALOG"))
    return result
  }

  private fun check(title: String, action: List<String>?): Boolean {
    val sc = Scanner(System.`in`)
    println("\n$title (y/n)".red())
    if (action != null) {
      project.providers.exec {
        // adb shell am start 打开 app
        commandLine(
          adb, "shell", "am", "start",
          "-p", Config.getApplicationId(project),
          *action.toTypedArray()
        )
        isIgnoreExitValue = true
      }.result.get()
    }
    val nextLine = sc.nextLine()
    if (nextLine != "y") {
      println("❌ 失败，取消发版".red())
      return false
    }
    return true
  }

  private fun String.red() = "\u001B[31m$this\u001B[0m"
  private fun String.green() = "\u001B[32m$this\u001B[0m"
  private fun String.yellow() = "\u001B[33m$this\u001B[0m"
  private fun String.blue() = "\u001B[34m$this\u001B[0m"
  private fun String.purple() = "\u001B[35m$this\u001B[0m"
  private fun String.cyan() = "\u001B[36m$this\u001B[0m"
  private fun String.white() = "\u001B[37m$this\u001B[0m"
  private fun String.bold() = "\u001B[1m$this\u001B[0m"
}