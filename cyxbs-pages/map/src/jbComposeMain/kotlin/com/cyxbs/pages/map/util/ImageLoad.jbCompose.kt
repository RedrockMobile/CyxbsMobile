package com.cyxbs.pages.map.util

actual suspend fun getImage(): ByteArray? {
  return null
}

actual suspend fun loadImage(
  url: String,
  listener: (Long, Long) -> Unit
) : ByteArray? {
  return null
}

actual suspend fun isMapLocalExist(): Boolean {
  return false
}

actual suspend fun deleteFile() {
}