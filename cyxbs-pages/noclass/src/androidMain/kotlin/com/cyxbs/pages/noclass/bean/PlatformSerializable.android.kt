package com.cyxbs.pages.noclass.bean

/**
 * Android 平台上实现 java.io.Serializable
 * 使得数据类可被 Intent.putExtra 使用
 */
actual interface PlatformSerializable : java.io.Serializable
