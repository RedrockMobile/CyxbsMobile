package com.cyxbs.pages.map.model

import com.cyxbs.pages.map.util.MapImageDownloadResult
import com.cyxbs.pages.map.util.MapImageHelper
import com.cyxbs.pages.map.util.getImageFile
import kotlinx.coroutines.CancellationException

internal data class MapImageLoadRequest(
  val url: String,
  val version: Long,
  val forceDownload: Boolean,
)

internal sealed interface MapImageLoadResult {
  data class Success(
    val bytes: ByteArray,
    val updateAvailable: Boolean,
  ) : MapImageLoadResult

  data class Failure(val cause: Throwable) : MapImageLoadResult
}

internal class MapImageLoader(
  private val readCachedImage: suspend () -> ByteArray? = { getImageFile() },
  private val readCachedVersion: () -> Long? = { MapDataRepository.getMapVersion() },
  private val downloadImage: suspend (String, (Long, Long) -> Unit) -> MapImageDownloadResult =
    { url, listener -> MapImageHelper.downloadImage(url, listener) },
  private val saveVersion: (Long) -> Unit = { MapDataRepository.saveMapVersion(it) },
) {
  suspend fun load(
    request: MapImageLoadRequest,
    onDownloadStart: () -> Unit,
    onProgress: (Float) -> Unit,
  ): MapImageLoadResult {
    try {
      if (!request.forceDownload) {
        val cachedImage = readCachedImage()
        val cachedVersion = readCachedVersion()
        if (cachedImage != null && cachedImage.isNotEmpty() && cachedVersion != null) {
          return MapImageLoadResult.Success(
            bytes = cachedImage,
            updateAvailable = cachedVersion != request.version,
          )
        }
      }

      onDownloadStart()
      val downloadResult = downloadImage(request.url) { bytesSent, contentLength ->
        onProgress((bytesSent.toFloat() / contentLength).coerceIn(0f, 1f))
      }
      if (downloadResult.isCached) {
        saveVersion(request.version)
      }
      return MapImageLoadResult.Success(downloadResult.bytes, updateAvailable = false)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      return MapImageLoadResult.Failure(e)
    }
  }
}
