package com.cyxbs.pages.schoolcar.utils

import okio.ByteString.Companion.encodeUtf8

/**
 * description ： md5加密
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 23:36
 */

fun md5Hex(s: String): String =s.encodeUtf8().md5().hex()