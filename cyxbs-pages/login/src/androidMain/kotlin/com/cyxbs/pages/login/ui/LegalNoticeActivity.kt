package com.cyxbs.pages.login.ui

import android.os.Bundle
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.webView.LiteJsWebView
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.login.R

/**
 * ```
 * // 打开用户协议页面
 * ILegalNoticeService::class.impl().startUserAgreementActivity()
 *
 * // 打开隐私政策页面
 * ILegalNoticeService::class.impl().startPrivacyPolicyActivity()
 * ```
 *
 * @author: lytMoon
 * @description: 负责用户协议和隐私政策
 * @time: 2023/12/1
 * @version: 1.0
 */

class LegalNoticeActivity : BaseActivity() {

    companion object {
        fun start(url: String) {
            startActivity(LegalNoticeActivity::class) {
                putExtra("url", url)
            }
        }
    }

    private val mWebView by R.id.protocol_webView.view<LiteJsWebView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity_privacy)
        val url = intent.getStringExtra("url")
        if (url == null) {
            finish()
            return
        }
        mWebView.init()
        mWebView.webViewClient = WebViewClient()
        onBackPressedDispatcher.addCallback(object :OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if (mWebView.canGoBack()) mWebView.goBack()
                else finish()
            }
        })
        mWebView.loadUrl(url)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                mWebView.onPause()
            }

            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                mWebView.onResume()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                mWebView.destroy()
            }
        })
    }
}