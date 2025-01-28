package com.cyxbs.pages.mine.page.security.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.viewmodel.ChangePasswordViewModel
import com.cyxbs.pages.mine.util.ui.ChooseFindTypeDialog

/**
 * Author: SpreadWater
 * Time: 2020-10-29 15:06
 */
class ChangePasswordActivity : BaseActivity() {

    private val viewModel by viewModels<ChangePasswordViewModel>()

    private val mEtSecuritySecondInputPassword by R.id.mine_security_secondinput_password.view<AppCompatEditText>()
    private val mIvSecurityChangePasswordLine1Eye by R.id.mine_iv_security_change_paswword_line1_eye.view<ImageView>()
    private val mIvSecurityFrontIcLine2 by R.id.mine_iv_security_front_ic_line2.view<ImageView>()
    private val mTvSecurityTipLine1 by R.id.mine_tv_security_tip_line1.view<TextView>()
    private val mTvSecurityTipLine2 by R.id.mine_tv_security_tip_line2.view<TextView>()
    private val mViewDivider2 by R.id.mine_divider2.view<View>()
    private val mIvSecurityChangePasswordLine2Eye by R.id.mine_iv_security_change_paswword_line2_eye.view<ImageView>()
    private val mEtSecurityFirstInputPassword by R.id.mine_security_firstinput_password.view<AppCompatEditText>()
    private val mTvForgetPassword by R.id.mine_security_tv_forget_password.view<TextView>()
    private val mBtnSecurityChangePasswordConfirm by R.id.mine_bt_security_change_password_confirm.view<Button>()
    private val mPbSecurityChangePassword by R.id.mine_pb_security_change_password.view<ProgressBar>()
    private val toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    companion object {
        //下面的常量是转化页面形态的标识量
        const val TYPE_OLD_PASSWORDS = 0 //仅一行的输入旧密码模式
        const val TYPE_NEW_PASSWORD = 1 //两行的输入新密码模式
        const val TYPE_COLOR_LIGHT_BUTTON = 2//没输入满时按钮颜色
        const val TYPE_COLOR_NIGHT_BUTTON = 3//深色按钮
        const val TYPE_START_FROM_MINE = 4//从个人页面跳转到修改密码页面
        const val TYPE_START_FROM_OTHERS = 5//从密保和邮箱界面跳转
        const val TYPE_FORM_LOGIN_ACTIVITY_WITH_STU_NUMBER = 6//从登陆界面跳转过来，并且传递了学号进来
        const val INPUT_NEW_PASSWORD_FORMAT_IS_CORRECT = 10002
        fun actionStart(context: Context, startType: Int) {
            val intent = Intent(context, ChangePasswordActivity::class.java)
            intent.putExtra("startType", startType)
            context.startActivity(intent)
        }

        fun startFormLogin(context: Context, stuNumber: String, code: Int) {
            val intent = Intent(context, ChangePasswordActivity::class.java)
            intent.putExtra("startType", TYPE_FORM_LOGIN_ACTIVITY_WITH_STU_NUMBER)
            intent.putExtra("stuNumber", stuNumber)
            intent.putExtra("code", code)
            context.startActivity(intent)
        }
    }

    private var isLine2ShowPassword = false//是否显示密码

    private var isLine1ShowPassword = false//是否显示密码

    var isClick = false//按钮是否能点击

    var isOriginView = true//是否是在第一个界面

    private var originPassword = ""//用来保存旧密码

    private var stuNum = IAccountService::class.impl()
        .stuNum.orEmpty() //获取当前用户的学号

    private var isFromLogin = false//是否来自登陆界面

