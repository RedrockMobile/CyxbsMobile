package com.cyxbs.pages.home.ui.course.utils

import android.util.Log
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.api.ILinkService
import com.cyxbs.pages.course.api.utils.getEndRow
import com.cyxbs.pages.course.api.utils.getEndTimeMinute
import com.cyxbs.pages.course.api.utils.getShowTimeStr
import com.cyxbs.pages.course.api.utils.getStartRow
import com.cyxbs.pages.course.api.utils.getStartTimeMinute
import com.cyxbs.pages.course.api.utils.parseClassRoom
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asObservable
import okhttp3.internal.filterList
import java.util.Calendar
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/14 17:03
 */
object CourseHeaderHelper {

  private const val TAG = "CourseHeaderHelper"

  private val lessonService = ILessonService::class.impl()
  private val linkService = ILinkService::class.impl()
  private val affairService = IAffairService::class.impl()

  /**
   * 观察课表头的变化
   */
  fun observeHeader(): Observable<Header> {
    return SchoolCalendar.observeWeekOfTerm()
      .asObservable()
      .observeOn(Schedulers.io())
      .switchMap { week ->
        if (week !in 1..ICourseService.maxWeek) {
          observeHeaderOnVacation()
        } else observeHeader1(week)
          .startWithItem(HintHeader("加载数据中"))
          .retry(3) // 存在中途断网的情况，这个时候调用 getStuLesson() 会抛异常，所以重新订阅以检查网络是否可用
          .doOnError { Log.d(TAG, "课表请求异常: \n${it.stackTraceToString()}") }
          .onErrorReturn {
            // 因为上流用的观察流，一般是不会发送异常到下流的，除了在断网时调用 getStuLesson()，
            // 但已经经过 retry 后还是出错，可能就是内部异常了
            HintHeader("", it)
          }
      }
      // 如果在获取不了周数时说明没有请求过课表数据，因为周数是从课表接口来的
      .startWithItem(HintHeader(""))
  }

  /**
   * 放假期间的课表头
   */
  private fun observeHeaderOnVacation(): Observable<Header> {
    val textOnVacation = "享受假期吧～"
    return IAccountService::class.impl().state
      .map { (it as? AccountState.Login)?.stuNum.orEmpty() }
      .asObservable()
      .switchMap {
        if (it.isEmpty()) Observable.empty() else {
          lessonService.refreshLesson(it)
            .toObservable()
            .flatMap { Observable.empty<Header>() }
            .doOnError {
              if (isDebug()) {
                toast("课表崩了，长按课表头显示")
              }
            }.onErrorReturn { throwable ->
              HintHeader(textOnVacation, throwable)
            }
        }
      }.startWithItem(HintHeader(textOnVacation))
  }

  /**
   * 处理是否允许使用本地数据的逻辑
   */
  private fun observeHeader1(
    nowWeek: Int
  ): Observable<Header> {
    return flow {
      if (ILessonService.isUseLocalSaveLesson) {
        // 可以使用本地数据时
        emit(true)
      } else {
        if (NetworkUtil.isAvailableExact()) {
          emit(true)
        } else {
          // 网络不可用，并且也不能使用本地数据
          emit(false)
          // 挂起，一直直到网络可用
          NetworkUtil.suspendUntilAvailable()
          toast("网络已恢复，正在加载课表中")
          emit(true) // 重新请求网络数据
        }
      }
    }.asObservable()
      .switchMap {
        if (it) observeHeader2(nowWeek) else Observable.just(HintHeader("联网才能查看课表哦~"))
      }
  }

  /**
   * 处理是否登录的逻辑
   */
  private fun observeHeader2(
    nowWeek: Int
  ): Observable<Header> {
    return linkService.observeSelfLinkStu()
      .switchMap { linkData ->
        if (linkData.selfNum.isBlank()) {
          Observable.just(HintHeader("登录后即可查看课表"))
        } else {
          observeHeader3(
            nowWeek,
            linkData.selfNum,
            if (linkData.isShowLink) linkData.linkNum else "",
          )
        }
      }
  }

