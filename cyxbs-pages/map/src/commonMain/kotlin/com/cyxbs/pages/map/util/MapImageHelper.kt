package com.cyxbs.pages.map.util

import com.cyxbs.components.utils.network.HttpClientNoToken
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Sink
import okio.buffer
import okio.use

/**
 * @Desc : 地图底图图片相关辅助类
 * @Author : zzx
 * @Date : 2025/12/10 14:55
 */

expect val imageFile: PlatformFile
expect fun getAbsolutePath(): String
expect fun getSink(append: Boolean = false): Sink
expect fun isFileExist(): Boolean
expect suspend fun deleteImageFile()
expect suspend fun getImageFile(): ByteArray?
expect val dispatchersIO: CoroutineDispatcher

/**
 * 地图下载的返回结果
 * @param bytes: 地图的字节流
 * @param isCached: 是否写入缓存
 */
data class MapImageDownloadResult(
  val bytes: ByteArray,
  val isCached: Boolean,
)

object MapImageHelper {
  suspend fun downloadImage(
    url: String,
    listener: (Long, Long) -> Unit
  ): MapImageDownloadResult = withContext(dispatchersIO) {
    try {
      val bytes = HttpClientNoToken.get(url.toHttpsCdnUrl()) {
        onDownload { bytesSentTotal, contentLength ->
          listener(bytesSentTotal, contentLength ?: 1L)
        }
      }.body<ByteArray>()
      if (bytes.isEmpty()) error("Map image is empty")
      // 是否写入缓存，同样使用runCatching包住
      val isCached = runCatching {
        getSink().buffer().use { sink ->
          sink.write(bytes)
          sink.flush()
        }
        getImageFile()?.isNotEmpty() == true
      }.onFailure {
        if (isFileExist()) {
          runCatching { deleteImageFile() }
        }
      }.getOrDefault(false)
      MapImageDownloadResult(bytes, isCached)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // 用runCatching包住，删除缓存作为兜底，失败也不抛出异常
      if (isFileExist()) {
        runCatching { deleteImageFile() }
      }
      throw e
    }
  }

  /**
   * 后端返回的为http开头，这里转成https的
   * 因为苹果的ATS不允许http，这里暂时转换一下
   */
  private fun String.toHttpsCdnUrl(): String {
    return if (startsWith("http://")) {
      replaceFirst("http://", "https://")
    } else {
      this
    }
  }
}