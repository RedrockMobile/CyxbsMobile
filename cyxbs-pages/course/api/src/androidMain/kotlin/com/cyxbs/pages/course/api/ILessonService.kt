package com.cyxbs.pages.course.api

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.io.Serializable

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/14 17:05
 */
interface ILessonService {
  
  companion object {
  
    /**
     * 在 22 年 10 月，掌邮因为获取信息不合法需要整改，
     * 后端课表接口由最开始的爬取 jwzx，改为了由教务在线提供的官方接口
     * 但他们要求我们不能本地本地保存课表，
     * 所以增加该变量控制课表本地保存逻辑
     *
     * 目前逻辑为课表数据仍会保存至数据库，但在网络请求失败时会直接返回空数据
     *
     * 23 年 10 月，恢复课表缓存
     * we 重邮都有缓存，为什么我们不能用呢?
     * 经过跟 wyh 商讨，我们最终决定恢复课表缓存
     */
    val isUseLocalSaveLesson
      get() = true
  }

  /**
   * 刷新当前学号的课
   * - 上游已主动切换成 io 线程
   * - 在得不到这个人课表数据时会抛出异常，比如学号为空串时
   */
  fun refreshLesson(stuNum: String): Single<List<Lesson>>
  
  /**
   * 直接得到当前学号的课
   * - 上游已主动切换成 io 线程
   * - 在得不到这个人课表数据时会抛出异常，比如学号为空串时
   */
  fun getStuLesson(stuNum: String): Single<List<Lesson>>
  
  /**
   * 得到当前登录人的课
   * - 上游已主动切换成 io 线程
   * - 在得不到这个人课表数据时会抛出异常
   */
  fun getSelfLesson(): Single<List<Lesson>>
  
  /**
   * 得到当前关联人的课
   * - 上游已主动切换成 io 线程
   * - 在不存在关联人和得不到这个人课表数据时会抛出异常
   */
  fun getLinkLesson(): Single<List<Lesson>>
  
  /**
   * 观察当前登录人的课
   * - 在登录人改变时回调
   * - 已使用 distinctUntilChanged() 进行了去重处理
   * - 上游已主动切换成 io 线程
   * - 没登录时发送 emptyList()
   * - 没有连接网络并且不允许使用本地缓存时会一直不发送数据给下游
   * - 不会抛出异常给下游
   */
  fun observeSelfLesson(
    needRefresh: Boolean,
  ): Observable<List<Lesson>>

  /**
   * 观察当前关联人的课
   * - 在登录人改变时回调
   * - 已使用 distinctUntilChanged() 进行了去重处理
   * - 上游已主动切换成 io 线程
   * - 没登录时发送 emptyList()
   * - 没有连接网络并且不允许使用本地缓存时会一直不发送数据给下游
   * - 不会抛出异常给下游
   */
  fun observeLinkLesson(
    needRefresh: Boolean,
  ): Observable<List<Lesson>>
  
  /**
   * 这里提供 Calendar 与 [hashDay] 互换代码
   * ```
   * 转换星期数，为了跟 hashDay 相匹配
   * 互换代码：(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
   * 逻辑如下：
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
   * ```
   */
  data class Lesson(
    val stuNum: String,
    val week: Int, // 第几周的课
    val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    val classroom: String, // 教室
    val course: String, // 课程名
    val courseNum: String, // 课程号
    val day: String, // 星期几，这是字符串的星期几：星期一、星期二......
    val hashDay: Int, // 星期数，星期一为 0
    val period: Int, // 课的长度
    val rawWeek: String, // 周期
    val teacher: String,
    val type: String, // 选修 or 必修
  ) : Serializable
}