package com.cyxbs.pages.mine.sign.util

import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.store.api.IStoreService

actual fun postDailySignTask() {
  IStoreService::class.impl()
    .postTask(IStoreService.Task.DAILY_SIGN, "")
}