package com.cyxbs.pages.map.util

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineDispatcher
import okio.Sink

actual val imageFile: PlatformFile
  get() = TODO("Not yet implemented")

actual fun getAbsolutePath(): String {
  return ""
}

actual fun getSink(append: Boolean): Sink {
  TODO("Not yet implemented")
}

actual fun isFileExist(): Boolean {
  TODO("Not yet implemented")
}

actual suspend fun deleteImageFile() {

}

actual suspend fun getImageFile(): ByteArray? {
  return null
}

actual val dispatchersIO: CoroutineDispatcher
  get() = TODO("Not yet implemented")