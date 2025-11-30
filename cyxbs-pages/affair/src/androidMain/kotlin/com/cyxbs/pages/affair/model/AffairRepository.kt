package com.cyxbs.pages.affair.model

import android.annotation.SuppressLint
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.throwApiExceptionIfFail
import com.cyxbs.components.utils.utils.config.PhoneCalendar
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.affair.bean.TodoListPushWrapper
import com.cyxbs.pages.affair.bean.toAffairDateBean
import com.cyxbs.pages.affair.net.AffairApiService
import com.cyxbs.pages.affair.room.AffairCalendarEntity
import com.cyxbs.pages.affair.room.AffairDataBase
import com.cyxbs.pages.affair.room.AffairEntity
import com.cyxbs.pages.affair.room.AffairEntity.Companion.LocalRemoteId
import com.cyxbs.pages.affair.room.AffairIncompleteEntity
import com.cyxbs.pages.affair.room.LocalAddAffairEntity
import com.cyxbs.pages.affair.room.LocalDeleteAffairEntity
import com.cyxbs.pages.affair.room.LocalUpdateAffairEntity
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.api.utils.checkCourseItem
import com.cyxbs.pages.course.api.utils.getEndRow
import com.cyxbs.pages.course.api.utils.getEndTimeMinute
import com.cyxbs.pages.course.api.utils.getStartRow
import com.cyxbs.pages.course.api.utils.getStartTimeMinute
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.internal.functions.Functions
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asObservable
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/3 19:30
 */
@SuppressLint("CheckResult")
object AffairRepository {

  private val Api = AffairApiService.INSTANCE

  private val DB = AffairDataBase
  private val AffairDao = DB.getAffairDao()
  private val AffairCalendarDao = DB.getAffairCalendarDao()
  private val LocalAddDao = DB.getLocalAddAffairDao()
  private val LocalUpdateDao = DB.getLocalUpdateAffairDao()
  private val LocalDeleteDao = DB.getLocalDeleteAffairDao()

  private val mGson = defaultGson

  private fun List<AffairEntity.AtWhatTime>.toPostDateJson(): String {
    // 不建议让 AtWhatTime 成为转 json 的类，应该转换成 AffairDateBean 转 json
    return mGson.toJson(toAffairDateBean())
  }

  /**
   * 观察当前登录人的事务
   * - 支持换账号登录后返回新登录人的数据
   * - 第一次观察时会请求新的数据
   * - 使用了 distinctUntilChanged()，只会在数据更改了才会回调
   * - 上游不会抛出错误到下游
   */
  fun observeAffair(
    needRefresh: Boolean,
  ): Observable<List<AffairEntity>> {
    return IAccountService::class.impl()
      .stuNumFlow
      .map { it.orEmpty() }
      .asObservable()
      .observeOn(Schedulers.io())
      .switchMap {
        // 使用 switchMap 可以停止之前学号的订阅
        if (it.isEmpty()) Observable.just(emptyList()) else {
          AffairDao.observeAffair(it)
            .distinctUntilChanged()
            .doOnSubscribe {
              if (needRefresh) {
                // 观察时先请求一次最新数据
                refreshAffair().unsafeSubscribeBy()
              }
            }.subscribeOn(Schedulers.io())
        }
      }
  }

