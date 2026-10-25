package com.cyxbs.pages.discover.home.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 校历 CDN 元数据。
 *
 * [cdnUrl] 是当前校历图片地址，[picVersion] 是后端维护的图片版本。两者共同决定图片缓存身份：
 * 任一字段变化都必须绕过旧缓存并重新下载。
 */
@Serializable
data class SchoolCalendarCdnBean(
  @SerialName("cdn_url")
  val cdnUrl: String,
  @SerialName("pic_version")
  val picVersion: Int,
)

/**
 * 生成 Coil 的跨平台缓存键。
 *
 * 同时包含 URL 与版本号，确保后端只更新其中任一字段时立即产生新键；两者都不变时持续复用缓存。
 */
internal fun SchoolCalendarCdnBean.imageCacheKey(): String =
  "discover_school_calendar:$picVersion:$cdnUrl"

/**
 * 给 CDN 请求地址附加版本参数。
 *
 * 参数插入 fragment 之前，并兼容地址已经带 query 的情况；这样即使底层网络缓存忽略 Coil 的自定义
 * 缓存键，也能在版本变化后重新请求图片。
 */
internal fun SchoolCalendarCdnBean.versionedImageUrl(): String {
  val fragmentIndex = cdnUrl.indexOf('#')
  val urlWithoutFragment = if (fragmentIndex >= 0) cdnUrl.substring(0, fragmentIndex) else cdnUrl
  val fragment = if (fragmentIndex >= 0) cdnUrl.substring(fragmentIndex) else ""
  val separator = if ('?' in urlWithoutFragment) '&' else '?'
  return "$urlWithoutFragment${separator}cyxbs_calendar=$picVersion$fragment"
}
