package release.step

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Source
import okio.source
import release.net.TaskService
import java.io.File
import java.lang.Exception
import java.util.Scanner
import kotlin.time.Duration.Companion.milliseconds

/**
 * apk 上传
 *
 * @author 985892345
 * @date 2025/9/21
 */
class ApkUploadStep(val netService: TaskService) {

  // 返回 apk 下载链接
  fun execute(apk: File): String? {
    println("\n======================== apk 上传 ========================".purple())
    return uploadApk(apk)
  }

  /**
   * 上传 apk，返回 apk 下载链接
   */
  private fun uploadApk(apk: File): String? {
    println("开始上传 apk: $apk".bold())
    val startTime = System.currentTimeMillis().milliseconds
    val filePart = MultipartBody.Part.createFormData(
      "file", apk.name,
      ProgressRequestBody(
        apk,
        "application/octet-stream".toMediaType()
      ) { bytesRead: Long, contentLength: Long ->
        val progress = bytesRead * 100 * 100000 / contentLength / 100000.0 // 保留 5 位小数
        val diffTime = System.currentTimeMillis().milliseconds - startTime
        println("上传进度: $bytesRead/$contentLength" +
            " (${progress.toString().padEnd(7, '0')}%)" +
            "   耗时 $diffTime" +
            "   预计剩余时间 ${diffTime / progress * (100 - progress)}")
      }
    )
    var count = 0
    while (true) {
      try {
        val response = netService.uploadApk(filePart).execute()
        if (!response.isSuccessful) throw RuntimeException("apk 请求失败: " +
            "message: ${response.message()}, code: ${response.code()}, body: ${response.errorBody()?.bytes()?.let { String(it) }}")
        val apkUploadData = response.body()!!
        if (apkUploadData.ok) {
          println("✅ 上传 apk 成功，新版本下载链接为: " + apkUploadData.data.yellow())
          return apkUploadData.data
        } else {
          throw RuntimeException("请求成功，但返回异常: ${response.raw().body?.bytes()?.let { String(it) }}")
        }
      } catch (e: Exception) {
        println("❌ 上传失败，异常如下: ".red())
        e.printStackTrace()
        println()
        val sc = Scanner(System.`in`)
        println("\n是否再次尝试上传? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消重试".red())
          return null
        }
        println("重试开始，这是第 ${++count} 次重试：".purple())
      }
    }
  }

  // 显示上传 apk 进度
  private class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType,
    private val listener: (bytesRead: Long, contentLength: Long) -> Unit
  ) : RequestBody() {

    override fun contentType(): MediaType? {
      return contentType
    }

    override fun contentLength(): Long {
      return file.length()
    }

    override fun writeTo(sink: BufferedSink) {
      val source: Source = file.source()
      var totalBytesRead = 0L
      val buffer = okio.Buffer()
      var bytesRead: Long

      while (source.read(buffer, 8192).also { bytesRead = it } != -1L) {
        totalBytesRead += bytesRead
        sink.write(buffer, bytesRead)
        // 回调进度
        listener.invoke(totalBytesRead, contentLength())
      }
      source.close()
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