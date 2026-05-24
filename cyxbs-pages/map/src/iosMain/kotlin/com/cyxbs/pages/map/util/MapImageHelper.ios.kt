package com.cyxbs.pages.map.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink

actual val imageFile: PlatformFile
  get() = FileKit.filesDir / "map_image.jpg"

actual fun getAbsolutePath(): String {
  return imageFile.absolutePath()
}

actual fun getSink(append: Boolean): Sink {
  val path = getAbsolutePath().toPath()
  return if (append) {
    FileSystem.SYSTEM.appendingSink(path)
  } else {
    FileSystem.SYSTEM.sink(path)
  }
}

actual fun isFileExist(): Boolean {
  return imageFile.exists()
}

actual suspend fun deleteImageFile() = withContext(Dispatchers.Default) {
  imageFile.delete()
}

actual suspend fun getImageFile(): ByteArray? {
  return if (isFileExist()) imageFile.readBytes() else null
}

actual val dispatchersIO: CoroutineDispatcher
  get() = Dispatchers.Default
