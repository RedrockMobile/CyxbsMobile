package com.cyxbs.pages.mine.page.security.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.utils.Jump2QQHelper
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.viewmodel.BindEmailViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

class BindEmailActivity : BaseActivity() {

    private val viewModel by lazy { ViewModelProvider(this).get(BindEmailViewModel::class.java) }
    var email = ""

    private val btn_bind_email_next by R.id.btn_bind_email_next.view<Button>()
    private val tv_bind_email_contact_us by R.id.tv_bind_email_contact_us.view<TextView>()
    private val tv_bind_email_send_code by R.id.tv_bind_email_send_code.view<TextView>()
    private val et_bind_email by R.id.et_bind_email.view<EditText>()
    private val tv_bind_email_top_tips by R.id.tv_bind_email_top_tips.view<TextView>()
    private val tv_bind_email_tips by R.id.tv_bind_email_tips.view<TextView>()
    private val common_toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_bind_email)

        viewModel.mldConfirmIsSucceed.observe(this, Observer<Boolean> {
            if (it) {
                toast(getString(R.string.mine_security_bind_email_bind_succeed))
                finish()
            } else {
                toast(getString(R.string.mine_security_bind_email_bind_failed))
            }
        })

        viewModel.mldCode.observe(this, Observer<Int> {
            btn_bind_email_next.isEnabled = true
        })

        common_toolbar.init(activity = this@BindEmailActivity, title = "绑定邮箱")

        tv_bind_email_contact_us.setOnSingleClickListener {
            Jump2QQHelper.onFeedBackClick()
        }

        tv_bind_email_send_code.setOnSingleClickListener {
            sendCode()
        }

        btn_bind_email_next.setOnSingleClickListener {
            if (btn_bind_email_next.text == getString(R.string.mine_security_bind_email_next)) {
                sendCode()
            } else if (btn_bind_email_next.text == getString(R.string.mine_security_confirm)) {
                if (et_bind_email.text.toString() == "") {
                    toast(getString(R.string.mine_security_please_type_new_words))
                } else if (System.currentTimeMillis() / 1000 <= viewModel.expireTime) {
                    viewModel.confirmCode(email, et_bind_email.text.toString())
                } else {
                    toast(getString(R.string.mine_security_bind_email_code_expire))
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun sendCode() {
        val userEmail: String = et_bind_email.text.toString()
        var showUserEmail = userEmail
        if (isEmail(userEmail) || email != "") {
            if (email == "") email = userEmail
            else showUserEmail = email
            viewModel.getCode(email) {
                toast("已向你的邮箱发送了一条验证码")
                val atLocation = showUserEmail.indexOf("@")
                when {
                    atLocation in 2..4 -> {
                        showUserEmail = showUserEmail.substring(0, 1) + "*" + showUserEmail.substring(2, showUserEmail.length)
                    }
                    atLocation == 5 -> {
                        showUserEmail = showUserEmail.substring(0, 2) + "**" + showUserEmail.substring(4, showUserEmail.length)
                    }
                    atLocation > 5 -> {
                        var starString = ""
                        for (i in 0 until atLocation - 4) starString += "*"
                        showUserEmail = showUserEmail.substring(0, 2) + starString + showUserEmail.substring(atLocation - 2, showUserEmail.length)
                    }
                }
                tv_bind_email_top_tips.text = "掌邮向你的邮箱${showUserEmail}发送了验证码"
                btn_bind_email_next.text = "确定"
                et_bind_email.setText("")
                tv_bind_email_send_code.visible()
                tv_bind_email_tips.gone()
                tv_bind_email_send_code.isEnabled = false
                btn_bind_email_next.isEnabled = false
                Observable.intervalRange(0, 60, 0, 1, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnComplete {
                        tv_bind_email_send_code.isEnabled = true
                        tv_bind_email_send_code.text = getString(R.string.mine_security_resend)
                    }.safeSubscribeBy {
                        tv_bind_email_send_code.text = "正在发送(${60 - it})"
                    }
            }
        } else {
            tv_bind_email_tips.visible()
        }
    }

    private fun isEmail(strEmail: String): Boolean {
        val strPattern = "^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)\$"
        val p: Pattern = Pattern.compile(strPattern)
        val m: Matcher = p.matcher(strEmail)
        return m.matches()
    }
}