package com.mredrock.cyxbs.common.network.exception

import com.cyxbs.components.utils.extensions.toast
import com.mredrock.cyxbs.common.BuildConfig
import com.mredrock.cyxbs.common.utils.LogUtils
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 只处理简单的网络异常及账号密码错误
 * Created By jay68 on 2018/8/12.
 */
@Deprecated("检测状态已过时，请不要再使用")
object DefaultErrorHandler : ErrorHandler {
    override fun handle(e: Throwable?): Boolean {
        when {
            e == null -> toast(
                if (BuildConfig.DEBUG) throw NullPointerException("throwable must be not null") else "未知错误"
            )
            e is SocketTimeoutException || e is ConnectException || e is UnknownHostException -> toast(
                if (BuildConfig.DEBUG) e.toString() else "网络中断，请检查您的网络状态"
            )
            e is HttpException -> toast(
                if (BuildConfig.DEBUG) e.response()?.raw().toString() else "此服务暂时不可用"
            )

            e is RedrockApiIllegalStateException -> toast("数据异常，请稍后再试")

            e.message.equals("authentication error") -> toast("登录失败：学号或者密码错误,请检查输入")

            e.message.equals("student id error") -> toast("登录失败：学号不存在,请检查输入")

            e.message.equals("jwzx return invalid data") -> toast(
                "服务暂时不可用：教务在线维护中..."
            )

            e.message != null && BuildConfig.DEBUG -> toast("error: ${e.message}")

            else -> LogUtils.e("DefaultErrorHandler", "onError", e)
        }
        return true
    }
}