  /**
   * 刷新事务
   */
  fun refreshAffair(): Single<List<AffairEntity>> {
    val selfNum: String = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isBlank()) return Single.just(emptyList())
    // 先上传本地临时数据，只有本地临时数据全部上传后才能下载新的数据，防止数据混乱
    return uploadLocalAffair(selfNum)
      .andThen(Api.getAffair())
      .throwApiExceptionIfFail()
      .map {
        // 转换数据并插入数据库
        val affairIncompleteEntity = it.toAffairIncompleteEntity()
        AffairDao.resetData(selfNum, affairIncompleteEntity)
      }.subscribeOn(Schedulers.io())
  }

  /**
   * 得到事务，但不建议你直接使用，应该用 [observeAffair] 来代替
   *
   * 永远不会给下游抛出异常
   */
  fun getAffair(): Single<List<AffairEntity>> {
    val selfNum: String = IAccountService::class.impl().stuNum.orEmpty()
    if (selfNum.isBlank()) return Single.just(emptyList())
    return refreshAffair().onErrorReturn {
      // 上游失败了就取本地数据，可能是网络失败，也可能是本地临时上传事务失败
      AffairDao.getAffairByStuNum(selfNum)
    }
  }

  /**
   * 添加事务，请使用 [observeAffair] 进行观察数据
   */
  fun addAffair(
    time: Int,
    title: String,
    content: String,
    atWhatTime: List<AffairEntity.AtWhatTime>,
  ): Completable {
    val stuNum = IAccountService::class.impl().stuNum.orEmpty()
    if (stuNum.isBlank()) return Completable.error(IllegalStateException("学号为空！"))
    if (atWhatTime.any { !checkCourseItem(it.beginLesson, it.period) }) {
      return Completable.error(IllegalArgumentException("事务越界：$atWhatTime"))
    }
    val dateJson = atWhatTime.toPostDateJson()
    return Single.create {
      // 先使用 LocalRemoteId 保存进本地数据库，后续网络请求后再更新
      val entity = AffairIncompleteEntity(LocalRemoteId, time, title, content, atWhatTime)
      val onlyId = AffairDao
        .insertAffair(stuNum, entity) // 优先添加进数据库，保证用户先看到 ui
        .onlyId
      it.onSuccess(onlyId)
    }.doOnSuccess { onlyId ->
      // 这里进行异步上传
      Api.addAffair(time, title, content, dateJson)
        .throwApiExceptionIfFail()
        .doOnSuccess {
          // 更新本地 remoteId
          AffairDao.updateRemoteId(stuNum, onlyId, it.remoteId)
        }.doOnError {
          // 网络请求失败，保存进临时数据库
          val localEntity = LocalAddAffairEntity(stuNum, onlyId, time, title, content, dateJson)
          LocalAddDao.insertLocalAddAffair(localEntity)
        }.unsafeSubscribeBy()
    }.doOnSuccess { onlyId ->
      insertCalendarAfterClear(onlyId, time, title, content, atWhatTime)
    }.flatMapCompletable { Completable.complete() }
      .subscribeOn(Schedulers.io())
  }

  /**
   * 更新事务，请使用 [observeAffair] 进行观察数据
   */
  fun updateAffair(
    onlyId: Int,
    time: Int,
    title: String,
    content: String,
    atWhatTime: List<AffairEntity.AtWhatTime>,
  ): Completable {
    val stuNum = IAccountService::class.impl().stuNum.orEmpty()
    if (stuNum.isBlank()) return Completable.error(IllegalStateException("学号为空"))
    return updateAffairInternal(stuNum, onlyId, time, title, content, atWhatTime)
      .doOnComplete {
        insertCalendarAfterClear(onlyId, time, title, content, atWhatTime)
      }.subscribeOn(Schedulers.io())
  }

  @SuppressLint("CheckResult")
  private fun updateAffairInternal(
    stuNum: String,
    onlyId: Int,
    time: Int,
    title: String,
    content: String,
    atWhatTime: List<AffairEntity.AtWhatTime>
  ): Completable {
    return AffairDao.findAffairByOnlyId(stuNum, onlyId)
      .toSingle() // 找不到时直接抛错，更新操作还能找不到?
      .map { it.remoteId }
      .doOnSuccess { remoteId ->
        // 更新本地数据库，更新后 ui 就会同步刷新
        AffairDao.updateAffair(
          AffairEntity(
            stuNum,
            onlyId,
            remoteId,
            time,
            title,
            content,
            atWhatTime
          )
        )
      }.doOnSuccess { remoteId ->
        val dateJson = atWhatTime.toPostDateJson()
        if (remoteId == LocalRemoteId) {
          // 如果是本地临时事务，就直接更新临时添加的事务
          LocalAddDao
            .updateLocalAddAffair(
              LocalAddAffairEntity(stuNum, onlyId, time, title, content, dateJson)
            )
        } else {
          // 不是本地临时事务就上传，这里异步上传
          Api.updateAffair(remoteId, time, title, content, dateJson)
            .throwApiExceptionIfFail()
            .doOnError {
              // 上传失败就暂时保存在本地临时更新的事务中
              // insert 已改为 OnConflictStrategy.REPLACE，可进行替换插入
              LocalUpdateDao
                .insertLocalUpdateAffair(
                  LocalUpdateAffairEntity(
                    stuNum, onlyId, remoteId, time, title, content, dateJson
                  )
                )
            }.unsafeSubscribeBy()
        }
      }.flatMapCompletable { Completable.complete() }
  }

  /**
   * 删除事务，请使用 [observeAffair] 进行观察数据
   */
  fun deleteAffair(onlyId: Int): Completable {
    val stuNum = IAccountService::class.impl().stuNum.orEmpty()
    if (stuNum.isEmpty()) return Completable.error(IllegalStateException("学号为空"))
    return deleteAffairInternal(stuNum, onlyId)
      .doOnComplete {
        // 删除手机上的日历
        AffairCalendarDao.remove(onlyId).forEach {
          PhoneCalendar.delete(it)
        }
      }.subscribeOn(Schedulers.io())
  }

  @SuppressLint("CheckResult")
  private fun deleteAffairInternal(stuNum: String, onlyId: Int): Completable {
    return Single.create {
      try {
        // 找不到时直接抛错，删除操作还能找不到?
        val entity = AffairDao.deleteAffair(stuNum, onlyId)!!
        it.onSuccess(entity)
      } catch (e: Exception) {
        it.tryOnError(e)
      }
    }.doOnSuccess { entity ->
      if (entity.remoteId == LocalRemoteId) {
        // 如果是本地临时事务，就直接删除临时添加的事务
        LocalAddDao.deleteLocalAddAffair(stuNum, onlyId)
      } else {
        // 不是本地临时事务就上传，这里异步上传
        Api.deleteAffair(entity.remoteId)
          .throwApiExceptionIfFail()
          .doOnError {
            // 上传失败就暂时保存在本地临时删除的事务中
            LocalDeleteDao.insertLocalDeleteAffair(
              LocalDeleteAffairEntity(stuNum, onlyId, entity.remoteId)
            )
            // 然后尝试删除本地临时更新的事务，不管有没有
            LocalUpdateDao.deleteLocalUpdateAffair(stuNum, onlyId)
          }.unsafeSubscribeBy()
      }
    }.flatMapCompletable { Completable.complete() }
  }

  fun addTodo(pushWrapper: TodoListPushWrapper) = AffairApiService
    .INSTANCE
    .pushTodo(pushWrapper)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())

  private val uploadLocalAffairSynchronized = Any()

  /**
   * 发送本地临时保存的事务
   *
   * ## 注意：
   * ### 不使用 Rxjava 多条流的原因
   * 最开始采用的 Rxjava 多条流合并，结果发现在使用 runInTransaction 后导致线程死锁，
   * 最后就直接改成单线程运行了
   *
   * ### 使用接口包裹的原因
   * localAdd、localUpdate、localDelete 三个都是接口，
   * 一是为了同时在 runInTransaction  使用
   * 二是为了可读性，并没有全部写在 Completable.create {} 里面
   */
  @SuppressLint("CheckResult")
  private fun uploadLocalAffair(stuNum: String): Completable {
    var hasLocalAffair = false
    // 本地临时添加的事务
    val localAdd = {
      LocalAddDao.getLocalAddAffair(stuNum).forEach { entity ->
        hasLocalAffair = true
        Api.addAffair(entity.time, entity.title, entity.content, entity.dateJson) // 网络请求
          .throwApiExceptionIfFail()
          .doOnSuccess {
            // 上传成功就删除
            LocalAddDao.deleteLocalAddAffair(entity)
          }.doOnSuccess {
            // 上传成功就修改 remoteId
            AffairDao.updateRemoteId(stuNum, entity.onlyId, it.remoteId)
          }.blockingGet() // 直接同步请求，原因请看该方法注释
      }
    }

    // 本地临时更新的事务
    val localUpdate = {
      LocalUpdateDao.getLocalUpdateAffair(stuNum).forEach { entity ->
        hasLocalAffair = true
        Api.updateAffair(
          entity.remoteId, // 注意：这里需要使用 remoteId
          entity.time,
          entity.title,
          entity.content,
          entity.dateJson
        ).throwApiExceptionIfFail()
          .doOnSuccess {
            // 上传成功就删除
            LocalUpdateDao.deleteLocalUpdateAffair(entity)
          }.blockingGet() // 直接同步请求，原因请看该方法注释
      }
    }

    // 本地临时删除的事务
    val localDelete = {
      LocalDeleteDao.getLocalDeleteAffair(stuNum).forEach { entity ->
        hasLocalAffair = true
        Api.deleteAffair(entity.remoteId) // 注意：这里需要使用 remoteId
          .throwApiExceptionIfFail()
          .doOnSuccess {
            // 上传成功就删除
            LocalDeleteDao.deleteLocalDeleteAffair(entity)
          }.blockingGet() // 直接同步请求，原因请看该方法注释
      }
    }

    return Completable.create { emitter ->
      try {
        // 加锁保证只会触发一次
        synchronized(uploadLocalAffairSynchronized) {
          localAdd.invoke()
          localUpdate.invoke()
          localDelete.invoke()
        }
        emitter.onComplete()
      } catch (e: Exception) {
        emitter.tryOnError(e)
      }
    }.toObservable<Unit>().flatMapCompletable {
      // 进行延时处理，防止同步问题
      Completable.complete()
        .delay(if (hasLocalAffair) 200 else 0, TimeUnit.MILLISECONDS)
    }.observeOn(Schedulers.io())
  }

  /**
   * 先清理 [onlyId] 已经添加进的手机日历
   * 再添加进手机日历
   */
  private fun insertCalendarAfterClear(
    onlyId: Int,
    time: Int,
    title: String,
    content: String,
    atWhatTime: List<AffairEntity.AtWhatTime>
  ) {
    // 更新手机上的日历，采取先删除再添加的方式，因为一个事务对应了多个日历中的安排，不好更新
    /*
    * todo 如果以后有学弟要更新事务逻辑，我的建议是将事务改成设置一个事务长度后，进入编辑界面时只能设置出现在哪几天内，
    *  而不是仍能设置其他长度的事务。
    * 这样一个事务才能与日历中的一个事务才能对应起来
    * */
    AffairCalendarDao.remove(onlyId).forEach {
      PhoneCalendar.delete(it)
    }
    val firstMonDay = SchoolCalendar.getFirstMonDayTimestamp() ?: return
    // 只有大于 0 才有提醒，只有需要提醒的才写进手机日历
    if (time > 0) {
      val eventIdList = arrayListOf<Long>()
      atWhatTime.forEach { whatTime ->
        val startMinute = getStartTimeMinute(getStartRow(whatTime.beginLesson))
        val endMinute = getEndTimeMinute(getEndRow(whatTime.beginLesson, whatTime.period))
        // 如果是整学期,添加重复事件
        if (whatTime.week.any { it == 0 }) {
          PhoneCalendar.add(
            PhoneCalendar.FrequencyEvent(
              title = title,
              description = content,
              remind = time,
              duration = PhoneCalendar.Event.Duration(
                minute = endMinute - startMinute
              ),
              startTime = (Calendar.getInstance()).apply {
                timeInMillis = firstMonDay
                add(Calendar.DATE, whatTime.day)
                add(Calendar.MINUTE, startMinute)
              },
              freq = PhoneCalendar.FrequencyEvent.Freq.WEEKLY,
              count = ICourseService.maxWeek
            )
          )?.also { eventIdList.add(it) }
        } else {
          // 如果不是整学期,添加一次性事件
          PhoneCalendar.add(
            PhoneCalendar.CommonEvent(
              title = title,
              description = content,
              remind = time,
              duration = PhoneCalendar.Event.Duration(
                minute = endMinute - startMinute
              ),
              startTime = whatTime.week.map {
                (Calendar.getInstance()).apply {
                  timeInMillis = firstMonDay
                  add(Calendar.DATE, whatTime.day + (it - 1) * 7)
                  add(Calendar.MINUTE, startMinute)
                }
              }
            )
          )?.also { eventIdList.add(it) }
        }
      }
      if (eventIdList.isNotEmpty()) {
        AffairCalendarDao.insert(AffairCalendarEntity(onlyId, eventIdList))
      }
    }
  }

  init {
    // 监听网络状态，在网络恢复时上传临时事务
    NetworkUtil.state
      .filter { it }
      .doOnNext {
        uploadLocalAffair(IAccountService::class.impl().stuNum.orEmpty())
          .subscribeOn(Schedulers.io())
          .subscribe(Functions.EMPTY_ACTION, Functions.emptyConsumer())
      }.subscribe()
  }
}