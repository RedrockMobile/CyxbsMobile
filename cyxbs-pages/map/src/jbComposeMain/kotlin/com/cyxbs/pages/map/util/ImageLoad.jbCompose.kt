package com.cyxbs.pages.map.util

actual fun getImage(): ByteArray? {
  return null
}

actual suspend fun loadImage(
  url: String,
  listener: (Long, Long) -> Unit
) : ByteArray? {
  return null
}

actual fun isMapLocalExist(): Boolean {
  return false
}