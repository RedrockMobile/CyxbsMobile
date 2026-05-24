package com.cyxbs.pages.schoolcar.utils

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
  get() = FileKit.filesDir / "schoolcar_map.png"

actual fun getAbsolutePath(): String {
  return imageFile.absolutePath()
}

actual fun getSink(): Sink {
  return FileSystem.SYSTEM.sink(getAbsolutePath().toPath())
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
