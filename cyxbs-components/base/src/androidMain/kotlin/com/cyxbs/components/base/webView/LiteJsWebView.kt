package com.cyxbs.components.base.webView

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.utils.extensions.toast

/**
 * 传入的实现类应该继承 IAndroidWebView,如果仅使用这个自定义类的话，生命周期回调是没有效果的
 * 这个仅支持简单的使用(参考IAndroidWebView),KtProvider 跳转,toast,得到学号,黑夜模式,执行Js代码
 * 一定要调用init方法
 *
 * 推荐使用 [init] 的 LifecycleOwner 重载，会自动接管生命周期、传感器、onLoad 注入等
 */
class LiteJsWebView : WebView {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int):super(context,attrs,defStyleAttr)
    constructor(context: Context,attrs: AttributeSet?):super(context,attrs)
    constructor(context: Context):super(context)

    private var sensorManager: SensorManager? = null
    private val sensorListeners = mutableListOf<SensorEventListener>()

    // 缓存当前 Window 的 systemBar 间距（dp）。JS 在后台线程调 getSystemBarInsets() 时同步读取。
    // 在 attach 之后和每次 onResume 时刷新；全屏隐藏 systemBars 后值会变 0，此时跳过以保留全屏前缓存
    @Volatile
    private var cachedSystemBarInsetsJson: String = IAndroidWebView.DEFAULT_INSETS_JSON

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

    /**
     * 进阶用法：自动接管生命周期、传感器注册、onLoad 注入、网页标题回调、url 加载、返回键回退
     *
     * 在 Compose 中调用：
     * - [lifecycleOwner] 用 androidx.lifecycle.compose.LocalLifecycleOwner 获取
     * - [onBackPressedDispatcher] 用 LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher 获取
     *
     * @param lifecycleOwner 用于自动 resumeTimers/pauseTimers/destroy 以及 onPause 时取消传感器
     * @param url 加载的网址
     * @param onBackPressedDispatcher 自动绑定返回键回退：有历史则 goBack，否则交回系统
     * @param onSavePic Js 调用 savePic(url) 时回调，由调用方自行处理（如弹对话框 + 申请权限）
     * @param onSetFullscreen Js 调用 setFullscreen 时回调，由 Activity 切换沉浸式与标题栏
     * @param onReceivedTitle 网页标题回调，传 null 表示不监听
     * @param onPageFinish 页面加载完成后的额外回调（onLoad 注入和传感器注册由内部完成）
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun init(
        lifecycleOwner: LifecycleOwner,
        url: String,
        onBackPressedDispatcher: OnBackPressedDispatcher,
        onSavePic: (url: String) -> Unit = {},
        onSetFullscreen: (fullscreen: Boolean) -> Unit = {},
        onReceivedTitle: ((title: String) -> Unit)? = null,
        onPageFinish: ((url: String?) -> Unit)? = null,
    ) {
        val androidWebView = AndroidWebView(
            onSavePic = onSavePic,
            exe = { js -> this.post { this.evaluateJavascript(js) {} } },
            toast = { it.toast() },
            onSetFullscreen = onSetFullscreen,
            getSystemBarInsets = { cachedSystemBarInsetsJson },
        )
        init(
            androidWebView = androidWebView,
            onPageFinish = { finishedUrl ->
                androidWebView.onLoadStr?.let { js -> post { evaluateJavascript(js) {} } }
                registerSensors(androidWebView)
                onPageFinish?.invoke(finishedUrl)
            },
        )
        if (onReceivedTitle != null) {
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String) {
                    super.onReceivedTitle(view, title)
                    onReceivedTitle(title)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                resumeTimers()
                androidWebView.webViewResume()
                refreshSystemBarInsets()
            }

            override fun onPause(owner: LifecycleOwner) {
                androidWebView.webViewPause()
                pauseTimers()
                unregisterSensors()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                androidWebView.webViewDestroy()
                destroy()
            }
        })
        onBackPressedDispatcher.addCallback(lifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack()) {
                    goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        doOnAttach { post { refreshSystemBarInsets() } }
        loadUrl(url)
    }

    /**
     * 主线程读取当前 Window 根 insets，更新缓存。全屏隐藏 systemBars 后值会全为 0，
     * 此时跳过以保留全屏前的缓存，避免前端拿到错误的 0。
     */
    private fun refreshSystemBarInsets() {
        val rootInsets = ViewCompat.getRootWindowInsets(this) ?: return
        val sys = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (sys.top == 0 && sys.bottom == 0 && sys.left == 0 && sys.right == 0) return
        val density = resources.displayMetrics.density
        cachedSystemBarInsetsJson = """{"top":${(sys.top / density).toInt()},"bottom":${(sys.bottom / density).toInt()},"left":${(sys.left / density).toInt()},"right":${(sys.right / density).toInt()}}"""
    }

    private fun registerSensors(androidWebView: IAndroidWebView) {
        if (androidWebView.sensorIDs.isEmpty()) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        androidWebView.sensorIDs.forEach { id ->
            when (id) {
                Sensor.TYPE_GYROSCOPE -> {
                    val gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return@forEach
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            evaluateJavascript(
                                "window.gyroscope('${event.values[0]}','${event.values[1]}','${event.values[2]}')"
                            ) {}
                        }
                        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
                    }
                    sm.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
                    sensorListeners.add(listener)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@forEach
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            evaluateJavascript(
                                "window.accelerometer('${event.values[0]}','${event.values[1]}','${event.values[2]}')"
                            ) {}
                        }
                        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
                    }
                    sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                    sensorListeners.add(listener)
                }
            }
        }
    }

    private fun unregisterSensors() {
        val sm = sensorManager ?: return
        sensorListeners.forEach { sm.unregisterListener(it) }
        sensorListeners.clear()
    }
}