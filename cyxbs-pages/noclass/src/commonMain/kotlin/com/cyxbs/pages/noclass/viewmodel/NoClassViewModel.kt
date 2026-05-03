package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import com.cyxbs.components.base.ui.BaseViewModel



/**
 * description ： TODO:没课约页的ViewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/2 19:45
 */

//仅仅管理tab
class NoClassViewModel : BaseViewModel() {
    companion object {
        const val TAB_TEMPORARY = 0
        const val TAB_SOLID = 1
    }

    val currentTabIndex = mutableIntStateOf(TAB_TEMPORARY)

    fun selectTab(index: Int) { currentTabIndex.intValue = index }
}
