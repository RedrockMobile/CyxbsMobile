package com.cyxbs.pages.noclass.page.viewmodel.dialog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.noclass.bean.NoclassGroupId
import com.cyxbs.pages.noclass.page.repository.NoClassRepository

/**
 * 将创建分组这个ViewModel单独拿出来了,因为课表界面要用，固定分组fragment中也要用
 */
class CreateGroupViewModel : BaseViewModel(){

    //是否创建成功
    val isCreateSuccess : LiveData<NoclassGroupId> get() = _isCreateSuccess
    private val _isCreateSuccess = MutableLiveData<NoclassGroupId>()

    /**
     * 上传分组
     */
    fun postNoclassGroup(
        name : String,
        stuNums : String,
    ){
        NoClassRepository
            .postNoclassGroup(name,stuNums)
            .map {
                it.data
            }
            .doOnError {
                if (it.message?.contains("\"data\":\"exist\"") == true) {
                    // 如果重名，就返回-2
                    // 这里返回的结构不符合规范，暂时使用这种判断来解决
                    _isCreateSuccess.postValue(NoclassGroupId(-2))
                } else {
                    _isCreateSuccess.postValue(NoclassGroupId(-1))
                }
            }.safeSubscribeBy {
                _isCreateSuccess.postValue(it)
            }
    }

}