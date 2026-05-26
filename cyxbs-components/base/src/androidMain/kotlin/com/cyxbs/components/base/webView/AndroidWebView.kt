package com.cyxbs.components.base.webView

import androidx.annotation.Keep


/**
 * 如果是要暴露给js调用的接口 请加上 [@JavascriptInterface] 注释
 */
@Keep
class AndroidWebView(
    onSavePic: (url: String) -> Unit = {},
    exe: (String) -> Unit = {},
    toast: (String) -> Unit = {},
    onSetFullscreen: (fullscreen: Boolean) -> Unit = {},
    getSystemBarInsets: () -> String = { IAndroidWebView.DEFAULT_INSETS_JSON },
) : IAndroidWebView(onSavePic, exe, toast, onSetFullscreen, getSystemBarInsets) {


    override fun webViewResume() {

    }

    override fun webViewPause() {

    }

    override fun webViewDestroy() {

    }


}