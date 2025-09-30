package com.cyxbs.pages.noclass.page.viewmodel.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.crash.CrashDialog
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.noclass.bean.NoClassGroup
import com.cyxbs.pages.noclass.page.repository.NoClassRepository


/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.page.ui.viewmodel
 * @ClassName:      NoClassViewModel
 * @Author:         Yan
 * @CreateDate:     2022年08月11日 16:11:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description:
 */


class NoClassViewModel : BaseViewModel() {

    /**
     * 获取分组数据。但是接收者却在固定分组,这是由于批量添加在activity中，却要传递给固定分组的fragment中
     */
    //获得所有分组
    val groupList: LiveData<List<NoClassGroup>> get() = _groupList
    private val _groupList = MutableLiveData<List<NoClassGroup>>()

    /**
     * 获得所有分组
     */
    fun getAllGroup() {
        NoClassRepository
            .getNoclassGroupDetail()
            .mapOrInterceptException {
                CrashDialog.Builder(it).show()
            }.safeSubscribeBy {
                _groupList.postValue(it)
            }
    }
}