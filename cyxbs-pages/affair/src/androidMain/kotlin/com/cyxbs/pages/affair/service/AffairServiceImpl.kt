package com.cyxbs.pages.affair.service

import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.affair.api.NoClassBean
import com.cyxbs.pages.affair.model.AffairRepository
import com.cyxbs.pages.affair.room.AffairEntity
import com.cyxbs.pages.affair.ui.activity.AffairActivity
import com.cyxbs.pages.affair.ui.activity.NoClassAffairActivity
import com.g985892345.provider.api.annotation.ImplProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/14 17:13
 */
@ImplProvider
object AffairServiceImpl : IAffairService {
  
  override fun getAffair(): Single<List<IAffairService.Affair>> {
    return AffairRepository.getAffair()
      .map { it.toAffair() }
  }
  
  override fun refreshAffair(): Single<List<IAffairService.Affair>> {
    return AffairRepository.refreshAffair()
      .map { it.toAffair() }
  }
  
  override fun observeSelfAffair(needRefresh: Boolean): Observable<List<IAffairService.Affair>> {
    return AffairRepository.observeAffair(needRefresh)
      .map { it.toAffair() }
  }
  
  override fun deleteAffair(onlyId: Int): Completable {
    return AffairRepository.deleteAffair(onlyId)
  }
  
  override fun updateAffair(affair: IAffairService.Affair): Completable {
    return AffairRepository.updateAffair(
      affair.onlyId,
      affair.time,
      affair.title,
      affair.content,
      listOf(
        AffairEntity.AtWhatTime(
          affair.beginLesson,
          affair.day,
          affair.period,
          listOf(affair.week)
        )
      )
    )
  }
  
  override fun startActivityForAddAffair(
    week: Int,
    day: Int,
    beginLesson: Int,
    period: Int
  ) {
    AffairActivity.startForAdd(week, day, beginLesson, period)
  }
  
  override fun startActivityForEditActivity(onlyId: Int) {
    AffairActivity.startForEdit(onlyId)
  }

  override fun startActivityForNoClass(noClassBean: NoClassBean) {
    NoClassAffairActivity.startForNoClass(noClassBean)
  }

  private fun List<AffairEntity>.toAffair(): List<IAffairService.Affair> {
    return buildList {
      this@toAffair.forEach { entity ->
        entity.atWhatTime.forEach { atWhatTime ->
          atWhatTime.week.forEach { week ->
            add(
              IAffairService.Affair(
                entity.stuNum,
                entity.onlyId,
                entity.time,
                entity.title,
                entity.content,
                week,
                atWhatTime.beginLesson,
                atWhatTime.day,
                atWhatTime.period
              )
            )
          }
        }
      }
    }
  }
}