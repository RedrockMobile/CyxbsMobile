package com.cyxbs.components.utils.utils

import android.os.Process
import com.cyxbs.components.config.dir.DIR_LOG
import com.cyxbs.components.config.dir.OKHTTP_LOCAL_LOG
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 *
 * 以前廖老师写的本地收集报错文件
 *
 * 在 关于我们 底部文字长按即可触发本地收集报错文件
 *
 * 解密请看飞书知识库
 *
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2022/8/10
 * @Description:
 */
object LogLocal {
    private var logLocalHelper: LogLocalHelper? = null
    private val filePath: String = "${appContext.filesDir.absolutePath}$DIR_LOG/"
    private val pid = Process.myPid()
    fun log(tag: String = "tag", msg: String, throwable: Throwable? = null) {
        Observable.create<String> {
            it.onNext("$tag $msg")
        }
            .subscribeOn(Schedulers.io())
            .map {
                if (logLocalHelper == null) {
                    logLocalHelper = LogLocalHelper(pid.toString(), filePath, OKHTTP_LOCAL_LOG)
                }
                logLocalHelper?.write(it)
                Unit
            }.unsafeSubscribeBy {}
    }
}