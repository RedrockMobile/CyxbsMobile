package com.cyxbs.pages.ufield.ui.fragment

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.route.FAIRGROUND_ENTRY
import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.components.config.route.UFIELD_MAIN_ENTRY
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.setAvatarImageFromUrl
import com.cyxbs.pages.food.api.FoodNavArgument
import com.cyxbs.pages.ufield.R
import com.cyxbs.pages.ufield.viewmodel.FairgroundViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 *
 * author : 苟云东
 * email : 2191288460@qq.com
 * date : 2023/8/26 14:59
 */
@ImplProvider(clazz = Fragment::class, name = FAIRGROUND_ENTRY)
class FairgroundPageFragment : BaseFragment(R.layout.ufield_fragment_fairground) {


    private val tvDays by R.id.main_tv_days.view<TextView>()
    private val tvNickname by R.id.tv_nickname.view<TextView>()
    private val ivHead by R.id.main_iv_head.view<ImageView>()
    private val startActivity by R.id.main_fairground_activity.view<FrameLayout>()
    private val startFood by R.id.main_fairground_food.view<FrameLayout>()
    private val startSquare by R.id.main_fairground_square.view<FrameLayout>()

    private val viewModel by viewModels<FairgroundViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startActivity.setOnClickListener {
            doIfLogin {
                startActivity(UFIELD_MAIN_ENTRY)
            }
        }
        startFood.setOnClickListener {
            doIfLogin {
                //startActivity(FOOD_ENTRY)
                //接入Compose
                MainNavController.navigate(FoodNavArgument)
            }
        }
        startSquare.setOnClickListener {
            doIfLogin {
                startActivity(QA_ENTRY)
            }
        }
        viewModel.days.observe(viewLifecycleOwner) {
            val text = "这是你来到邮乐园的第 $it 天"

            val spannableStringBuilder = SpannableStringBuilder(text)
            val startIndex = text.indexOf(it)
            val endIndex = startIndex + it.length

            // 设置加粗样式
            spannableStringBuilder.setSpan(
                StyleSpan(Typeface.BOLD),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 设置字体大小
            spannableStringBuilder.setSpan(
                AbsoluteSizeSpan(20, true),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 设置字体颜色
            spannableStringBuilder.setSpan(
                ForegroundColorSpan(Color.parseColor("#5D5DF7")),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 将 spannableStringBuilder 应用到文本视图
            tvDays.text = spannableStringBuilder
        }
        viewModel.message.observe(viewLifecycleOwner) {
            if (it != null) {
                tvNickname.text = "Hi, ${it.nickname}"
                ivHead.setAvatarImageFromUrl(it.photo_src)
            }
        }

    }
}