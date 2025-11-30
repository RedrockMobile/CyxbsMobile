package com.cyxbs.pages.map.util

/**
 * @Desc : 图片的本地存储与加载
 * @Author : zzx
 * @Date : 2025/11/11 18:43
 */

// 用于拿到图片的ByteArray对象
expect suspend fun getImage(): ByteArray?

/**
 * 用于从网络加载图片
 * @param url 网络图片的链接
 * @param listener 监听下载进度
 * @return 返回图片的`ByteArray`对象，如果为null则不存在
 */
expect suspend fun loadImage(
    url: String,
    listener: (bytesRead: Long, contentLength: Long) -> Unit
): ByteArray?

// 判断本地图片缓存是否存在
expect suspend fun isMapLocalExist(): Boolean

// 删除本地图片
expect suspend fun deleteFile()
