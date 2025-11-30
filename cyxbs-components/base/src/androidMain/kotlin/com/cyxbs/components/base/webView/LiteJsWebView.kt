package com.cyxbs.components.base.webView

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.extensions.toast
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.config.isDebug

/**
 * 传入的实现类应该继承 IAndroidWebView,如果仅使用这个自定义类的话，生命周期回调是没有效果的
 * 这个仅支持简单的使用(参考IAndroidWebView),KtProvider 跳转,toast,得到学号,黑夜模式,执行Js代码
 * 其余参考RollerViewActivity实现
 * 一定要调用init方法
 */
class LiteJsWebView : WebView {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int):super(context,attrs,defStyleAttr)
    constructor(context: Context,attrs: AttributeSet?):super(context,attrs)
    constructor(context: Context):super(context)

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun init(
        androidWebView: IAndroidWebView = AndroidWebView(
            exe = {
                //执行Js代码
                this.post {
                    this.evaluateJavascript(it) { }
                }
            },
            toast = {
                //弹toast
                it.toast()
            }
        ),
        onPageFinish: ((url: String?) -> Unit)? = null,
    ) {
        //如果是DEBUG就开启webview的debug
        if (isDebug()) setWebContentsDebuggingEnabled(true)
        //基本配置
        this.settings.apply {
            //支持js
            javaScriptEnabled = true
            //支持 DOM 缓存
            domStorageEnabled = true
            //将图片调整到适合webView的大小
            useWideViewPort = true
            //缩放至屏幕的大小
            loadWithOverviewMode = true
            //支持缩放
            setSupportZoom(true)
            //设置内置的缩放控件。若为false，则该WebView不可缩放
            builtInZoomControls = true
            //隐藏原生的缩放控件
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            //这里必须为false，因为他为true则必须等到用户交互之后才行
            mediaPlaybackRequiresUserGesture = false
        }
        //加载js文件的
        this.addJavascriptInterface(androidWebView, androidWebView::class.simpleName.toString())

        setDownloadListener { downloadUrl, _, _, _, _ ->
            // 直接交给浏览器下载，简单粗暴
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.data = downloadUrl.toUri()
            context.startActivity(intent)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val requestUrl = request.url
                // 如果为http/https的链接则正常加载，如果为qq这种schema部分的则手动拦截进行外部跳转
                if (URLUtil.isHttpsUrl(requestUrl.toString()) || URLUtil.isHttpUrl(requestUrl.toString())) {
                    return super.shouldOverrideUrlLoading(view, request)
                } else {
                    //使用隐式intent跳转qq
                    val intent = Intent(Intent.ACTION_VIEW, requestUrl)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        toast("未安装qq客户端或版本不支持!")
                    }
                    return true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinish?.invoke(url)
            }
        }

        //长按处理各种信息
        setOnLongClickListener { view ->
            val result = (view as WebView).hitTestResult
            val type = result.type

            if (type == WebView.HitTestResult.UNKNOWN_TYPE) return@setOnLongClickListener false
            when (type) {
                HitTestResult.IMAGE_TYPE -> {
                    val imgUrl = result.extra
                }
                //如果是长按超链接就跳转
                HitTestResult.SRC_ANCHOR_TYPE -> {
                    val intent = Intent(Intent.ACTION_VIEW,Uri.parse(result.extra))
                    context.startActivity(intent)
                    return@setOnLongClickListener true
                }
            }
            true
        }

        //这里为什么要用onTouch，因为clickListener收不到，需要提示用户超链接需要长按进入
        setOnTouchListener { view, motionEvent ->
            when(motionEvent.action){
                MotionEvent.ACTION_DOWN ->{
                    val result = (view as WebView).hitTestResult
                    when(result.type){
                        HitTestResult.SRC_ANCHOR_TYPE -> {
                            toast("长按即可跳转到浏览器打开")
                        }
                    }
                }
            }
            false
        }
    }

}