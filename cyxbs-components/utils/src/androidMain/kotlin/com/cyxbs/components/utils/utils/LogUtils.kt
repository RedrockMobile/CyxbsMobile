package com.cyxbs.components.utils.utils

import android.util.Log
import com.cyxbs.components.config.isDebug
import kotlin.experimental.and

/**
 * log工具
 * Created by Jay on 2017/10/10.
 * 这个Log工具打印的Log只会在Debug的时候会出现
 */

object LogUtils {
    private val DEBUG = isDebug()
    private val SHOW_LOG: Byte
    private const val VERBOSE_MASK: Byte = 0x1
    private const val DEBUG_MASK: Byte = 0x2
    private const val INFO_MASK: Byte = 0x4
    private const val WARN_MASK: Byte = 0x8
    private const val ERROR_MASK: Byte = 0x10

    init {
        SHOW_LOG = if (DEBUG) 0x1F else 0x10
    }

    fun v(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        if (SHOW_LOG and VERBOSE_MASK == VERBOSE_MASK)
            Log.v(tag, msg, throwable)
    }

    fun d(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        if (SHOW_LOG and DEBUG_MASK == DEBUG_MASK)
            Log.d(tag, msg, throwable)
    }

    fun i(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        if (SHOW_LOG and INFO_MASK == INFO_MASK)
            Log.i(tag, msg, throwable)
    }

    fun w(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        if (SHOW_LOG and WARN_MASK == WARN_MASK)
            Log.w(tag, msg, throwable)
    }

    fun e(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        if (SHOW_LOG and ERROR_MASK == ERROR_MASK)
            Log.e(tag, msg, throwable)
    }
}
