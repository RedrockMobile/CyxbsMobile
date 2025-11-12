package com.cyxbs.pages.map.util

import com.cyxbs.components.init.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 图片本地保存的位置
val mapImageFile = appContext.filesDir.resolve("map_image")

actual fun getImage(): ByteArray? {
  return if (mapImageFile.exists()) mapImageFile.readBytes() else null
}
actual suspend fun loadImage(
  url: String,
  listener: (Long, Long) -> Unit
): ByteArray? = withContext(Dispatchers.IO) {
  runCatching {
    MapImageDownloader.download(url, mapImageFile, listener)
    if (mapImageFile.exists()) {
      mapImageFile.readBytes()
    } else {
      null
    }
  }.getOrElse { e ->
    null
  }
}

actual fun isMapLocalExist(): Boolean = mapImageFile.exists()