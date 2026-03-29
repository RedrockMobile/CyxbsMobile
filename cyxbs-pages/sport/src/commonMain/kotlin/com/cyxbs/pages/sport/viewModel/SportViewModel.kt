package com.cyxbs.pages.sport.viewModel

import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.sport.bean.SportDetailBean
import com.cyxbs.pages.sport.model.SportRepository

class SportViewModel : BaseViewModel() {

    val sportDetailState = mutableStateOf<SportDetailBean?>(null)

    fun getSportDetailData() {
        launchByViewModelScope {
            SportRepository.getSportDetailData().getOrElse { throwable ->
                toast("网络错误")
                null
            }?.let { data ->
                sportDetailState.value = data
            }
        }
    }

}