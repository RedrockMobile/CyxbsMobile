package com.cyxbs.components.account.provider

import com.cyxbs.components.config.sp.defaultSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 游客状态提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object TouristProvider {
  private const val KEY = "is_tourist"

  val stateFlow: StateFlow<Boolean>
    field = MutableStateFlow(defaultSettings.getBoolean(KEY, false))

  fun set(isTourist: Boolean) {
    stateFlow.value = isTourist
    defaultSettings.putBoolean(KEY, isTourist)
  }
}