package com.cyxbs.components.base.webView

import android.os.Handler
import android.os.Message
import android.webkit.JavascriptInterface
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.service.startActivity

/**
 * 新增的AndroidWebView直接继承这个类
 * 具体写参考AndroidWebView
 * 高阶函数不用管，直接继承重写就可以
 * 如果出现白屏问题，原因可能是Js代码中调用了这边没有实现的代码
 * 注意继承类需要防止被混淆，否则Js对应关键字就是a了
 */
abstract class IAndroidWebView(
    private val handler: Handler?,
    private val lExeJs: (String) -> Unit = {},
    private val lToast: (String) -> Unit = {}
    ) : WebViewBaseCallBack {

    var onLoadStr: String? = null
    var sensorIDs: ArrayList<Int> = ArrayList()

    //调用这个方法来保存图片
    @JavascriptInterface
    open fun savePic(url: String) {
        val message = Message()
        message.what = 0
        message.obj = url
        handler?.sendMessage(message)
    }

    /**
     * 初始化背景音乐
     * @musicJs 播放音乐的Js命令
     */
    @JavascriptInterface
    open fun onLoad(onLoadJs: String) {
        onLoadStr = onLoadJs
    }

    /**
     * 初始化传感器
     * ID 1 加速度传感器
     * ID 4 陀螺仪传感器
     */
    @JavascriptInterface
    open fun initSensor(sensorId: Int) {
        sensorIDs.add(sensorId)
    }

    /**
     * 通过 KtProvider 进行端内跳转
     * 传入 RouteTable.kt 表中的路徑
     */
    @JavascriptInterface
    open fun jump(path: String) {
        startActivity(path)
    }

    /**
     * 传入Js到Android来执行
     */
    @JavascriptInterface
    open fun exeJs(str: String) {
        lExeJs.invoke(str)
    }

    /**
     * 弹toast
     */
    @JavascriptInterface
    open fun toast(str: String) {
        lToast.invoke(str)
    }

    /**
     * 获得当前登录人的学号
     */
    @JavascriptInterface
    open fun getStu(): String {
        return IAccountService::class.impl().stuNum.orEmpty()
    }

    @JavascriptInterface
    open fun isDark(): Boolean {
        return appContext.applicationContext.resources.configuration.uiMode == 0x21
    }
}