package com.cyxbs.pages.volunteer

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.iterator
import com.cyxbs.components.config.route.DISCOVER_VOLUNTEER
import com.cyxbs.pages.volunteer.event.VolunteerLoginEvent
import com.cyxbs.pages.volunteer.viewmodel.VolunteerLoginViewModel
import com.cyxbs.pages.volunteer.widget.EncryptPassword
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.textfield.TextInputEditText
import com.mredrock.cyxbs.common.component.CyxbsToast
import com.mredrock.cyxbs.common.ui.BaseViewModelActivity
import com.mredrock.cyxbs.common.utils.extensions.gone
import com.mredrock.cyxbs.common.utils.extensions.invisible
import com.mredrock.cyxbs.common.utils.extensions.setOnSingleClickListener
import com.mredrock.cyxbs.common.utils.extensions.visible
import org.greenrobot.eventbus.EventBus

@KClassProvider(clazz = Activity::class, name = DISCOVER_VOLUNTEER)
class VolunteerLoginActivity : BaseViewModelActivity<VolunteerLoginViewModel>() {

    private val btn_volunteer_login by R.id.btn_volunteer_login.view<Button>()
    private val iv_back by R.id.iv_back.view<AppCompatImageView>()
    private val et_volunteer_password by R.id.et_volunteer_password.view<TextInputEditText>()
    private val et_volunteer_account by R.id.et_volunteer_account.view<TextInputEditText>()
    private val fl_root by R.id.fl_root.view<FrameLayout>()

    companion object {
        const val BIND_SUCCESS: Int = 0
        const val FAILED: Int = -1
        const val INVALID_ACCOUNT: Int = -2
        const val WRONG_PASSWORD: Int = 3
    }

    //进入登录页面，说明发现页的vm中志愿数据为空，或者用户主动接触绑定
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.volunteer_activity_login)
        initObserve()
        btn_volunteer_login.setOnSingleClickListener {
            loginAction()
        }
        iv_back.setOnSingleClickListener { finish() }
        useSoftKeyboard()
    }


    private fun useSoftKeyboard() {
        et_volunteer_password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loginAction()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }


    private fun loginAction() {
        if (et_volunteer_account.text.toString().isEmpty() || et_volunteer_password.text.toString()
                .isEmpty()
        ) {
            CyxbsToast.makeText(this, "账号或密码不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            currentFocus?.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
        displayLottie()
        viewModel.login(
            et_volunteer_account.text.toString(),
            EncryptPassword.encrypt(et_volunteer_password.text.toString())
        ) {

            Toast.makeText(this, "服务暂时不可使用~", Toast.LENGTH_SHORT).show()
            stopLottie()
        }
    }


    private fun failedAction(text: String) {
        stopLottie()
        CyxbsToast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun initObserve() {
        viewModel.loginCode.observe {
            it ?: return@observe
            when (it) {
                BIND_SUCCESS -> {

                }

                INVALID_ACCOUNT, WRONG_PASSWORD -> failedAction("亲，输入的账号或密码有误哦")


                FAILED -> failedAction("亲，登录失败哦")

            }
        }
        viewModel.volunteerTime.observe {
            it ?: return@observe
            VolunteerRecordActivity.startActivity(this, it)
            EventBus.getDefault().postSticky(VolunteerLoginEvent(it))
            finish()
        }
    }

    private fun displayLottie() {
        for (i in fl_root) {
            if (i.id != R.id.lav_login) {
                i.invisible()
            } else {
                i.visible()
            }
        }
    }

    private fun stopLottie() {
        for (i in fl_root) {
            if (i.id != R.id.lav_login) {
                i.visible()
            } else {
                i.gone()
            }
        }
    }

}
