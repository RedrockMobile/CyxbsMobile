package com.cyxbs.pages.course.page.link.model

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrThrowApiException
import com.cyxbs.components.utils.network.throwApiExceptionIfFail
import com.cyxbs.pages.course.page.link.network.LinkApiServices
import com.cyxbs.pages.course.page.link.room.LinkDataBase
import com.cyxbs.pages.course.page.link.room.LinkStuEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asObservable

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/3 19:02
 */
object LinkRepository {
  
  private val mLinkStuDB by lazyUnlock { LinkDataBase.INSTANCE.getLinkStuDao() }
  
  /**
   * 观察当前登录人的我的关联数据
   * - 支持换账号登录后返回新登录人的数据
   * - 没登录返回 [LinkStuEntity.NULL]
   * - 第一次观察时会请求新的数据
   * - 使用了 distinctUntilChanged()，只会在数据更改了才会回调
   * - 上游不会抛出错误到下游
   *
   * ## 注意
   * 只要开始订阅，就一定会发送数据下来，但是否有关联人请通过 [LinkStuEntity.isNull] 来判断
   */
  fun observeLinkStudent(): Observable<LinkStuEntity> {
    return IAccountService::class.impl()
      .stuNumFlow
      .map { it.orEmpty() }
      .asObservable()
      .observeOn(Schedulers.io())
      .switchMap {
        // 使用 switchMap 可以停止之前学号的订阅
        if (it.isEmpty()) Observable.just(LinkStuEntity.NULL) else {
          mLinkStuDB.observeLinkStu(it) // 然后观察数据库
            .distinctUntilChanged() // 必加，因为 Room 每次修改都会回调，所以需要加个这个去重
            .doOnSubscribe {
              // 在开始订阅时请求一次云端数据
              refreshLinkStudent().unsafeSubscribeBy()
            }.subscribeOn(Schedulers.io())
        }
      }
  }

  /**
   * 只是单纯的刷新数据，如果要观察请使用 [observeLinkStudent]
   */
  fun refreshLinkStudent(): Single<LinkStuEntity> {
    val selfNum = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isBlank()) return Single.error(IllegalStateException("学号为空！"))
    return LinkApiServices::class.api
      .getLinkStudent()
      .mapOrThrowApiException()
      .map {
        // 因为本地有其他字段，所以需要先拿本地数据库做比较
        val linkStu = mLinkStuDB.getLinkStu(selfNum)
        if (linkStu != null && linkStu.isNotNull()) {
          if (linkStu.linkNum == it.linkNum) {
            return@map linkStu
          }
        }
        // 这里说明与远端的关联人不一样，需要修改数据库
        // 但注意后端对应没有关联人时会返回空串，所以需要使用 it.isNotEmpty()
        val newLinkStu = LinkStuEntity(it, it.isNotEmpty())
        mLinkStuDB.insertLinkStu(newLinkStu)
        newLinkStu
      }.doOnError {
        // 如果网络失败就插一个空的值到数据库中，防止观察流不回调
        if (mLinkStuDB.getLinkStu(selfNum) == null) {
          mLinkStuDB.insertLinkStu(LinkStuEntity.NULL.copy(selfNum = selfNum))
        }
      }.subscribeOn(Schedulers.io())
  }
  
  /**
   * 只是单纯的得到数据，如果要观察请使用 [observeLinkStudent]
   *
   * ## 注意
   * - 只要学号不为空串，就不会返回异常
   * - 网络连接失败时会返回本地数据，本地数据为 null 时会返回一个空的 [LinkStuEntity]，但会包含自己的学号
   */
  fun getLinkStudent(): Single<LinkStuEntity> {
    val selfNum = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isBlank()) return Single.error(IllegalStateException("学号为空！"))
    return refreshLinkStudent()
      .onErrorReturn {
        // 这里说明网络连接失败，只能使用本地数据
        mLinkStuDB.getLinkStu(selfNum) ?: LinkStuEntity.NULL.copy(selfNum = selfNum)
      }.subscribeOn(Schedulers.io())
  }
  
  fun deleteLinkStudent(): Completable {
    val selfNum = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isEmpty()) return Completable.error(IllegalStateException("学号为空！"))
    return LinkApiServices::class.api
      .deleteLinkStudent()
      .throwApiExceptionIfFail()
      .flatMapCompletable {
        // 这个只能更新，不能使用删除，
        // 因为 Observable 不能发送 null，删除后的观察中是不会回调的，所以只能使用 update
        mLinkStuDB.updateLinkStu(LinkStuEntity.NULL.copy(selfNum = selfNum))
        Completable.complete()
      }.subscribeOn(Schedulers.io())
  }
  
  fun changeLinkStudent(linkNum: String): Single<LinkStuEntity> {
    return LinkApiServices::class.api
      .changeLinkStudent(linkNum)
      .mapOrThrowApiException()
      .map {
        LinkStuEntity(it, true)
      }.doOnSuccess {
        mLinkStuDB.insertLinkStu(it)
      }.subscribeOn(Schedulers.io())
  }
  
  fun changeLinkStuVisible(visible: Boolean): Completable {
    val selfNum = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isEmpty()) return Completable.error(IllegalStateException("学号为空！"))
    return Completable.create {
      val linkStuEntity = mLinkStuDB.getLinkStu(selfNum)
      if (linkStuEntity != null) {
        if (linkStuEntity.isShowLink != visible) {
          mLinkStuDB.updateLinkStu(linkStuEntity.copy(isShowLink = visible))
        }
        it.onComplete()
      } else {
        it.tryOnError(RuntimeException("数据库不存在该学号（$selfNum）的关联人"))
      }
    }.subscribeOn(Schedulers.io())
  }
}