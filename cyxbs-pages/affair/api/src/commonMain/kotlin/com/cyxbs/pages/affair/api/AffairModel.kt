package com.cyxbs.pages.affair.api

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import kotlinx.coroutines.flow.StateFlow

/**
 * 事务数据类
 * - 添加事务使用 [AffairModel.addAffair]
 * - 删除事务使用 [AffairItemModel.delete]
 * - 修改事务使用 [AffairItemModel.edit]
 *
 * 事务包含两颗树：
 * - model 树：每个 model 结点在应用生命周期内对象不会改变，且其属性通过 StateFlow 供外界进行观察
 * - editor 树：编辑 model 树时生成，提交更新后将同步更新到 model 树上
 *
 * @author 985892345
 * @date 2025/6/22
 */

// 事务根结点，其 items 包含所有事务 AffairItemModel
interface AffairModel {
  val stuNum: String
  val items: StateFlow<List<AffairItemModel>>

  // 添加新的事务，并上传到远端
  suspend fun addAffair(
    remindTime: Int, // 提醒时间
    title: String,
    content: String,
    action: suspend (AffairItemModelEditor) -> Unit
  ): Result<AffairItemModel>
}

// 单个事务，id 为唯一值
// 其下包含多个时间段 AffairWhatTimeModel
interface AffairItemModel {
  val id: Int
  val remindTime: StateFlow<Int>
  val title: StateFlow<String>
  val content: StateFlow<String>

  val whatTimeList: StateFlow<Map<out AffairWhatTimeModel, MinuteTimePair>> // 如果使用 List<AffairWhatTimeModel> 会因为对象未改变导致无法监听更新

  // 删除事务
  suspend fun delete(): Result<Any>

  // 编辑事务
  suspend fun edit(
    action: suspend (AffairItemModelEditor) -> Unit, // action 中抛出异常则可以中断事务的编辑并阻止更新
  ): Result<Any?> // 结果返回 null 时表明事务已被移除，这通常是 whatTimeList 或者 dateList 为空导致
}

// 事务时间段，timePair 表明了时间段的开始和结束
// 其下包含多个日期 AffairDateModel
interface AffairWhatTimeModel {
  // 返回 false 时表示该 AffairDateModel 已经被删除
  val enable: StateFlow<Boolean>

  val affair: AffairItemModel

  val timePair: StateFlow<MinuteTimePair>

  val dateList: StateFlow<Map<out AffairDateModel, Date>> // 如果使用 List<AffairDateModel> 会因为对象未改变导致无法监听更新
}

// 事务日期，作为整个事务的叶子结点
// 其对象在整个应用生命周期内不会改变，可用于绑定 Compose 结点
interface AffairDateModel {
  // 返回 false 时表示该 AffairDateModel 已经被删除
  val enable: StateFlow<Boolean>

  val affair: AffairItemModel

  // whatTime 是可能会发生改变的，例如从原先的 whatTimeList 中分裂出新的 whatTime
  val whatTime: StateFlow<AffairWhatTimeModel>

  val date: StateFlow<Date>
}

//////////////////////////////////////// Editor ////////////////////////////////////////

interface AffairItemModelEditor {
  var remindTime: Int
  var title: String
  var content: String
  val whatTimeList: AffairWhatTimeModelEditorList

  interface AffairWhatTimeModelEditorList : List<AffairWhatTimeModelEditor> {
    fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditor? // 如果有相同值则返回 null
    fun remove(whatTime: AffairWhatTimeModelEditor): Boolean
  }
}

interface AffairWhatTimeModelEditor {
  var timePair: MinuteTimePair
  val dateList: AffairDateModelEditorList

  interface AffairDateModelEditorList : List<AffairDateModelEditor> {
    fun add(date: Date): AffairDateModelEditor? // 如果有相同值则返回 null
    fun add(date: AffairDateModelEditor): Boolean
    fun remove(date: AffairDateModelEditor): Boolean
  }
}

interface AffairDateModelEditor {
  var date: Date
}

