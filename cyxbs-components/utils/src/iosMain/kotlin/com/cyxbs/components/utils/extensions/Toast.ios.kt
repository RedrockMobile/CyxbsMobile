package com.cyxbs.components.utils.extensions

import com.cyxbs.components.config.service.impl


interface IOSToast {
  fun toast(s: String, isLong: Boolean)
}

actual fun toast(s: CharSequence?) {
  s ?: return
  IOSToast::class.impl().toast(s.toString(), false)
}

actual fun toastLong(s: CharSequence?) {
  s ?: return
  IOSToast::class.impl().toast(s.toString(), true)
}