    private var code = -1//验证用的随机数

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getIntExtra("startType", 4)
        initView(type)
    }

    private fun initView(type: Int) {
        when (type) {
            TYPE_START_FROM_MINE -> {
                setContentView(R.layout.mine_activity_change_password)
                setToolBar(TYPE_OLD_PASSWORDS)
                changePageType(TYPE_OLD_PASSWORDS)
                changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                initEvent()
            }
            TYPE_START_FROM_OTHERS -> {
                isOriginView = false
                setContentView(R.layout.mine_activity_change_password)
                changePageType(TYPE_NEW_PASSWORD)
                changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                setToolBar(TYPE_NEW_PASSWORD)
                initEvent()
            }
            //表示自登陆界面跳转到这里，由于没有登陆，所以需要自intent中获取
            TYPE_FORM_LOGIN_ACTIVITY_WITH_STU_NUMBER -> {
                isOriginView = false
                setContentView(R.layout.mine_activity_change_password)
                changePageType(TYPE_NEW_PASSWORD)
                changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                setToolBar(TYPE_NEW_PASSWORD)
                //将此处的学号更新为自外界传递进来的学号
                stuNum = intent.getStringExtra("stuNumber")!!
                code = intent.getIntExtra("code", -1)
                isFromLogin = true
                initEvent()
            }
        }
    }

    private fun changePageType(type: Int) {
        when (type) {
            TYPE_OLD_PASSWORDS -> {
                mEtSecuritySecondInputPassword.visibility = View.GONE
                mIvSecurityChangePasswordLine1Eye.visibility = View.GONE
                mIvSecurityFrontIcLine2.visibility = View.GONE
                mTvSecurityTipLine2.visibility = View.GONE
                mTvSecurityTipLine1.visibility = View.GONE
                mViewDivider2.visibility = View.GONE
                mIvSecurityChangePasswordLine2Eye.visibility = View.GONE
                mEtSecurityFirstInputPassword.hint =
                    getString(R.string.mine_security_type_in_old_password)
            }
            TYPE_NEW_PASSWORD -> {
                mEtSecuritySecondInputPassword.visibility = View.VISIBLE
                mIvSecurityChangePasswordLine1Eye.visibility = View.GONE
                mIvSecurityFrontIcLine2.visibility = View.VISIBLE
                mViewDivider2.visibility = View.VISIBLE
                mIvSecurityChangePasswordLine2Eye.visibility = View.GONE
                mTvSecurityTipLine1.visibility = View.GONE
                mTvSecurityTipLine2.visibility = View.GONE
                mEtSecurityFirstInputPassword.hint =
                    getString(R.string.mine_security_please_type_new_words)
                mTvForgetPassword.visibility = View.GONE
            }
        }
    }

    fun changeButtonColorType(type: Int) {
        when (type) {
            TYPE_COLOR_LIGHT_BUTTON -> {
                mBtnSecurityChangePasswordConfirm.background =
                    ContextCompat.getDrawable(this, R.drawable.mine_shape_security_next_btn)
            }
            TYPE_COLOR_NIGHT_BUTTON -> {
                mBtnSecurityChangePasswordConfirm.background =
                    ContextCompat.getDrawable(this, R.drawable.mine_shape_round_corner_purple_blue)
            }
        }
    }

    private fun setToolBar(type: Int) {
        when (type) {
            TYPE_OLD_PASSWORDS -> {
                toolbar.apply {
                    init(
                        activity = this@ChangePasswordActivity,
                        title = "修改密码",
                        withSplitLine = false,
                        titleOnLeft = true,
                    )
                }
            }
            TYPE_NEW_PASSWORD -> {
                toolbar.apply {
                    init(
                        activity = this@ChangePasswordActivity,
                        title = "重设密码",
                        withSplitLine = false,
                        titleOnLeft = true,
                    )
                }
            }
        }
    }

    private fun initEvent() {
        mEtSecuritySecondInputPassword.transformationMethod =
            PasswordTransformationMethod.getInstance()//不显示密码
        mEtSecurityFirstInputPassword.transformationMethod =
            PasswordTransformationMethod.getInstance()//不显示密码
        mIvSecurityChangePasswordLine2Eye.setOnSingleClickListener {
            if (isLine2ShowPassword) {
                mIvSecurityChangePasswordLine2Eye.setImageResource(R.drawable.mine_ic_close_eye)
                mEtSecurityFirstInputPassword.transformationMethod =
                    PasswordTransformationMethod.getInstance()//不显示密码
                mEtSecurityFirstInputPassword.text?.let {
                    mEtSecurityFirstInputPassword.setSelection(it.length)
                }
                isLine2ShowPassword = false
            } else {
                mIvSecurityChangePasswordLine2Eye.setImageResource(R.drawable.mine_ic_open_eye)
                mEtSecurityFirstInputPassword.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()//显示密码
                mEtSecurityFirstInputPassword.text?.let {
                    mEtSecurityFirstInputPassword.setSelection(it.length)
                }
                isLine2ShowPassword = true
            }
        }
        mIvSecurityChangePasswordLine1Eye.setOnSingleClickListener {
            if (isLine1ShowPassword) {
                mIvSecurityChangePasswordLine1Eye.setImageResource(R.drawable.mine_ic_close_eye)
                mEtSecuritySecondInputPassword.transformationMethod =
                    PasswordTransformationMethod.getInstance()//不显示密码
                mEtSecuritySecondInputPassword.text?.let {
                    mEtSecuritySecondInputPassword.setSelection(it.length)
                }
                isLine1ShowPassword = false
            } else {
                mIvSecurityChangePasswordLine1Eye.setImageResource(R.drawable.mine_ic_open_eye)
                mEtSecuritySecondInputPassword.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()//显示密码
                mEtSecuritySecondInputPassword.text?.let {
                    mEtSecuritySecondInputPassword.setSelection(it.length)
                }
                isLine1ShowPassword = true
            }
        }
        mEtSecurityFirstInputPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                mTvSecurityTipLine1.visibility = View.GONE
                p0?.length?.let {
                    if (isOriginView) {
                        if (p0.isNotEmpty()) {
                            changeButtonColorType(TYPE_COLOR_NIGHT_BUTTON)
                            isClick = true
                            mIvSecurityChangePasswordLine2Eye.visibility = View.VISIBLE
                        } else {
                            mIvSecurityChangePasswordLine2Eye.visibility = View.GONE
                            changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                            isClick = false
                        }
                    } else {
                        //第二个界面的逻辑
                        if (p0.isNotEmpty()) {
                            if (p0.length in 6..16) {
                                mTvSecurityTipLine1.visibility = View.GONE
                            }
                            mEtSecuritySecondInputPassword.text?.isNotEmpty()?.let {
                                if (it) {
                                    //第二个不为空才去检查
                                    if (mEtSecuritySecondInputPassword.text.toString() != mEtSecurityFirstInputPassword.text.toString()) {
                                        //用于防止第二个输入框输入了，用于再次修改第一个输入框
                                        mTvSecurityTipLine2.visibility = View.VISIBLE
                                        changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                                        isClick = false
                                    } else {
                                        mTvSecurityTipLine2.visibility = View.GONE
                                        changeButtonColorType(TYPE_COLOR_NIGHT_BUTTON)
                                        isClick = true
                                    }
                                }
                            }
                            mIvSecurityChangePasswordLine2Eye.visibility = View.VISIBLE
                        } else {
                            mIvSecurityChangePasswordLine2Eye.visibility = View.GONE
                            changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                            isClick = false
                        }
                    }
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })
        mEtSecuritySecondInputPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                mTvSecurityTipLine1.visibility = View.GONE
                //判断两次输入的字符串是否相同
                p0?.length?.let {
                    if (!isOriginView) {
                        if (p0.length > 0) {
                            if (mEtSecuritySecondInputPassword.text.toString() != mEtSecurityFirstInputPassword.text.toString()) {
                                mTvSecurityTipLine2.visibility = View.VISIBLE
                                changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                                isClick = false
                            } else {
                                mTvSecurityTipLine2.visibility = View.GONE
                                changeButtonColorType(TYPE_COLOR_NIGHT_BUTTON)
                                isClick = true
                            }
                            mIvSecurityChangePasswordLine1Eye.visibility = View.VISIBLE
                        } else {
                            mTvSecurityTipLine2.visibility = View.GONE
                            mIvSecurityChangePasswordLine1Eye.visibility = View.GONE
                            changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                            isClick = false
                        }
                    }
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }
        })
        //检查新密码是否上传成功！
        viewModel.inputNewPasswordCorrect.observe(this, Observer {
            if (it) {
                toast("重置密码成功！由于账号互通，重邮帮小程序的密码也会一起更改哦~")
                finish()
            } else {
                if (viewModel.inputNewPasswordFormat == INPUT_NEW_PASSWORD_FORMAT_IS_CORRECT) {
                    mTvSecurityTipLine1.visibility = View.VISIBLE
                }
            }
        })
        //观察旧密码是否正确， 若正确就切换到下一个界面
        viewModel.originPassWordIsCorrect.observe(this, Observer {
            if (it) {
                //如果旧密码正确
                originPassword = mEtSecurityFirstInputPassword.text.toString()
                changePageType(TYPE_NEW_PASSWORD)
                changeButtonColorType(TYPE_COLOR_LIGHT_BUTTON)
                setToolBar(TYPE_NEW_PASSWORD)
                mEtSecurityFirstInputPassword.setText("")
                isOriginView = false
            } else {
                toast("旧密码错误!")
            }
        })
        mBtnSecurityChangePasswordConfirm.setOnSingleClickListener {
            if (isClick) {
                if (mEtSecurityFirstInputPassword.text == null) {
                    return@setOnSingleClickListener
                }
                if (isOriginView) {
                    //旧密码检测的网络请求
                    viewModel.originPassWordCheck(mEtSecurityFirstInputPassword.text.toString())
                } else {
                    //填写新密码的界面，跳转到上传新密码
                    mEtSecurityFirstInputPassword.text?.let {
                        if (isFromLogin) {
                            if (code != -1) {
                                if (it.length in 6..16) {
                                    //字数格式要求满足的情况下进行网络请求
                                    viewModel.resetPasswordFromLogin(
                                        stuNum,
                                        mEtSecurityFirstInputPassword.text.toString(),
                                        code
                                    )
                                    mTvSecurityTipLine1.visibility = View.GONE
                                } else {
                                    mTvSecurityTipLine1.visibility = View.VISIBLE
                                }
                            } else {
                                toast("后端返回的认证码存在问题，修改失败")
                            }
                        } else {
                            if (it.length in 6..16) {
                                //字数格式要求满足的情况下进行网络请求
                                viewModel.newPassWordInput(
                                    originPassword,
                                    mEtSecurityFirstInputPassword.text.toString()
                                )
                                mTvSecurityTipLine1.visibility = View.GONE
                            } else {
                                mTvSecurityTipLine1.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
        mTvForgetPassword.setOnSingleClickListener {
            mPbSecurityChangePassword.visibility = View.VISIBLE
            viewModel.checkDefaultPassword(stuNum) {
                mPbSecurityChangePassword.visibility = View.GONE
                if (viewModel.isDefaultPassword) {
                    //如果是默认密码
                    toast(getString(R.string.mine_security_default_password_hint))
                } else {
                    viewModel.checkBinding(stuNum) {
                        //此处的dialog需要传递来源，是来自登陆界面还是来自个人界面
                        ChooseFindTypeDialog.showDialog(
                            this,
                            viewModel.bindingEmail,
                            viewModel.bindingPasswordProtect,
                            this,
                            isFromLogin,
                            stuNum
                        )
                    }
                }
            }
        }
    }
}