package com.cyxbs.pages.affair.api

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single


/**
 * author: WhiteNight(1448375249@qq.com)
 * date: 2022/9/6
 * description:
 */
interface IAffairService {

  /**
   * 得到当前登录人的事务
   * - 上游已主动切换成 io 线程
   */
  fun getAffair(): Single<List<Affair>>
  
  /**
   * 强制刷新当前登录人的事务
   */
  fun refreshAffair(): Single<List<Affair>>
  
  /**
   * 观察当前登录人学号的所有事务
   * - 在数据库发生改变时回调
   * - 已进行了去重的处理
   * - 上游已主动切换成 io 线程
   * - 不会抛异常
   */
  fun observeSelfAffair(needRefresh: Boolean): Observable<List<Affair>>

  fun deleteAffair(onlyId: Int): Completable
  
  /**
   * 更新事务，只能更新不重复的单一事务 !!!
   *
   * ## 注意
   * 由于课表可以进行 item 的长按移动，但在移动事务时会涉及到重复事务对应多段时间的问题，
   * 所以目前暂时只能移动不重复的事务，该方法就是用来更新不重复的单一事务的
   *
   * 后面应该会考虑对事务进行重构，到时候该方法需要重新设计
   */
  fun updateAffair(affair: Affair): Completable
  
  data class Affair(
    val stuNum: String,
    val onlyId: Int, // 事务唯一 id
    val time: Int, // 提醒时间
    val title: String,
    val content: String,
    val week: Int, // 在哪一周，特别注意：整学期的 week 为 0
    val beginLesson: Int,  // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    val day: Int, // 星期数，星期一为 0
    val period: Int, // 长度
  )

  /**
   * 打开 AffairActivity，用于选择一个区域打算添加一个事务时调用
   */
  fun startActivityForAddAffair(
    week: Int, //周数，如果为 0，则表示整学期
    day: Int, // 星期数，星期一为 0
    beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    period: Int // 长度
  )

  /**
   * 打开 AffairActivity，用于修改一个事务
   * @param onlyId 事务唯一 id
   */
  fun startActivityForEditActivity(onlyId: Int)


  /**
   * 来自没课约的跳转，需要有不同于事务的界面
   */
  fun startActivityForNoClass(noClassBean: NoClassBean)
}