package com.cyxbs.pages.noclass.bean

/**
 * 跨平台序列化接口
 *
 * - Android: 实现 java.io.Serializable（支持 Intent.putExtra）
 * - 其他平台: 空标记接口
 */
expect interface PlatformSerializable
