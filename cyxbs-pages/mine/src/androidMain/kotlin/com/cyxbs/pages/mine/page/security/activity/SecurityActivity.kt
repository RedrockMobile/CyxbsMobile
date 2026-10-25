package com.cyxbs.pages.mine.page.security.activity

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.viewModels
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.activity.ChangePasswordActivity.Companion.TYPE_START_FROM_MINE
import com.cyxbs.pages.mine.page.security.viewmodel.SecurityActivityViewModel
import com.cyxbs.pages.mine.util.ui.DoubleChooseDialog

/**
 * Author: RayleighZ
 * Time: 2020-10-29 15:06
 */
class SecurityActivity : BaseActivity() {

    private val viewModel by viewModels<SecurityActivityViewModel>()

    private val mLlChangeBindingMail by R.id.mine_ll_change_binding_mail.view<LinearLayout>()
    private val mLlChangeSecretSecurity by R.id.mine_ll_change_secret_security.view<LinearLayout>()
    private val mLlChangePassword by R.id.mine_ll_change_password.view<LinearLayout>()
    private val mIvSecurityBack by R.id.mine_iv_security_back.view<ImageView>()

    //在网络请求返回绑定结果之前不允许进行点击

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_security)
        viewModel.checkBinding()
        mLlChangeBindingMail.setOnSingleClickListener {//绑定邮箱
            if (viewModel.canClick) {
                val intent = Intent(this, BindEmailActivity::class.java)
                startActivity(intent)
            } else {
                showRequestResult()
            }
        }

        mLlChangeSecretSecurity.setOnSingleClickListener {//密保
            if (viewModel.canClick) {
                SetPasswordProtectActivity.actionStart(this)
            } else {
                showRequestResult()
            }
        }

        mLlChangePassword.setOnSingleClickListener {//修改密码
            if (viewModel.canClick) {
                if (!viewModel.isBindingEmail && !viewModel.isSetProtect) {
                    DoubleChooseDialog.show(this)
                } else {
                    ChangePasswordActivity.actionStart(this, TYPE_START_FROM_MINE)
                }
            } else {
                showRequestResult()
            }
        }

        mIvSecurityBack.setOnSingleClickListener {//退回界面
            finish()
        }
    }

    private fun showRequestResult() {
        if (viewModel.netRequestSuccess)
            toast("正在请求是否绑定信息，请稍候")
        else
            toast("绑定信息请求失败，无法使用此功能")
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkBinding() // 刷新数据
    }
}
