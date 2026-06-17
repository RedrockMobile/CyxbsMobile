package com.cyxbs.pages.ufield

import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.components.config.route.UFIELD_MAIN_ENTRY
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.ufield.fairground.FairgroundNavPlatform
import com.g985892345.provider.api.annotation.ImplProvider

@ImplProvider
object FairgroundNavAndroidPlatformImpl : FairgroundNavPlatform {
  override fun jumpQaEntry() {
    startActivity(QA_ENTRY)
  }

  override fun jumpUfieldMainEntry() {
    startActivity(UFIELD_MAIN_ENTRY)
  }
}