  /**
   * 处理数据流合并的逻辑
   */
  private fun observeHeader3(
    nowWeek: Int,
    selfNum: String,
    linkNum: String,
  ): Observable<Header> {
    // 这里不需要调用 observeSelfLesson()，因为外面的 observeSelfLinkStu() 有观察 self 的作用
    val selfSingle = lessonService.getStuLesson(selfNum)
    val linkSingle =
      if (linkNum.isNotBlank()) lessonService.getStuLesson(linkNum)
      else Single.just(emptyList())
    val affairObservable = affairService.observeSelfAffair()
    // combineLast 可以同时观察任一个 Observable，
    // 只要收到一个新的，他就会整和数据发给下游，不同于 zip 操作符，
    // zip 操作符需要三个都发送新的才会整合发给下游
    return Observable.combineLatest(
      selfSingle.toObservable(),
      linkSingle.toObservable(),
      affairObservable
    ) { self, link, affair ->
      Triple(self, link, affair)
    }.switchMap { (self, link, affair) ->
      // 每分钟流动一次，用于刷新课表头
      Observable.interval(0, 1, TimeUnit.MINUTES)
        .map {
          getHeader(
            selfNum,
            nowWeek,
            self + link,
            affair,
          )
        }
    }
  }

  private fun getHeader(
    selfNum: String,
    nowWeek: Int,
    lessonList: List<ILessonService.Lesson>,
    affairList: List<IAffairService.Affair>,
  ): Header {
    val calendar = Calendar.getInstance()
    /*
    * 转换星期数，为了跟 hashDay 相匹配
    * (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 的逻辑如下：
    * 星期天：1 -> 6
    * 星期一：2 -> 0
    * 星期二：3 -> 1
    * 星期三：4 -> 2
    * 星期四：5 -> 3
    * 星期五：6 -> 4
    * 星期六：7 -> 5
    *
    * 左边一栏是 Calendar.get(Calendar.DAY_OF_WEEK) 得到的数字，
    * 右边一栏是该数字距离周一的天数差
    * */
    val todayHashDay = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val tomorrowHashDay = (todayHashDay + 1) % 7
    val treeSet = TreeSet<Item> { o1, o2 ->
      if (o1 === o2) return@TreeSet 0 // 同一个对象，直接返回 0
      // 使用 abs() 避免明天是下一周周一的情况
      val absDiffDay1 = abs(o1.hashDay - todayHashDay)
      val absDiffDay2 = abs(o2.hashDay - todayHashDay)
      val diff = if (absDiffDay1 == absDiffDay2) {
        if (o1.startTime == o2.startTime) {
          // 根据先后顺序来排
          o1.rank - o2.rank
        } else o1.startTime - o2.startTime
      } else absDiffDay1 - absDiffDay2
      if (diff == 0) {
        // 这里防止前面算出来 0，导致 o2 把 o1 给抵掉了
        o1.hashCode() - o2.hashCode()
      } else diff
    }
    treeSet.addAll(
      lessonList.filterList {
        /*
        * 筛选出今天和明天的课
        * 存在两种情况：
        * 1、明天是下周一
        * 2、明天就在这周
        * */
        if (tomorrowHashDay == 0) {
          // 明天是下周一
          week == nowWeek && hashDay == todayHashDay
              || week == nowWeek + 1 && hashDay == tomorrowHashDay
        } else {
          week == nowWeek && (hashDay == todayHashDay || hashDay == tomorrowHashDay)
        }
      }.map { LessonItem(it, it.stuNum == selfNum) }
    )
    treeSet.addAll(
      affairList.filterList {
        /*
        * 筛选出今天和明天的课
        * 存在两种情况：
        * 1、明天是下周一
        * 2、明天就在这周
        * */
        if (tomorrowHashDay == 0) {
          // 明天是下周一
          week == nowWeek && day == todayHashDay
              || week == nowWeek + 1 && day == tomorrowHashDay
        } else {
          week == nowWeek && (day == todayHashDay || day == tomorrowHashDay)
        }
      }.map { AffairItem(it) }
    )
    val nowTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val iterator = treeSet.iterator()
    while (iterator.hasNext()) {
      val item = iterator.next()
      if (item.hashDay == todayHashDay) {
        if (nowTime < item.startTime) {
          return when (item) {
            is LessonItem -> ShowHeader(
              if (item.isSelf) "下节课" else "Ta的下节课",
              item.lesson.course,
              getShowTimeStr(item.lesson.beginLesson, item.lesson.period),
              parseClassRoom(item.lesson.classroom),
              item
            )

            is AffairItem -> ShowHeader(
              "下个事务",
              item.affair.title,
              getShowTimeStr(item.affair.beginLesson, item.affair.period),
              item.affair.content,
              item
            )
          }
        } else if (nowTime < item.endTime) {
          return when (item) {
            is LessonItem -> ShowHeader(
              if (item.isSelf) "进行中..." else "Ta的课进行中...",
              item.lesson.course,
              getShowTimeStr(item.lesson.beginLesson, item.lesson.period),
              parseClassRoom(item.lesson.classroom),
              item
            )

            is AffairItem -> ShowHeader(
              "进行中...",
              item.affair.title,
              getShowTimeStr(item.affair.beginLesson, item.affair.period),
              item.affair.content,
              item
            )
          }
        }
      } else if (item.hashDay == tomorrowHashDay) {
        if (item is LessonItem) {
          // 这里我想了下，还是优先显示自己的课比较好，所以关联人的课就跳过
          if (!item.isSelf) continue
        }
        return when (item) {
          is LessonItem -> ShowHeader(
            "明天",
            item.lesson.course,
            getShowTimeStr(item.lesson.beginLesson, item.lesson.period),
            parseClassRoom(item.lesson.classroom),
            item
          )

          is AffairItem -> ShowHeader(
            "明天事务",
            item.affair.title,
            getShowTimeStr(item.affair.beginLesson, item.affair.period),
            item.affair.content,
            item
          )
        }
      }
    }
    // 注释掉，因为没得这个需求
    //    if (treeSet.isNotEmpty()) {
    //      // 如果前面没有 return，且 treeSet 不为空，就说明今天有课但上完了，而且明天也没得课
    //      return HintHeader("明天一整天都没课 :)")
    //    }
    return HintHeader("今天和明天都没课咯～")
  }


