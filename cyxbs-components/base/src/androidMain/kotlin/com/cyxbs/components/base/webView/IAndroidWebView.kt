package com.cyxbs.components.base.webView

import android.webkit.JavascriptInterface
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.init.appContext
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity

/**
 * 新增的AndroidWebView直接继承这个类
 * 具体写参考AndroidWebView
 * 高阶函数不用管，直接继承重写就可以
 * 如果出现白屏问题，原因可能是Js代码中调用了这边没有实现的代码
 * 注意继承类需要防止被混淆，否则Js对应关键字就是a了
 */
abstract class IAndroidWebView(
    private val onSavePicCallback: (url: String) -> Unit = {},
    private val lExeJs: (String) -> Unit = {},
    private val lToast: (String) -> Unit = {},
    private val onSetFullscreenCallback: (fullscreen: Boolean) -> Unit = {},
    private val getSystemBarInsetsProvider: () -> String = { DEFAULT_INSETS_JSON },
    ) : WebViewBaseCallBack {

    var onLoadStr: String? = null
    var sensorIDs: ArrayList<Int> = ArrayList()

    //调用这个方法来保存图片
    @JavascriptInterface
    open fun savePic(url: String) {
        onSavePicCallback(url)
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

    /**
     * 前端通知客户端进入/退出全屏（隐藏 systemBar 和标题栏）
     *
     * 使用：`window.AndroidWebView.setFullscreen(true)`
     */
    @JavascriptInterface
    open fun setFullscreen(fullscreen: Boolean) {
        onSetFullscreenCallback(fullscreen)
    }

    /**
     * 客户端把当前 systemBar 的安全间距返回给前端用于自适应布局（单位：dp，与 CSS px 大体等价）
     *
     * 使用：
     * ```js
     * const insets = JSON.parse(window.AndroidWebView.getSystemBarInsets())
     * // insets => { top: 24, bottom: 48, left: 0, right: 0 }
     * ```
     * 注意：进入 [setFullscreen] 后 systemBar 被隐藏，此处仍返回原本被遮挡的间距，
     * 方便前端把页面元素错开刘海/挖孔/导航条区域。
     *
     * 值为 -1 表示未加载，请稍后再读取
     */
    @JavascriptInterface
    open fun getSystemBarInsets(): String {
        return getSystemBarInsetsProvider()
    }

    /**
     * 返回当前应用的 token，如果不存在则返回空串 ""
     *
     * 使用：`window.AndroidWebView.getToken()`
     */
    @JavascriptInterface
    open fun getToken(): String {
        return ITokenService::class.impl().getToken().orEmpty()
    }

    companion object {
        const val DEFAULT_INSETS_JSON = """{"top":-1,"bottom":-1,"left":-1,"right":-1}"""
    }
}