package com.mredrock.cyxbs.common.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.utils.BindView
import com.mredrock.cyxbs.common.R
import com.mredrock.cyxbs.common.utils.extensions.getDarkModeStatus


/**
 * Created By jay68 on 2018/8/9.
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * 这里可以开启生命周期的Log，你可以重写这个值并给值为true，
     * 也可以直接赋值为true（赋值的话请在init{}里面赋值或者在onCreate的super.onCreate(savedInstanceState)调用之前赋值）
     */
    protected open var isOpenLifeCycleLog = false

    // 只在这里做封装处理
    private var baseBundle: Bundle? = null


    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseBundle = savedInstanceState
        // 禁用横屏，现目前不需要横屏，防止发送一些错误，
        // 如果要适配横屏，掌邮会有很多不规范的地方，尤其是 Fragment 那块没办法，学长们遗留下来的代码太多了
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        initFlag()
    }

    private fun initFlag() {
        if (getDarkModeStatus()) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            window.decorView.systemUiVisibility =
                    //亮色模式状态栏
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            //设置decorView的布局设置为全屏
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            //维持布局稳定，不会因为statusBar和虚拟按键的消失而移动view位置
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    val common_toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    protected fun JToolbar.initWithSplitLine(title: String,
                                             withSplitLine: Boolean = true,
                                             @DrawableRes icon: Int = R.drawable.common_ic_back,
                                             listener: View.OnClickListener? = View.OnClickListener { finish() },
                                             titleOnLeft: Boolean = true) {
        init(this@BaseActivity, title, withSplitLine, icon, titleOnLeft, listener)
    }

    /**
     * 在简单界面，使用这种方式来得到 View，kae 插件 和 DataBinding 已不被允许使用
     * ```
     * 使用方法：
     *    val mTvNum: TextView by R.id.xxx.view()
     * or
     *    val mTvNum by R.id.xxx.view<TextView>()
     * ```
     */
    protected fun <T: View> Int.view() = BindView<T>(this, this@BaseActivity)
    
    
    
    
    
    
    
    
    
}