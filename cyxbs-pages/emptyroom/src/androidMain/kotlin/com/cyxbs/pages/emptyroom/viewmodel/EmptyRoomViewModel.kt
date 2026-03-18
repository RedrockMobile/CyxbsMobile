package com.cyxbs.pages.emptyroom.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.pages.emptyroom.bean.EmptyRoom
import com.cyxbs.pages.emptyroom.network.ApiService
import com.cyxbs.pages.emptyroom.utils.EmptyConverter
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.utils.LogUtils
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit


/**
 * Created by Cynthia on 2018/9/21
 */

class EmptyRoomViewModel : BaseViewModel() {
    var rooms: MutableLiveData<List<EmptyRoom>> = MutableLiveData()
    var status: MutableLiveData<Int> = MutableLiveData()

    private var d: Disposable? = null

    private val apiService = ApiGenerator.getApiService(ApiService::class.java)

    companion object {
        const val DEFAULT = 1
        const val LOADING = 2
        const val FINISH = 3
        const val ERROR = 4
    }

    init {
        status.value = DEFAULT
    }

    fun getData(week: Int, weekday: Int, building: Int, section: List<Int>) {
        val tag = section.map { it - 1 }//新接口从0开始
            .joinToString(",", " ", " ")
            .replace(" ", "")
        if (section.isEmpty()) {
            status.value = DEFAULT
            return
        }
        status.value = LOADING
        d?.dispose()
        d = apiService.getEmpyRooms(weekday.toString(), tag, building.toString(), week.toString())
            .delay(300, TimeUnit.MILLISECONDS)
            .setSchedulers()
            .safeSubscribeBy(
                onNext = {
                    status.value = FINISH
                    val converter = EmptyConverter()
                    converter.setEmptyData(it.data)
                    LogUtils.d("EmptyRoomData",it.data.toString())
                    rooms.value = converter.convert()
                },
                onError = {
                    status.value = ERROR
                })
    }
}