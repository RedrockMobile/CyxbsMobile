package com.cyxbs.pages.map.util

import com.cyxbs.components.utils.network.HttpClientNoToken
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
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

object MapImageHelper {
  suspend fun downloadImage(
    url: String,
    listener: (Long, Long) -> Unit
  ) = withContext(dispatchersIO) {
    try {
      val response = HttpClientNoToken.get(url) {
        onDownload { bytesSentTotal, contentLength ->
          listener(bytesSentTotal, contentLength ?: 1L)
        }
      }
      val channel = response.bodyAsChannel()
      getSink().buffer().use { sink ->
        val buffer = ByteArray(8 * 1024)
        while (!channel.isClosedForRead) {
          val bytesRead = channel.readAvailable(buffer)
          if (bytesRead < 0) break
          sink.write(buffer, 0, bytesRead)
        }
        sink.flush()
      }
    } catch (e: Exception) {
      deleteImageFile()
      throw e
    }
  }
}