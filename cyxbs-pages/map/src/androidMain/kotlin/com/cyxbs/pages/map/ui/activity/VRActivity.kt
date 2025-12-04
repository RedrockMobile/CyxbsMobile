package com.cyxbs.pages.map.ui.activity

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.webView.LiteJsWebView

class VRActivity : BaseActivity() {

    private lateinit var webView: WebView


    companion object {
        const val VR_URL = "vr_url"
        const val VR_URL_SP = "vr_url_sp"
        const val DEFAULT_VR_URL = "http://720yun.com/t/0e929mp6utn?pano_id=473004"
        fun startVRActivity(activity: Context, vrUrl: String) {
            activity.startActivity(Intent(activity, VRActivity::class.java).putExtra(VR_URL, vrUrl))
        }
    }

    private var url = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(VR_URL).isNullOrEmpty()) {
            url = defaultSp.getString(VR_URL_SP, DEFAULT_VR_URL) ?: DEFAULT_VR_URL
        } else {
            url = intent.getStringExtra(VR_URL).toString()
            defaultSp.edit {
                putString(VR_URL_SP, url)
            }
        }
        webView = LiteJsWebView(this)
        setContentView(FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            fitsSystemWindows = true // 偏移状态栏
            addView(webView)
        })

        val dialog = ProgressDialog(this)
        dialog.setMessage("加载中...")
        dialog.show()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.settings.mediaPlaybackRequiresUserGesture = false
        }
        webView.loadUrl(url)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    dialog.dismiss()
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return super.shouldOverrideUrlLoading(view, url)
            }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                webView.onPause()
            }

            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                webView.onResume()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                webView.destroy()
            }
        })
    }
}
