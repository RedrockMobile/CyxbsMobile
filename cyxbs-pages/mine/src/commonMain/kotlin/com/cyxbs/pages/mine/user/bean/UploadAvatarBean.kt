package com.cyxbs.pages.mine.user.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 头像上传接口返回结构（PUT /magipoke/person/upload/avatar）
 *
 * ⚠️ 注意后端字段命名不一致：返回的是 `photosrc`（中间没下划线）和 `thumbnail_src`，
 * 而 PUT /person/info 接口传参时又叫 `photo_src`（带下划线）。
 * 字段名以后端实际返回为准，不要按"猜测的命名风格"统一。
 */
@Serializable
data class UploadAvatarBean(
  @SerialName("photosrc")
  val photoSrc: String,
)
