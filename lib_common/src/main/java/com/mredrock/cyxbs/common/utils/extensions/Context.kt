package com.mredrock.cyxbs.common.utils.extensions

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.toastLong
import com.mredrock.cyxbs.common.utils.Internals

/**
 * Created by anriku on 2018/8/14.
 */

@Deprecated("使用 utils 中 screenWidth 替换", replaceWith = ReplaceWith(""))
fun Context.getScreenWidth(): Int {
    return resources.displayMetrics.widthPixels
}

@Deprecated("目前 Base 包中的 BaseActivity 已默认设置全屏，请查看父类中的 isCancelStatusBar 属性")
fun Activity.setFullScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val lp = window.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp
    }

    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
}

@Deprecated("使用 utils 中 toast() 替换", replaceWith = ReplaceWith("com.cyxbs.components.utils.extensions.toast(message)"))
fun toast(message: CharSequence) = com.cyxbs.components.utils.extensions.toast(message)

@Deprecated("使用 utils 中 toast() 替换", replaceWith = ReplaceWith("com.cyxbs.components.utils.extensions.toast(res)"))
fun toast(res: Int) = com.cyxbs.components.utils.extensions.toast(appContext.resources.getText(res))

@Deprecated("使用 utils 中 isDarkMode() 替换")
fun Context.getDarkModeStatus(): Boolean {
    val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}

@Deprecated("使用 utils 中 toastLong() 替换", replaceWith = ReplaceWith(""))
fun longToast(message: CharSequence) = toastLong(message)

@Deprecated("使用 utils 中 toastLong() 替换", replaceWith = ReplaceWith(""))
fun longToast(res: Int) = toastLong(appContext.resources.getText(res))


//anko-bridge
//anko不再维护，删除anko，一些从anko拿过来的扩展方法
@Deprecated("使用 utils 中 dp2px 替换", replaceWith = ReplaceWith(""))
fun Context.dp2px(dpValue: Float) = (dpValue * resources.displayMetrics.density + 0.5f).toInt()

//returns dip(dp) dimension value in pixels
@Deprecated("使用 utils 中 dp2px 替换", replaceWith = ReplaceWith(""))
fun Context.dip(value: Int): Int = (value * resources.displayMetrics.density).toInt()
@Deprecated("使用 utils 中 dp2px 替换", replaceWith = ReplaceWith(""))
fun Context.dip(value: Float): Int = (value * resources.displayMetrics.density).toInt()

//return sp dimension value in pixels
@Deprecated("使用 utils 中 dp2sp 替换", replaceWith = ReplaceWith(""))
fun Context.sp(value: Int): Int = (value * resources.displayMetrics.scaledDensity).toInt()
@Deprecated("使用 utils 中 dp2sp 替换", replaceWith = ReplaceWith(""))
fun Context.sp(value: Float): Int = (value * resources.displayMetrics.scaledDensity).toInt()

//converts px value into dip or sp
@Deprecated("使用 utils 中 px2dp 替换", replaceWith = ReplaceWith(""))
fun Context.px2dip(px: Int): Float = px.toFloat() / resources.displayMetrics.density

@Deprecated("不建议使用，请被启动的 Activity 单独提供静态方法，而不是这样随意传参！！！", replaceWith = ReplaceWith(""))
inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any?>) =
        Internals.internalStartActivity(this, T::class.java, params)

@Deprecated("请使用新的 Result API 代替：https://juejin.cn/post/6922866182190022663", replaceWith = ReplaceWith(""))
inline fun <reified T : Activity> Activity.startActivityForResult(requestCode: Int, vararg params: Pair<String, Any?>) =
        Internals.internalStartActivityForResult(this, T::class.java, requestCode, params)

@Deprecated("请使用新的 Result API 代替：https://juejin.cn/post/6922866182190022663", replaceWith = ReplaceWith(""))
inline fun <reified T : Activity> Fragment.startActivityForResult(requestCode: Int, vararg params: Pair<String, Any?>) {
    startActivityForResult(Internals.createIntent(requireActivity(), T::class.java, params), requestCode)
}