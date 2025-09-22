package release.step

import release.net.ReleaseData
import release.net.TaskService
import java.util.Scanner

/**
 * 上传新版本信息
 *
 * @author 985892345
 * @date 2025/9/21
 */
class UploadNewVersionInfoStep(val netService: TaskService) {

  fun execute(apkUrl: String): ReleaseData? {
    println("\n======================== 发布新版本信息 ========================".purple())
    val releaseData = postUpdateContent(apkUrl)
    if (releaseData != null) {
      println("✅ 发版成功!".green())
      println("versionName: " + Config.versionName.yellow())
      println("versionCode: " + Config.versionCode.toString().yellow())
      println("updateContent: ")
      println(Config.updateContent.yellow())
    }
    return releaseData
  }

  /**
   * 上传更新信息
   */
  private fun postUpdateContent(apkUrl: String): ReleaseData? {
    val data = ReleaseData(
      apkUrl = apkUrl,
      updateContent = Config.updateContent,
      versionCode = Config.versionCode,
      versionName = Config.versionName
    )
    while (true) {
      try {
        val response = netService.postUpdateContent(data).execute()
        if (!response.isSuccessful) throw RuntimeException("发版信息上传失败: " +
            "message: ${response.message()}, code: ${response.code()}")
        return response.body()!!
      } catch (e: Exception) {
        println("❌ 发版信息上传失败: ".red())
        e.printStackTrace()
        println()
        val sc = Scanner(System.`in`)
        println("\n是否再次尝试发布新版本信息? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消重试".red())
          return null
        }
      }
    }
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