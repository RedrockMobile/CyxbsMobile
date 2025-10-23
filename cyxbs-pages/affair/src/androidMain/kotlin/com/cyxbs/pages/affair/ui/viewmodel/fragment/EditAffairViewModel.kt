package com.cyxbs.pages.affair.ui.viewmodel.fragment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.affair.model.AffairRepository
import com.cyxbs.pages.affair.room.AffairDataBase
import com.cyxbs.pages.affair.room.AffairEntity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/6/11 10:33
 */
class EditAffairViewModel : BaseViewModel() {

  private val _affairEntity = MutableLiveData<AffairEntity>()
  val affairEntity: LiveData<AffairEntity>
    get() = _affairEntity

  fun updateAffair(
    onlyId: Int,
    time: Int,
    title: String,
    content: String,
    atWhatTime: List<AffairEntity.AtWhatTime>,
  ) {
    if (atWhatTime.isNotEmpty()) {
      AffairRepository.updateAffair(onlyId, time, title, content, atWhatTime)
        .observeOn(AndroidSchedulers.mainThread())
        .safeSubscribeBy { "更新成功".toast() }
    } else {
      AffairRepository.deleteAffair(onlyId)
        .observeOn(AndroidSchedulers.mainThread())
        .safeSubscribeBy { "删除成功".toast() }
    }
  }

  fun findAffairEntity(onlyId: Int) {
    val stuNum = IAccountService::class.impl().stuNum.orEmpty()
    if (stuNum.isNotEmpty()) {
      AffairDataBase.getAffairDao()
        .findAffairByOnlyId(stuNum, onlyId)
        .subscribeOn(Schedulers.io())
        .safeSubscribeBy {
          _affairEntity.postValue(it)
        }
    }
  }
}