  /**
   * @param hashDay 星期数，星期一为 0
   * @param beginLesson 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
   * @param period 课的长度
   * @param rank 先后顺序，再开始时间都相同的时候，会用它作为标准来判断谁在前面，越小的越在前面
   */
  sealed class Item(
    val hashDay: Int,
    beginLesson: Int,
    period: Int,
    val rank: Int,
  ) {
    // 开始时间大小，为 小时数 * 60 + 分钟数
    val startTime: Int = getStartTimeMinute(getStartRow(beginLesson))

    // 结束时间大小，为 小时数 * 60 + 分钟数
    val endTime: Int = getEndTimeMinute(getEndRow(beginLesson, period))
  }

  data class LessonItem(
    val lesson: ILessonService.Lesson,
    val isSelf: Boolean
  ) : Item(
    lesson.hashDay,
    lesson.beginLesson,
    lesson.period,
    if (isSelf) 0 else 2 // 自己的课优先在前面
  )

  data class AffairItem(
    val affair: IAffairService.Affair
  ) : Item(
    affair.day,
    affair.beginLesson,
    affair.period,
    1 // 事务放到自己课后面，关联人课前面
  )

  sealed interface Header

  data class HintHeader(
    val hint: String,
    val throwable: Throwable? = null
  ) : Header

  data class ShowHeader(
    val state: String,
    val title: String,
    val time: String,
    val content: String,
    val item: Item
  ) : Header
}