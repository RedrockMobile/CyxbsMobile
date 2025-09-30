package release.step

import org.gradle.api.Project
import java.io.ByteArrayOutputStream
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

    if (!checkDevices()) {
      return false
    }
    if (!installApk(apk)) {
      return false
    }
    if (!confirm()) {
      return false
    }
    return true
  }

  private fun checkDevices(): Boolean {
    while (true) {
      val stdout = ByteArrayOutputStream()
      val stderr = ByteArrayOutputStream()
      val execResult = runCatching {
        project.providers.exec {
          commandLine("adb", "devices")
          standardOutput = stdout
          errorOutput = stderr
          isIgnoreExitValue = true
        }.result.get()
      }.onFailure {
        it.printStackTrace()
      }.getOrNull()
      if (execResult?.exitValue == 0) {
        val lines = stdout.toString().lines()
        val deviceLines = lines.filter {
          it.isNotBlank() && !it.startsWith("List of devices") && it.contains("\t")
        }
        if (deviceLines.isNotEmpty()) {
          println("已连接设备如下: ")
          deviceLines.forEach {
            println(it)
          }
        }
        if (deviceLines.all { !it.endsWith("\tdevice") }) {
          println("❌ 未检测到任何可用设备连接，请连接安卓设备以进行发版前的安装检查".red())
          Thread.sleep(2000)
        } else {
          break
        }
      } else {
        println("❌ adb 异常: $stderr".red())
        println("❌ 请确保已正确配置 adb 环境变量".red())
        println("❌ 如果终端有 adb 命令，则可能是 AS 抽风了，重启一下".red())
        return false
      }
    }
    return true
  }

  private fun installApk(apk: File): Boolean {
    while (true) {
      val installResult = project.providers.exec {
        // adb install 安装
        commandLine("adb", "install", "-r", apk)
        isIgnoreExitValue = true
      }.result.get()
      if (installResult.exitValue != 0) {
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
    println("✅ apk 已安装，请检查课表是否正常显示，5秒后将进行确认".bold())
    project.providers.exec {
      // adb shell am start 打开 app
      commandLine(
        "adb", "shell", "am", "start",
        "-a", "com.mredrock.cyxbs.action.COURSE", // 启动就直接打开课表的 action
        "-p", Config.getApplicationId(project), // 启动的包名
      )
    }
    Thread.sleep(5000)
    val sc = Scanner(System.`in`)
    println("\n请确定课表功能正常，能否正常显示课程? (y/n)".red())
    val nextLine = sc.nextLine()
    if (nextLine != "y") {
      println("❌ 取消发版".red())
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