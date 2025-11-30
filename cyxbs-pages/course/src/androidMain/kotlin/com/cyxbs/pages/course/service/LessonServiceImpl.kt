package com.cyxbs.pages.course.service

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.page.course.model.StuLessonRepository
import com.cyxbs.pages.course.page.course.room.StuLessonEntity
import com.cyxbs.pages.course.page.link.model.LinkRepository
import com.g985892345.provider.api.annotation.ImplProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.rx3.asObservable

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/14 17:09
 */
@ImplProvider
object LessonServiceImpl : ILessonService {
  
  private val mAccountService = IAccountService::class.impl()

  override fun refreshLesson(
    stuNum: String,
  ): Single<List<ILessonService.Lesson>> {
    return StuLessonRepository.refreshLesson(stuNum)
      .map { it.toLesson() }
  }

  override fun getStuLesson(stuNum: String): Single<List<ILessonService.Lesson>> {
    return StuLessonRepository.getLesson(stuNum)
      .map { it.toLesson() }
  }
  
  override fun getSelfLesson(): Single<List<ILessonService.Lesson>> {
    val stuNum = mAccountService.stuNum.orEmpty()
    return if (stuNum.isBlank()) Single.error(IllegalStateException("未登录"))
    else getStuLesson(stuNum)
  }
  
  override fun getLinkLesson(): Single<List<ILessonService.Lesson>> {
    return LinkRepository.getLinkStudent()
      .doOnSuccess {
        if (it.isNull()) throw IllegalStateException("当前登录人(${it.selfNum}关联人为空)")
      }.flatMap {
        getStuLesson(it.selfNum)
      }
  }
  
  override fun observeSelfLesson(needRefresh: Boolean): Observable<List<ILessonService.Lesson>> {
    return observeSelfLessonInternal(needRefresh = needRefresh).map { it.toLesson() }
  }

  override fun observeLinkLesson(needRefresh: Boolean): Observable<List<ILessonService.Lesson>> {
    return observeLinkLessonInternal(needRefresh = needRefresh).map { it.toLesson() }
  }

  fun observeSelfLessonInternal(
    isToast: Boolean = false,
    needRefresh: Boolean = true,
  ): Observable<List<StuLessonEntity>> {
    return StuLessonRepository.observeSelfLesson(isToast = isToast, needRefresh = needRefresh)
  }

  fun observeLinkLessonInternal(needRefresh: Boolean): Observable<List<StuLessonEntity>> {
    return LinkRepository.observeLinkStudent()
      .switchMap { entity ->
        // 没得关联人和不显示关联课程时发送空数据
        if (entity.isNull() || !entity.isShowLink) Observable.just(emptyList()) else {
          flow {
            if (!ILessonService.isUseLocalSaveLesson) {
              // 如果不允许使用本地数据就挂起直到网络连接成功
              NetworkUtil.suspendUntilAvailable()
            }
            emit(Unit)
          }.asObservable()
            .flatMap {
              StuLessonRepository.observeLesson(entity.linkNum, needRefresh = needRefresh)
            }.onErrorReturn {
              emptyList()
            }
        }
      }
  }
}

fun List<StuLessonEntity>.toLesson(): List<ILessonService.Lesson> {
  return buildList {
    this@toLesson.forEach { entity ->
      entity.week.forEach { week ->
        add(
          ILessonService.Lesson(
            entity.stuNum,
            week,
            entity.beginLesson,
            entity.classroom,
            entity.course,
            entity.courseNum,
            entity.day,
            entity.hashDay,
            entity.period,
            entity.rawWeek,
            entity.teacher,
            entity.type
          )
        )
      }
    }
  }
}