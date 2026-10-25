package com.cyxbs.components.utils.utils.get

import platform.Foundation.NSBundle

actual fun getAppVersionName(): String =
  (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String).orEmpty()
