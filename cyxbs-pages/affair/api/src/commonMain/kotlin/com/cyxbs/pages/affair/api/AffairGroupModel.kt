package com.cyxbs.pages.affair.api

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 事务数据类
 * - 添加事务使用 [AffairGroupModel.createAddAffairEditor]
 * - 删除事务使用 [AffairIdModelEditor.clear]
 * - 修改事务使用 [AffairIdModelEditor]
 *
 * 事务包含两颗树：
 * - model 树：每个 model 结点在应用生命周期内对象不会改变，且其属性通过 StateFlow 供外界进行观察
 * - editor 树：编辑 model 树时生成，提交更新后将同步更新到 model 树上
 *
 * ```
 *                                                           +------------------+
 *                                                           | AffairGroupModel |
 *                                                           +------------------+
 *                                                                    | List<Id>
 *                                             +-------------------------------------------+
 *                                             |                                           |
 *                                             V                                           V
 *                                     +---------------+                           +---------------+
 *                                     | AffairIdModel | Affair Id                 | AffairIdModel |
 *                                     +---------------+                           +---------------+
 *                                             | List<WhatTime>                            |
 *                         +------------------------------------+                         ...
 *                         |                                    |
 *                         V                                    V
 *              +---------------------+              +---------------------+
 *              | AffairWhatTimeModel | WhatTime     | AffairWhatTimeModel |
 *              +---------------------+              +---------------------+
 *                         | List<Date>
 *          +---------------------------+
 *          |                           |
 *          V                           V
 * +-----------------+         +-----------------+
 * | AffairDateModel | Date    | AffairDateModel |
 * +-----------------+         +-----------------+
 * ```
 *
 * @author 985892345
 * @date 2025/6/22
 */

// 事务根结点，其 items 包含所有事务 AffairIdModel
interface AffairGroupModel {
  val stuNum: String
  val itemList: StateFlow<ImmutableList<AffairIdModel>>
  val addedAffair: Flow<AffairIdModel>
  val deletedAffair: Flow<AffairIdModel>

  /**
   * 创建添加事务的 editor，使用 [AffairIdModelEditor.commit] 后上传到远端
   */
  fun createAddAffairEditor(
    remoteId: Int = 0, // 正常情况下新增事务为 0，不为 0 仅提供给同步使用
  ): AffairIdModelEditor
}

// 单个事务，id 为唯一值
// 其下包含多个时间段 AffairWhatTimeModel
interface AffairIdModel {
  // 返回 false 时表示该 AffairIdModel 已经被删除
  val enable: StateFlow<Boolean>
  // 非后端返回 id，此 id 为客户端上的唯一 id
  val localId: String
  // 后端 id，如果 = 0，则说明是本地临时事务
  val remoteId: StateFlow<Int>
  val remindTime: EditorStateFlow<Int>
  val title: EditorStateFlow<String>
  val content: EditorStateFlow<String>

  val whatTimeDate: StateFlow<ImmutableMap<out AffairWhatTimeModel, ImmutableList<AffairDateModel>>>

  // 新增的 AffairDateModel
  val addedDateModel: Flow<AffairDateModel>

  fun tryCreateEditor(): AffairIdModelEditor?

  suspend fun createEditorSuspend(): AffairIdModelEditor
}

// 事务时间段，timePair 表明了时间段的开始和结束
// 其下包含多个日期 AffairItemModel
interface AffairWhatTimeModel {
  // 返回 false 时表示该 AffairWhatTimeModel 已经被删除
  val enable: EditorStateFlow<Boolean>

  val idModel: AffairIdModel

  val timePair: EditorStateFlow<MinuteTimePair>
}

// 事务日期，作为整个事务的叶子结点
// 其对象在整个应用生命周期内不会改变，可用于绑定 Compose 结点
interface AffairDateModel {
  // 返回 false 时表示该 AffairLeafModel 已经被删除
  val enable: EditorStateFlow<Boolean>

  val idModel: AffairIdModel

  // whatTime 是可能会发生改变的，例如从原先的 whatTimeList 中分裂出新的 whatTime
  val whatTime: EditorStateFlow<AffairWhatTimeModel>

  val date: EditorStateFlow<Date>
}

//////////////////////////////////////// Editor ////////////////////////////////////////

interface AffairIdModelEditor {
  val idModel: AffairIdModel
  val remindTime: Int
  val title: String
  val content: String
  val whatTimeDate: Map<out AffairWhatTimeModelEditor, List<AffairDateModelEditor>>

  val incrementAddList: List<AffairWhatTimeModelEditor> // 增量添加数据
  val incrementRemoveList: List<AffairWhatTimeModelEditor> // 增量移除数据

  fun setRemindTime(remindTime: Int): String?
  fun setTitle(title: String): String?
  fun setContent(content: String): String?

  fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditor? // 如果有相同值则返回 null

  fun remove(whatTime: AffairWhatTimeModelEditor): Boolean

  fun clear() // 清空所有 whatTimeList，如果 editor 提交时 whatTimeList 为空则等同于删除当前事务

  fun enableModify(): Boolean // 能否修改

  // 提交
  suspend fun commit(
    needUpload: Boolean = true, // 是否需要上传到后端，正常情况下都需要上传，不上传仅提供给同步使用
  ): Result<EditResult>

  // 取消本次编辑
  fun cancelEdit(): Boolean

  sealed interface EditResult {
    object Success : EditResult
    object Deleted : EditResult // 已被删除或者当前编辑操作被认定为删除
  }
}

interface AffairWhatTimeModelEditor {
  val idModelEditor: AffairIdModelEditor
  val whatTimeModel: AffairWhatTimeModel
  val timePair: MinuteTimePair
  val dateList: List<AffairDateModelEditor>

  val incrementAddList: List<AffairDateModelEditor> // 增量添加数据
  val incrementRemoveList: List<AffairDateModelEditor> // 增量移除数据

  fun setTimePair(timePair: MinuteTimePair): String?

  fun add(date: Date): AffairDateModelEditor? // 如果有相同值则返回 null
  fun remove(date: AffairDateModelEditor): Boolean
  fun clear()
}

interface AffairDateModelEditor {
  val idModelEditor: AffairIdModelEditor
  val whatTimeEditor: AffairWhatTimeModelEditor?
  val dateModel: AffairDateModel
  val date: Date

  fun setDate(date: Date): String?

  fun replace(whatTime: AffairWhatTimeModelEditor): Boolean
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
abstract class EditorStateFlow<Value>(
  val valueFlow: StateFlow<Value>,
  val valueByEditorFlow: Flow<Value>,
) : StateFlow<Value> by valueFlow {
  val mergeFlow: Flow<Value> = merge(valueFlow, valueByEditorFlow.map { it }).distinctUntilChanged()
}