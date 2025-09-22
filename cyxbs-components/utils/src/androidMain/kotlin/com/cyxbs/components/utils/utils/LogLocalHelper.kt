package com.cyxbs.components.utils.utils

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class LogLocalHelper(
    private val pid: String,
    private val filePath: String,
    private val fileName: String
) : Thread() {
    private var file: File = File(filePath)
    private var out: FileOutputStream? = null
    private var isSupportEncrypt = true

    init {
        //创建文件夹
        if (!file.exists()) {
            file.mkdirs()
        }
        //删缓存
        remake()
        //写入
        val file = File(filePath, fileName)
        out = FileOutputStream(file, true)
        //加密
        try {
            val encrypt = LogLocalAES.encrypt("1234567890123456", "abcdefg")
            LogUtils.d("LogLocal", "密码 $encrypt")
            val decrypt = LogLocalAES.dencrypt("1234567890123456", encrypt)
            LogUtils.d("LogLocal", "加密功能正常 密码 $decrypt")
        } catch (e: Exception) {
            LogUtils.e("LogLocal", "无法加密！！", e)
            isSupportEncrypt = false
        }
    }

    fun write(str: String) {
        try {
            out?.write("${encrypt(pid+str)}\n".toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            out?.apply {
                close()
                out = null
            }
        }
    }

    /**
     * @lines 是达到多少行之后执行,
     * @multiple 是执行，从多少开始删,如果是3的话，就只剩下1/3
     */
    private fun remake(lines: Int = 1000, multiple: Int = 2) {
        if (File(filePath, fileName).exists()) {
            val inputStream: InputStream = File(filePath, fileName).inputStream()
            val readLines = inputStream.bufferedReader().readLines()
            if (readLines.size > lines) {
                File(filePath, fileName).delete()
                val outRemake = FileOutputStream(File(filePath, fileName))
                val subList =
                    readLines.subList(readLines.size * (1 - (1 / multiple)), readLines.size)
                subList.forEach {
                    outRemake.write(it.toByteArray())
                }
                outRemake.close()
            }
            inputStream.close()
        }
    }

    private fun encrypt(str: String): String {
        return if (!isSupportEncrypt) {
            str
        } else {
            LogLocalAES.encrypt(getPassword(), str)
        }
    }


    private fun decrypt(str: String): String {
        return if (!isSupportEncrypt) {
            str
        } else try {
            LogLocalAES.dencrypt(getPassword(), str)
        } catch (e: Exception) {
            LogUtils.e("LogLocal", "无法加密！！", e)
            ""
        }
    }

    private fun getPassword(): String {
        var password = "1234567890123456"
        val stuNum = IAccountService::class.impl().stuNum.orEmpty()
        if (stuNum.length < 16) {
            password = stuNum
            for (i in 0 until (16 - stuNum.length)) {
                password += "0"
            }
        } else {
            password = stuNum.substring(0, 16)
        }
        return password
    }
}

