package com.cyxbs.components.utils.extensions

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.cyxbs.components.init.appContext
import com.cyxbs.components.init.appHandler
import com.cyxbs.components.utils.BuildConfig
import com.cyxbs.components.utils.R

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/3/7 17:58
 */

/**
 * TODO 可以在 debug 包下控制台筛选：toast，查看 toast 调用堆栈信息
 */

/**
 * 已自带处于其他线程时自动切换至主线程发送
 */
actual fun toast(s: CharSequence?) {
  CyxbsToast.show(appContext, s, Toast.LENGTH_SHORT)
}

actual fun toastLong(s: CharSequence?) {
  CyxbsToast.show(appContext, s, Toast.LENGTH_LONG)
}

fun toast(@StringRes id: Int) = toast(appContext.getString(id))

// 用于特殊场景使用，比如桌面小组件
fun toast(s: CharSequence?, context: Context) {
  CyxbsToast.show(context, s, Toast.LENGTH_SHORT)
}

private class CyxbsToast {
  companion object {

    /**
     * 已自带处于其他线程时自动切换至主线程发送
     */
    fun show(
      context: Context,
      text: CharSequence?,
      duration: Int,
    ) {
      if (text == null) return
      if (Thread.currentThread() !== Looper.getMainLooper().thread) {
        appHandler.post { newInstance(context, text, duration).show() }
      } else {
        newInstance(context, text, duration).show()
      }
    }
    
    private fun newInstance(
      context: Context,
      text: CharSequence,
      duration: Int,
    ): Toast {
      if (BuildConfig.DEBUG) {
        val throwable = Throwable() // 获取堆栈信息
        val path = throwable.stackTrace
          .toMutableList()
          .apply { removeAt(0) } // 从堆栈中去掉当前方法
          .filter {
            // 第一次先筛选出是自己代码中的方法栈
            !it.isNativeMethod
              && it.fileName != null
              && it.fileName.endsWith(".kt")
              && it.className.startsWith("com.")
          }.after {
            // 第二次筛选出不必要的方法栈
            !it.className.contains(".base.") // 筛掉 base 包
              && !it.fileName.startsWith("Base")
              && !it.fileName.matches(Regex("[tT]oast")) // 去掉一些工具类
          }.joinToString(separator = " <- ") {
            "(${it.fileName}:${it.lineNumber})"
          }
        Log.d("toast", "toast: text = $text   path: $path")
      }
      val result = Toast(context)
      val inflate =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
      val v: View = inflate.inflate(R.layout.utils_layout_toast, null)
      val tv = v.findViewById<View>(R.id.tv_toast) as TextView
      tv.text = text
      val height = context.resources.displayMetrics.heightPixels / 8
      result.view = v
      result.duration = duration
      result.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, height)
      return result
    }

    /**
     * 寻找第一个满足条件后的子数组
     */
    private fun <T> List<T>.after(first: (T) -> Boolean): List<T> {
      val list = ArrayList<T>()
      var isFound = false
      forEach {
        if (isFound) {
          list.add(it)
        } else {
          isFound = first.invoke(it)
        }
      }
      return list
    }
  }
}