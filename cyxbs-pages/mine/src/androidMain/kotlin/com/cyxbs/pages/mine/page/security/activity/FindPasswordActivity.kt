package com.cyxbs.pages.mine.page.security.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.utils.Jump2QQHelper
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.util.AnswerTextWatcher
import com.cyxbs.pages.mine.page.security.viewmodel.FindPasswordViewModel

/**
 * Author: RayleighZ
 * Time: 2020-10-29 15:06
 * describe: 找回密码的活动
 */
class FindPasswordActivity : BaseActivity() {

    private val viewModel by viewModels<FindPasswordViewModel>()

    //在此activity以及ViewModel中统一使用这个stuNumber来获取学号，以方便整体修改
    private var stuNumber =
        IAccountService::class.impl().stuNum.orEmpty()

    //是否来自登陆界面
    private var isFromLogin = false

    private val mTvSecurityFindContractUs by R.id.mine_tv_security_find_contract_us.view<TextView>()
    private val mClSecurityFindPasswordInputBox by R.id.mine_cl_securoty_find_password_input_box.view<ConstraintLayout>()
    private val mEtSecurityFind by R.id.mine_et_security_find.view<EditText>()
    private val mTvSecurityFindFirstTitle by R.id.mine_tv_security_find_first_title.view<TextView>()
    private val mTvSecuritySecondTitle by R.id.mine_tv_security_second_title.view<TextView>()
    private val mTvSecurityFindSendConfirmCode by R.id.mine_tv_security_find_send_confirm_code.view<TextView>()
    private val mBtnSecurityFindNext by R.id.mine_bt_security_find_next.view<Button>()
    private val mTvSecurityFindFirstContent by R.id.mine_tv_security_find_first_content.view<TextView>()
    private val mTvSecurityFindFirstTip by R.id.mine_tv_security_find_first_tip.view<TextView>()

    companion object {
        //自登陆界面而来
        fun actionStartFromLogin(context: Context, type: Int, stuNumber: String) {
            val intent = Intent(context, FindPasswordActivity::class.java)
            intent.putExtra("type", type)
            intent.putExtra("stu_number", stuNumber)
            //是否是从登陆界面过来的（是否已经登陆，将影响学号的获取）
            intent.putExtra("is_from_login", true)
            context.startActivity(intent)
        }

        //从个人界面而来（已经登陆的状态）
        fun actionStartFromMine(context: Context, type: Int) {
            val intent = Intent(context, FindPasswordActivity::class.java)
            intent.putExtra("type", type)
            //是否是从登陆界面过来的（是否已经登陆，将影响学号的获取）
            intent.putExtra("is_from_login", false)
            context.startActivity(intent)
        }

        const val FIND_PASSWORD_BY_EMAIL = 0//找回密码的方式为邮箱找回
        const val FIND_PASSWORD_BY_SECURITY_QUESTION = 1//找回密码的方式为密保问题找回
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_find_password)
        //设置toolBar
        findViewById<JToolbar>(com.cyxbs.components.view.R.id.toolbar).apply {
            this.init(this@FindPasswordActivity, context.getString(R.string.mine_security_find_password))
        }

        //首先判断是否是自登陆界面来到的这里，如果是，就刷新当前的stuNumber
        isFromLogin = intent.getBooleanExtra("is_from_login", false)
        if (isFromLogin) {
            stuNumber = intent.getStringExtra("stu_number")!!
        }
        val type =
            intent.getIntExtra("type", FIND_PASSWORD_BY_SECURITY_QUESTION)//如果出错，则默认展示为按照密保问题进行找回密码
        //配置viewModel内部的学号
        viewModel.stuNumber = stuNumber
        //更改页面样式
        turnPageType(type)
        //联系我们的点击事件
        mTvSecurityFindContractUs.setOnSingleClickListener {
            Jump2QQHelper.onFeedBackClick()
        }
        initObserve()
    }

    private fun initObserve() {
        viewModel.emailAddressOrQuestion.observe {
            mTvSecurityFindFirstContent.text = it
        }
        viewModel.timerText.observe {
            mTvSecurityFindSendConfirmCode.text = it
        }
        viewModel.firstTipText.observe {
            mTvSecurityFindFirstTip.text = it
        }
    }

    private fun turnPageType(type: Int) {
        when (type) {
            FIND_PASSWORD_BY_EMAIL -> {
                //首先请求以获取当前用户的邮箱，展示给用户
                viewModel.getBindingEmail()
                //将页面变更为为按照邮箱进行查找
                //首先设置inputBox(ll)的高度
                mClSecurityFindPasswordInputBox.apply {
                    this.layoutParams.height = 41f.dp2px
                }
                //更改title和hint的提示字符
                mEtSecurityFind.hint =
                    getString(R.string.mine_security_please_type_in_confirm_code)
                mTvSecurityFindFirstTitle.text =
                    getString(R.string.mine_security_click_to_get_confirm_code)
                mTvSecuritySecondTitle.visibility = View.GONE
                //设置点击获取验证码的text
                viewModel.timerText.postValue(getString(R.string.mine_security_get_confirm_code))
                //接下来配置页面的点击事件以及相关逻辑
                //点击获取验证码(内部含有倒计时)
                mTvSecurityFindSendConfirmCode.setOnSingleClickListener {
                    viewModel.sendConfirmCodeAndStartBackTimer()
                }

                mEtSecurityFind.addTextChangedListener(
                    object : AnswerTextWatcher(
                        viewModel.firstTipText,
                        mBtnSecurityFindNext,
                        this
                    ) {
                        override fun afterTextChanged(s: Editable?) {
                            if (s?.length !in 5..6) {
                                button.background = ContextCompat.getDrawable(
                                    context,
                                    R.drawable.mine_shape_round_corner_light_blue
                                )
                            } else {
                                button.background = ContextCompat.getDrawable(
                                    context,
                                    R.drawable.mine_shape_round_corner_purple_blue
                                )
                            }
                        }
                    }
                )

                //点击下一步以判断验证码是否正确
                mBtnSecurityFindNext.setOnSingleClickListener {
                    viewModel.confirmCode(
                        inputText = mEtSecurityFind.text.toString(),
                        onSuccess = {
                            ChangePasswordActivity.startFormLogin(this, stuNumber, it)
                            finish()
                        },
                        onField = {
                            viewModel.firstTipText.postValue("验证码有误或过期，请重新获取")
                        }
                    )
                }
            }
            FIND_PASSWORD_BY_SECURITY_QUESTION -> {
                //默认的界面
                //首先获取用户的密保问题
                viewModel.getUserQuestion()
                //设置点击事件，即认证密保问题
                mEtSecurityFind.addTextChangedListener(
                    AnswerTextWatcher(viewModel.firstTipText, mBtnSecurityFindNext, this)
                )
                mBtnSecurityFindNext.setOnSingleClickListener {
                    viewModel.confirmAnswer(
                        inputText = mEtSecurityFind.text.toString(),
                    ) {
                        ChangePasswordActivity.startFormLogin(this, stuNumber, it)
                        finish()
                    }
                }
                mTvSecuritySecondTitle.visibility = View.VISIBLE
            }
        }
    }
}