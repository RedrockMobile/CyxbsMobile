package release.step

import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.Project
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Scanner
import kotlin.time.Duration.Companion.milliseconds

/**
 * apk 下载并验证
 *
 * @author 985892345
 * @date 2025/9/21
 */
class ApkDownloadStep(
  val project: Project,
  val originOkHttpClient: OkHttpClient,
) {

  fun execute(apk: File, apkUrl: String): Boolean {
    println("\n======================== apk 下载 ========================".purple())
    val downloadApk = downloadApk(apk, apkUrl)
    if (downloadApk == null) {
      return false
    }
    return verifyApk(apk, downloadApk)
  }

  private fun downloadApk(apk: File, apkUrl: String): File? {
    val request = Request.Builder()
      .url(apkUrl)
      .build()
    val downloadFile = apk.parentFile.resolve("download").resolve(apk.name)
    var count = 0
    while (true) {
      try {
        originOkHttpClient.newCall(request).execute().use { response ->
          val responseBody = response.body

          downloadFile.parentFile?.mkdirs()

          val totalBytes = responseBody.contentLength()

          FileOutputStream(downloadFile).use { outputStream ->
            val source = responseBody.source()
            val buffer = okio.Buffer()
            var totalBytesRead = 0L

            val startTime = System.currentTimeMillis().milliseconds

            while (!source.exhausted()) {
              val bytesRead = source.read(buffer, 8192)
              if (bytesRead == -1L) break

              outputStream.write(buffer.readByteArray())
              totalBytesRead += bytesRead

              // 计算并回调进度
              if (totalBytes > 0) {
                val progress = totalBytesRead * 100 * 100000 / totalBytes / 100000.0 // 保留 5 位小数
                val diffTime = System.currentTimeMillis().milliseconds - startTime
                println("下载进度: $totalBytesRead/$totalBytes" +
                    " (${progress.toString().padEnd(7, '0')}%)" +
                    "   耗时 $diffTime" +
                    "   预计剩余时间 ${diffTime / progress * (100 - progress)}")
              }
            }

            outputStream.flush()
          }
        }
        println("✅ 下载成功".bold())
        return downloadFile
      } catch (e: Exception) {
        println("❌ 下载失败，异常如下: ".red())
        e.printStackTrace()
        downloadFile.delete()
        println()
        val sc = Scanner(System.`in`)
        println("\n是否再次尝试下载? (y/n)".red())
        val nextLine = sc.nextLine()
        if (nextLine != "y") {
          println("❌ 取消重试".red())
          return null
        }
        println("重试开始，这是第 ${++count} 次重试：".purple())
      }
    }
  }

  // 效验本地 apk 与远端 apk 的文件哈希值
  private fun verifyApk(originFile: File, downloadApk: File): Boolean {
    println("正在效验 apk 文件...".bold())
    val originFileHash = calculateFileHashes(originFile)
    val downloadFileHash = calculateFileHashes(downloadApk)
    if (originFileHash.md5 == downloadFileHash.md5) {
      println("✅ MD5: " + originFileHash.md5.yellow() + "  ->  " + downloadFileHash.md5.yellow())
    } else {
      println("❌ MD5: " + originFileHash.md5.yellow() + "  ->  " + downloadFileHash.md5.red())
      println("❌ 效验失败".red())
      return false
    }
    if (originFileHash.sha1 == downloadFileHash.sha1) {
      println("✅ SHA1: " + originFileHash.sha1.yellow() + "  ->  " + downloadFileHash.sha1.yellow())
    } else {
      println("❌ SHA1: " + originFileHash.sha1.yellow() + "  ->  " + downloadFileHash.sha1.red())
      println("❌ 效验失败".red())
      return false
    }
    if (originFileHash.sha256 == downloadFileHash.sha256) {
      println("✅ SHA256: " + originFileHash.sha256.yellow() + "  ->  " + downloadFileHash.sha256.yellow())
    } else {
      println("❌ SHA256: " + originFileHash.sha256.yellow() + "  ->  " + downloadFileHash.sha256.red())
      println("❌ 效验失败".red())
      return false
    }
    println("✅ 效验成功".green())
    return true
  }

  /**
   * 同时计算多种哈希值
   */
  private fun calculateFileHashes(file: File): FileHashes {
    val md5Digest = MessageDigest.getInstance("MD5")
    val sha1Digest = MessageDigest.getInstance("SHA-1")
    val sha256Digest = MessageDigest.getInstance("SHA-256")

    file.inputStream().use { inputStream ->
      val buffer = ByteArray(8192)
      var bytesRead: Int

      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        md5Digest.update(buffer, 0, bytesRead)
        sha1Digest.update(buffer, 0, bytesRead)
        sha256Digest.update(buffer, 0, bytesRead)
      }
    }

    return FileHashes(
      md5 = md5Digest.digest().joinToString("") { "%02x".format(it) },
      sha1 = sha1Digest.digest().joinToString("") { "%02x".format(it) },
      sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) }
    )
  }

  data class FileHashes(
    val md5: String,
    val sha1: String,
    val sha256: String
  )

  private fun String.red() = "\u001B[31m$this\u001B[0m"
  private fun String.green() = "\u001B[32m$this\u001B[0m"
  private fun String.yellow() = "\u001B[33m$this\u001B[0m"
  private fun String.blue() = "\u001B[34m$this\u001B[0m"
  private fun String.purple() = "\u001B[35m$this\u001B[0m"
  private fun String.cyan() = "\u001B[36m$this\u001B[0m"
  private fun String.white() = "\u001B[37m$this\u001B[0m"
  private fun String.bold() = "\u001B[1m$this\u001B[0m"
}