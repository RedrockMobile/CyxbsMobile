package com.cyxbs.pages.electricity.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.pages.electricity.bean.ElecInf
import com.cyxbs.pages.electricity.network.ApiService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.throwApiExceptionIfFail
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Author: Hosigus
 * Date: 2018/9/28 19:30
 * Description: com.cyxbs.pages.electricity.viewmodel
 */
class ChargeViewModel : BaseViewModel() {
    val chargeInfo: LiveData<ElecInf> = MutableLiveData()
    val loadFailed = MutableSharedFlow<Boolean>()

    private val service: ApiService by lazy {
        ApiGenerator.getApiService(ApiService::class.java)
    }

    fun getCharge(building: String, room: String) {
        service.getElectricityInfo(building, room)
            .throwApiExceptionIfFail()
            .map {
                it.elecInf
            }
            .setSchedulers()
            .safeSubscribeBy {
                (chargeInfo as MutableLiveData).value = it
            }
    }

    fun preGetCharge() {
        service.getElectricityInfo()
            .throwApiExceptionIfFail()
            .map {
                it.elecInf
            }
            .setSchedulers()
            .doOnError {
                launchByViewModelScope {
                    loadFailed.emit(true)
                }
            }
            .safeSubscribeBy {
                if (it == null) {
                    launchByViewModelScope {
                        loadFailed.emit(true)
                    }
                }
                (chargeInfo as MutableLiveData).value = it
            }
    }

}