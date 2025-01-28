package com.cyxbs.pages.discover.pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.cyxbs.components.config.dir.DIR_PHOTO
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.pages.discover.BuildConfig
import com.cyxbs.pages.discover.R
import com.cyxbs.pages.discover.network.RollerViewInfo
import com.cyxbs.pages.discover.pages.discover.webView.WebViewFactory
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.webView.IAndroidWebView
import com.cyxbs.components.base.webView.LiteJsWebView
import com.cyxbs.components.base.webView.WebViewBaseCallBack
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.loadBitmap
import com.cyxbs.components.utils.extensions.saveImage


class RollerViewActivity : BaseActivity() {

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 0) {
                //因为保存图片需要权限,WebView类没有持有的权限
                savePic(msg.obj as String)
            }
        }
    }

    //这里是拿到衍生的Web方法类
    private var webApi: IAndroidWebView? = null

    private val callback: WebViewBaseCallBack? = webApi

    //传感器(方便remove)
    private val sm: SensorManager? = null
    private var sensorEventListeners: ArrayList<SensorEventListener>? = null
    
    private val discover_web_view by R.id.discover_web_view.view<LiteJsWebView>()
    private val common_toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.discover_activity_roller_view)
        setTheme(com.google.android.material.R.style.Theme_MaterialComponents) //这里是用的Material主题
        //如果是DEBUG就开启webview的debug
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        val url = intent.getStringExtra("URL")

        webApi = url?.let {
            WebViewFactory(
                it,handler, {
                    //这里是调用的Js的传进来的Js代码
                    discover_web_view.post {
                        discover_web_view.evaluateJavascript(it) { }
                    }
                },
                {
                    it.toast()
                }).produce()
        }

        webApi?.apply {
            discover_web_view.init(this)
        }
        //加载网页
        discover_web_view.loadUrl(url.toString())
        //设置几个webview的监听
        discover_web_view.webChromeClient = object : WebChromeClient() {
            //加载的时候会拿到网页的标签页名字
            override fun onReceivedTitle(view: WebView?, title: String) {
                //拿到web的标题，并设置,可以判断是否使用后端下发的标题
                if (title != "") {
                    common_toolbar.init(this@RollerViewActivity, title)
                } else {
                    intent.getStringExtra("Key")?.let { common_toolbar.init(this@RollerViewActivity, title) }
                }
                super.onReceivedTitle(view, title)
            }
        }
        discover_web_view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return super.shouldOverrideUrlLoading(view, url)
            }

            //这里是页面加载完之后调用
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                //在加载完之后，获得js中init中调用的方法
                //这里需要Js代码中初始化，否则无法启动
                initBgm()
                initSensor()
            }
        }
        discover_web_view.setDownloadListener { downloadUrl, _, _, _, _ ->
            // 直接交给浏览器下载，简单粗暴
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.data = Uri.parse(downloadUrl)
            startActivity(intent)
        }
        //长按处理各种信息
        discover_web_view.setOnLongClickListener { view ->
            val result = (view as WebView).hitTestResult
            val type = result.type

            if (type == WebView.HitTestResult.UNKNOWN_TYPE) return@setOnLongClickListener false
            when (type) {
                WebView.HitTestResult.IMAGE_TYPE -> {
                    val imgUrl = result.extra
                }
                //如果是长按超链接就跳转
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    val intent = Intent(Intent.ACTION_VIEW,Uri.parse(result.extra))
                    startActivity(intent)
                    return@setOnLongClickListener true
                }
            }
            true
        }
        //这里为什么要用onTouch，因为clickListener收不到，需要提示用户超链接需要长按进入
        discover_web_view.setOnTouchListener { view, motionEvent ->
            when(motionEvent.action){
                MotionEvent.ACTION_DOWN ->{
                    val result = (view as WebView).hitTestResult
                    when(result.type){
                        WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                            toast("长按即可跳转到浏览器打开")
                        }
                    }
                }
            }
            false
        }
    }

    private fun initBgm() {
        //使用Web端传入的js命令
        discover_web_view.post {
            webApi?.onLoadStr?.let { discover_web_view.evaluateJavascript(it) { } }
        }
    }

    /**
     * 这里是传感器
     * 通过 window.accelerometer(int1,int2,int3)
     * 和window.gyroscope(int1,int2,int3)
     * 把他传出去
     */
    private fun initSensor() {
        //如果没有就退出
        if (webApi?.sensorIDs?.size == 0) return
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorEventListeners = ArrayList()
        webApi?.sensorIDs?.forEach {
            when (it) {
                Sensor.TYPE_GYROSCOPE -> {
                    val gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                    gyroscope?.also { gyroscope ->
                        val value = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                //调用Js代码，把参数传过去
                                discover_web_view.evaluateJavascript("window.gyroscope('${event.values[0]}','${event.values[1]}','${event.values[2]}')") {

                                }
                            }

                            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

                            }
                        }
                        sm.registerListener(value, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
                        sensorEventListeners?.add(value)
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    accelerometer?.also { accelerometer ->
                        val value = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                //调用Js代码，把参数传过去
                                discover_web_view.evaluateJavascript("window.accelerometer('${event.values[0]}','${event.values[1]}','${event.values[2]}')") {
                                }
                            }

                            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
                        }
                        sm.registerListener(value, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                        sensorEventListeners?.add(value)
                    }
                }
            }
        }
    }

    private fun savePic(url: String) {
        doPermissionAction(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            doAfterGranted {
                MaterialAlertDialogBuilder(this@RollerViewActivity)
                    .setTitle(getString(R.string.discover_pic_save_alert_dialog_title))
                    .setMessage(R.string.discover_pic_save_alert_dialog_message)
                    .setPositiveButton("确定") { dialog, _ ->
                        val name = System.currentTimeMillis()
                            .toString() + url.split('/').lastIndex.toString()
                        this@RollerViewActivity.loadBitmap(url) {
                            this@RollerViewActivity.saveImage(it, name)
                            MediaScannerConnection.scanFile(
                                this@RollerViewActivity,
                                arrayOf(
                                    Environment.getExternalStorageDirectory()
                                        .toString() + DIR_PHOTO
                                ),
                                arrayOf("image/jpeg"),
                                null
                            )
                            runOnUiThread {
                                toast("图片保存于${Environment.DIRECTORY_PICTURES}${DIR_PHOTO}文件夹下哦")
                                dialog.dismiss()
                            }
                        }
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    //处理返回键，如果是还有历史记录就直接在webView返回
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KEYCODE_BACK && discover_web_view.canGoBack()) {
            discover_web_view.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        discover_web_view.resumeTimers()
        callback?.webViewResume()
    }

    override fun onPause() {
        callback?.webViewPause()
        //为什么不用webView的onPause(),因为pauseTimers()停止更加强硬，避免出现无法预料的问题
        discover_web_view.pauseTimers()
        if (sensorEventListeners?.size != 0) {
            sensorEventListeners?.forEach {
                sm?.unregisterListener(it)
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        callback?.webViewDestroy()
        discover_web_view.destroy()
        super.onDestroy()
    }

    companion object {
        fun startRollerViewActivity(info: RollerViewInfo, context: Context) {
            context.startActivity(
                Intent(context,RollerViewActivity::class.java)
                    .putExtra("URL", info.picture_goto_url)
                    .putExtra("Key", info.keyword)
            )
        }
    }
}