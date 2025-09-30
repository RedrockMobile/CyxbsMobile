package com.cyxbs.pages.home.mobile.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/20
 */
class CourseBottomSheetViewModel : BaseViewModel() {

  /**
   * 三个状态
   * - true -> 展开
   * - false -> 折叠
   * - null -> 隐藏
   */
  val state = MutableStateFlow<Boolean?>(false)
}