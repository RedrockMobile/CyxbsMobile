package com.cyxbs.pages.mine.page.security.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.login.api.ILoginService
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.security.util.IdsFindPasswordDialog
import com.cyxbs.pages.mine.page.security.viewmodel.FindPasswordByIdsViewModel

/**
 * @author : why
 * @time   : 2022/8/22 00:33
 * @bless  : God bless my code
 */
class ConfirmPasswordFragment : BaseFragment() {

    /**
     * activity的viewModel
     */
    private val mViewModel: FindPasswordByIdsViewModel by lazy {
        ViewModelProvider(requireActivity())[FindPasswordByIdsViewModel::class.java]
    }

    //设置按钮背景使用
    /**
     * 不可点击的背景
     */
    private var mUnClickable: Drawable? = null

    /**
     * 可点击的背景
     */
    private var mClickable: Drawable? = null
    
    /**
     * 跳转到登录界面重新登录的 Runnable
     *
     * 在修改密码成功后弹出 dialog 时会倒计时 2 秒允许这个 Runnable，
     * 但 dialog 有个确认按钮，点击就会立马跳转，所以需要防止重复调用
     */
    private val mReLoginRunnable = Runnable {
        ILoginService::class.impl().jumpToLoginPage()
        requireActivity().finish()
    }

    private val mineBtnFindPasswordIdsChange by R.id.mine_btn_find_password_ids_change.view<Button>()
    private val mineEtFindPasswordIdsInputPassword by R.id.mine_et_find_password_ids_input_password.view<EditText>()
    private val mineEtFindPasswordIdsConfirmPassword by R.id.mine_et_find_password_ids_confirm_password.view<EditText>()
    private val mineTvFindPasswordIdsDifferent by R.id.mine_tv_find_password_ids_different.view<Button>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mUnClickable = AppCompatResources.getDrawable(
            requireActivity(),
            R.drawable.mine_shape_bg_find_password_ids_button_invalid
        )
        mClickable = AppCompatResources.getDrawable(
            requireActivity(),
            R.drawable.mine_shape_bg_find_password_ids_button
        )
        val dialog =
            IdsFindPasswordDialog(requireActivity())
                .setTitle("成功")
                .setContent("新密码修改成功，请重新登录后再进行操作")
                .setConfirm {
                    //并设置点击跳转至登录界面
                    view.removeCallbacks(mReLoginRunnable) // 先手动取消之前设置的自动跳转
                    mReLoginRunnable.run()
                    dismiss()
                }

        //刚进入页面时按钮设置为灰色,且不可点击
        mineBtnFindPasswordIdsChange.background = mUnClickable
        mineBtnFindPasswordIdsChange.isEnabled = false
        mineEtFindPasswordIdsInputPassword.addTextChangedListener()
        mineEtFindPasswordIdsConfirmPassword.addTextChangedListener()
        mViewModel.isChangeSuccess.observe {
            if (!it) {
                mineBtnFindPasswordIdsChange.text = "修改"
                mineBtnFindPasswordIdsChange.isEnabled = true
                "修改失败！".toast()
            } else {
                //修改成功后弹出dialog提示
                dialog.show()
                // 该 dialog 在按返回键和按其他区域时无法取消，只能通过确认按钮才能关闭，
                // 但点击确认按钮会取消该 Runnable，所以不会触发内存泄漏
                view.postDelayed(mReLoginRunnable, 2000)
            }
        }
    }

    /**
     * 设置按钮的颜色、是否可点击以及具体的点击事件
     */
    @SuppressLint("SetTextI18n")
    private fun EditText.addTextChangedListener() {
        doOnTextChanged { _, _, _, _ ->
            //若三个输入栏中的内容均不为空，则背景设置为正常颜色，且可以点击
            if (
                mineEtFindPasswordIdsInputPassword.text.toString() != "" &&
                mineEtFindPasswordIdsConfirmPassword.text.toString() != ""
            ) {
                //设置按钮背景颜色为正常的渐变蓝
                mineBtnFindPasswordIdsChange.background = mClickable
                //设置按钮可点击
                mineBtnFindPasswordIdsChange.isEnabled = true
                mineBtnFindPasswordIdsChange.setOnSingleClickListener {
                    if (mineEtFindPasswordIdsInputPassword.text.toString() ==
                        mineEtFindPasswordIdsConfirmPassword.text.toString()
                    ) {
                        //若长度不够则弹出提示
                        if (mineEtFindPasswordIdsInputPassword.text.toString().length < 6) {
                            "密码长度应不小于6位哦~".toast()
                        } else {
                            //若两个输入栏里面的内容相同且长度足够，则正常设置点击事件
                            //设置提示内容为不可见
                            mineTvFindPasswordIdsDifferent.gone()
                            mineBtnFindPasswordIdsChange.text = "Loading..."
                            mineBtnFindPasswordIdsChange.isEnabled = false
                            mViewModel.changePassword(
                                mViewModel.stuNum,
                                mineEtFindPasswordIdsInputPassword.text.toString()
                            )
                        }
                    } else {
                        //若两个输入栏里面的内容不相同，则弹出提示
                        mineTvFindPasswordIdsDifferent.visible()
                    }
                }
            } else {
                //设置按钮背景颜色为灰色
                mineBtnFindPasswordIdsChange.background = mUnClickable
                //设置按钮不可点击
                mineBtnFindPasswordIdsChange.isEnabled = false
            }
        }
    }
}