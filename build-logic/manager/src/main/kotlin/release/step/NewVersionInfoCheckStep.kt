package release.step

import org.gradle.api.Project
import release.net.ReleaseData
import release.net.TaskService
import java.io.File
import java.util.Scanner

/**
 * 发版信息检查
 *
 * @author 985892345
 * @date 2025/9/21
 */
class NewVersionInfoCheckStep(val project: Project, val netService: TaskService) {

  fun execute(apk: File): Boolean {
    println("\n======================== 发版信息检查 ========================".purple())
    if (!apk.exists()) {
      throw IllegalArgumentException("未找到 apk 文件, $apk")
    }
    if (!apk.isFile) {
      throw IllegalArgumentException("apk 文件不存在, $apk")
    }
    if (!(apk.name.endsWith(".apk") || apk.name.endsWith(".Apk"))) {
      throw IllegalArgumentException("apk 文件格式错误, $apk")
    }
    val lastVersion = getUpdateContent(netService)
    println("线上下载链接: " + lastVersion.apkUrl)
    println("线上 updateContent: ")
    println(Config.updateContent.blue())
    println()
    println("versionName: " + lastVersion.versionName.blue() + "  ->  " + Config.versionName.yellow())
    println("versionCode: " + lastVersion.versionCode.toString().blue() + "  ->  " + Config.versionCode.toString().yellow())
    println("updateContent: ")
    println(Config.updateContent.yellow())
    return checkVersion(lastVersion)
  }

  private fun checkVersion(lastVersion: ReleaseData): Boolean {
    //忘记改updateContent和versionName的情况
    if (lastVersion.versionCode > Config.versionCode) {
      throw IllegalArgumentException("没改版本号 versionCode，线上为 " + lastVersion.versionCode.toString().blue()
          + ", 即将发布的新版为 " + Config.versionCode.toString().yellow())
    } else if (lastVersion.versionCode == Config.versionCode) {
      if (project.findProperty("force") == "TRUE") {
        val sc = Scanner(System.`in`)
        println("\n是否确定 强制 发布相同版本以覆盖线上（更推荐发布新版来解决）? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消发版".red())
          return false
        }
        return true
      } else {
        throw IllegalArgumentException("当前版本已发布，如果是因为发布错误或者需要重新覆盖安装包，" +
            "请在终端中手动执行: ./gradlew cyxbs-application:pro:cyxbsRelease -P force=\"TRUE\"")
      }
    } else if (lastVersion.versionCode + 1 != Config.versionCode) {
      throw IllegalArgumentException("versionCode 存在跳跃，线上为 " + lastVersion.versionCode.toString().blue()
          + ", 即将发布的新版为 " + Config.versionCode.toString().yellow())
    } else {
      if (lastVersion.versionName == Config.versionName) {
        throw IllegalArgumentException("改了 versionCode 却没改 versionName, 线上为 " + lastVersion.versionName.blue()
            + ", 即将发布的新版为 " + Config.versionName.yellow())
      } else if (lastVersion.updateContent == Config.updateContent) {
        throw IllegalArgumentException("没改更新的文案，去找产品要一个，然后写在 Config#updateContent 中")
      } else if (!lastVersion.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
        throw IllegalArgumentException("versionName 不符合规则，只能为 x.y.z")
      } else if (compareVersion(lastVersion.versionName, Config.versionName) >= 0) {
        throw IllegalArgumentException("versionName 版本号低于或等于线上版本号")
      } else {
        val sc = Scanner(System.`in`)
        println("\n是否确定发版? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消发版".red())
          return false
        }
        return true
      }
    }
  }


  /**
   * 获取线上版本信息
   */
  private fun getUpdateContent(netService: TaskService): ReleaseData {
    val response = netService.getUpdateContent().execute()
    if (!response.isSuccessful) throw RuntimeException("获取更新信息失败: " +
        "message: ${response.message()}, code: ${response.code()}")
    return response.body()!!
  }

  /**
   * https://leetcode.cn/problems/compare-version-numbers/description/
   * 时间复杂度: O(n+m)
   * 空间复杂度: O(1)
   */
  private fun compareVersion(version1: String, version2: String): Int {
    val n = version1.length
    val m = version2.length
    var i = 0
    var j = 0
    while (i < n || j < m) {
      var x = 0
      while (i < n && version1[i] != '.') {
        x = x * 10 + (version1[i] - '0')
        i++
      }
      i++ // 跳过点号
      var y = 0
      while (j < m && version2[j] != '.') {
        y = y * 10 + (version2[j] - '0')
        j++
      }
      j++ // 跳过点号
      if (x != y) {
        return if (x > y) 1 else -1
      }
    }
    return 0
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