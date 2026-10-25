package com.cyxbs.pages.mine.edit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.posix.memcpy
import kotlin.math.max
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun PlatformFile.prepareAvatarJpeg(): ByteArray = withContext(Dispatchers.Default) {
  require(size() <= 20 * 1024 * 1024) { "请选择小于 20MB 的图片" }
  prepareAvatarJpeg(readBytes())
}

@OptIn(ExperimentalForeignApi::class)
internal fun prepareAvatarJpeg(bytes: ByteArray): ByteArray {
  require(bytes.isNotEmpty()) { "图片内容为空" }
  val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
  val image = requireNotNull(UIImage.imageWithData(data)) { "无法读取该图片" }
  val (width, height) = image.size.useContents { width to height }
  require(width > 0 && height > 0) { "图片尺寸无效" }
  // 固定像素尺寸、居中方形裁剪；drawInRect 同时处理照片的方向信息。
  val side = 512.0
  val scale = max(side / width, side / height)
  val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0 }
  val renderer = UIGraphicsImageRenderer(size = CGSizeMake(side, side), format = format)
  val cropped = renderer.imageWithActions {
    image.drawInRect(CGRectMake((side - width * scale) / 2, (side - height * scale) / 2, width * scale, height * scale))
  }
  val jpeg = requireNotNull(UIImageJPEGRepresentation(cropped, 0.9)) { "图片转换失败" }
  return ByteArray(jpeg.length.toInt()).also { output ->
    output.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
  }
}
