package com.cyxbs.pages.course.page.course.model

import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.interceptException
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.page.course.bean.StuLessonBean
import com.cyxbs.pages.course.page.course.network.CourseApiServices
import com.cyxbs.pages.course.page.course.room.LessonDataBase
import com.cyxbs.pages.course.page.course.room.StuLessonEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asObservable
import java.util.concurrent.TimeUnit

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/3 18:23
 */
@Suppress("LiftReturnOrAssignment")
object StuLessonRepository {
  
  private val mStuDB = LessonDataBase.stuLessonDao

  // 上一次更新的天数差
  private var mLastUpdateDay: Int
    get() {
      val current = System.currentTimeMillis()
      val timestamp = defaultSp.getLong("上一次更新课表的时间戳", current)
      return TimeUnit.DAYS.convert(current - timestamp, TimeUnit.MILLISECONDS).toInt()
    }
    set(value) {
      defaultSp.edit {
        putLong(
          "上一次更新课表的时间戳",
          System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(value.toLong(), TimeUnit.DAYS)
        )
      }
    }
  
  /**
   * 观察当前登录人的课
   * - 支持换账号登录后返回新登录人的数据
   * - 第一次观察时会请求新的数据
   * - 使用了 distinctUntilChanged()，只会在数据更改了才会回调
   * - 没登录时发送 emptyList()
   * - 没有连接网络并且不允许使用本地缓存时会一直不发送数据给下游
   * - 不会抛出异常给下游
   *
   * @param isToast 是否提示课表正在使用缓存数据
   */
  fun observeSelfLesson(
    isToast: Boolean = false,
  ): Observable<List<StuLessonEntity>> {
    return IAccountService::class.impl()
      .stuNumFlow
      .map { it.orEmpty() }
      .asObservable()
      .observeOn(Schedulers.io())
      .switchMap { stuNum ->
        // 使用 switchMap 可以停止之前学号的订阅
        if (stuNum.isEmpty()) Observable.just(emptyList()) else {
          if (ILessonService.isUseLocalSaveLesson) {
            LessonDataBase.stuLessonDao
              .observeLesson(stuNum)
              .doOnSubscribe {
                // 在开始订阅时异步请求一次云端数据，所以下游会先拿到本地数据库中的数据，如果远端数据更新了，整个流会再次通知
                refreshLesson(stuNum).doOnError {
                  if (isToast) {
                    val lastUpdateDay = mLastUpdateDay
                    if (lastUpdateDay < 1) {
                      toastLong("课表正在使用缓存\n不能保证数据正确性")
                    } else {
                      toastLong("已 $lastUpdateDay 天未更新课表\n建议联网更新")
                    }
                  }
                }.unsafeSubscribeBy {
                  mLastUpdateDay = 0
                }
              }.distinctUntilChanged() // 去重
              .subscribeOn(Schedulers.io())
          } else {
            // 不允许使用本地数据时
            flow {
              // 直到第一次网络请求成功前一直挂起，不返回结果给下游
              NetworkUtil.suspendUntilAvailable()
              emit(Unit)
            }.asObservable()
              .switchMap {
                refreshLesson(stuNum)
                  .onErrorComplete() // 网络请求的异常全部吞掉
                  .toObservable()
              }
          }
        }
      }
  }
  
  /**
   * 刷新某人的课，会抛出网络异常
   */
  fun refreshLesson(stuNum: String): Single<List<StuLessonEntity>> {
    if (stuNum.isBlank()) return Single.error(IllegalArgumentException("学号不能为空！"))
    return CourseApiServices::class.api
      .getStuLesson(stuNum)
      .map { bean ->
        if (bean.status == 30001) {
          toast("当前教务在线不可用")
        }
        // 检查网络请求数据是否出错
        bean.throwApiExceptionIfFail()
        httpFromStuSuccess(bean)
      }.subscribeOn(Schedulers.io())
  }
  
  /**
   * 得到某人的课，在得不到这个人课表数据时会抛出异常，
   */
  fun getLesson(
    stuNum: String,
  ): Single<List<StuLessonEntity>> {
    return refreshLesson(stuNum)
      .interceptException {
        if (ILessonService.isUseLocalSaveLesson) {
          val list = mStuDB.getLesson(stuNum)
          if (list.isNotEmpty()) {
            // 这里允许使用本地缓存的课表数据
            emitter.onSuccess(list)
          } else {
            // 本地数据库没有课时抛出异常
            // 当然，如果这个人是大四的本来就没有课也会抛，但是这里是网络请求出错时的处理，一般情况下不会遇到该情况
            // 除非刚好他没得课，并且网络请求也出错
            throw throwable
          }
        } else {
          throw throwable
        }
      }
  }
  
  @WorkerThread
  private fun httpFromStuSuccess(bean: StuLessonBean): List<StuLessonEntity> {
    if (bean.judgeVersion(true)) {
      val list = bean.toStuLessonEntity()
      // 更新日历
      SchoolCalendar.updateFirstCalendar(bean.nowWeek)
      mStuDB.resetData(bean.stuNum, list)
      return list
    } else {
      if (!ILessonService.isUseLocalSaveLesson) {
        // 不允许本地保存课表时直接返回数据
        return bean.toStuLessonEntity()
      }
      val localList = mStuDB.getLesson(bean.stuNum)
      if (localList.isNotEmpty()) {
        // 使用本地数据
        return localList
      } else {
        // 本地没有数据，只能使用网络数据
        val newList = bean.toStuLessonEntity()
        mStuDB.resetData(bean.stuNum, newList)
        return newList
      }
    }
  }
}