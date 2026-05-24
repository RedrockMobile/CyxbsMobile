package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.pages.schoolcar.bean.CarStation

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
  actual override fun getClosedSite(): CarStation? {
    return null
  }

  actual override val isSupportLocation: Boolean
    get() = false

  actual override val shouldShowUserPositionMarker: State<Boolean> = mutableStateOf(false)
}
