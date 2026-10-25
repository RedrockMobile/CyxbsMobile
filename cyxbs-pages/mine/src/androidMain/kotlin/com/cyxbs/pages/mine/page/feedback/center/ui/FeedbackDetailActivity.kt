package com.cyxbs.pages.mine.page.feedback.center.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.widget.TextView
import com.cyxbs.components.base.webview.LiteJsWebView
import com.cyxbs.pages.mine.R
import com.mredrock.cyxbs.common.ui.BaseActivity

class FeedbackDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_feedback_detail)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val btnBack = findViewById<View>(R.id.btn_back)
        val webDetailContent = findViewById<LiteJsWebView>(R.id.web_detail_content)
        tvTitle.text = intent.getStringExtra("title")
        webDetailContent.apply {
            val setting = settings
            setting.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
            }
            loadDataWithBaseURL(null, intent.getStringExtra("content").toString(), "text/html", "utf-8", null)
        }
        btnBack.setOnClickListener { finish() }
    }
}
