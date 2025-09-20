package com.cyxbs.components.config.service

import android.app.Activity
import android.content.Intent
import com.cyxbs.components.config.route.ActivityInterceptor
import com.cyxbs.components.init.appTopActivity
import kotlin.reflect.KClass

/**
 * ```
 * // 注意：clazz 参数必须写 Activity::class
 * @KClassProvider(clazz = Activity::class, name = XXX_ENTRY)
 * class XXXActivity : BaseActivity
 * ```
 * @param implName @KClassProvider 注解的 name 参数
 */
fun startActivity(implName: String, action: (Intent.() -> Unit)? = null) {
  val activityClass = ActivityInterceptor.get(implName) ?: Activity::class.implClass(implName)
  startActivity(activityClass, action)
}


/**
 * 启动 Activity
 */
fun startActivity(clazz: KClass<out Activity>, action: (Intent.() -> Unit)? = null) {
  val context = appTopActivity.get() ?: return
  context.startActivity(
    Intent(context, clazz.java).apply {
      action?.invoke(this)
    }
  )
}
