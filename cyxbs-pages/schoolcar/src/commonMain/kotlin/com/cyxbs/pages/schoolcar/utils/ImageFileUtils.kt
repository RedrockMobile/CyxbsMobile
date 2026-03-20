package com.cyxbs.pages.schoolcar.utils

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
 * description ： 地图图片文件工具类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 19:45
 */

expect val imageFile: PlatformFile
expect fun getAbsolutePath(): String
expect fun getSink(): Sink
expect fun isFileExist(): Boolean
expect suspend fun deleteImageFile()
expect suspend fun getImageFile(): ByteArray?
expect val dispatchersIO: CoroutineDispatcher

suspend fun downloadMapImage(
	url: String,
	listener: (Long, Long) -> Unit
) {
	withContext(dispatchersIO) {
		try {
			val response = HttpClientNoToken.get(url) {
				this.onDownload { current, total ->
					listener(current, total ?: -1)
				}
			}
			val channel = response.bodyAsChannel()
			getSink().buffer().use {
				val buffer = ByteArray(8 * 1024)
				while (!channel.isClosedForRead) {
					val bytesRead = channel.readAvailable(buffer)
					if (bytesRead < 0) break
					it.write(buffer, 0, bytesRead)
				}
				it.flush()
			}
		} catch (e: Exception) {
			deleteImageFile()
			throw e
		}
	}
}