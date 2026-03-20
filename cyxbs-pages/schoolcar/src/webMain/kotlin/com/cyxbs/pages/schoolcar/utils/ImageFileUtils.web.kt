package com.cyxbs.pages.schoolcar.utils

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineDispatcher
import okio.Sink

actual val imageFile: PlatformFile
	get() = TODO("Not yet implemented")

actual fun getAbsolutePath(): String {
	return ""
}

actual fun getSink(): Sink {
	TODO("Not yet implemented")
}

actual fun isFileExist(): Boolean {
	TODO("Not yet implemented")
}

actual suspend fun deleteImageFile() {
}

actual suspend fun getImageFile(): ByteArray? {
	TODO("Not yet implemented")
}

actual val dispatchersIO: CoroutineDispatcher
	get() = TODO("Not yet implemented")