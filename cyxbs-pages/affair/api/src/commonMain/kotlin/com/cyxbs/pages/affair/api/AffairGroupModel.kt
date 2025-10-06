package com.cyxbs.pages.affair.api

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 事务数据类
 * - 添加事务使用 [AffairGroupModel.addAffair]
 * - 删除事务使用 [AffairIdModel.delete]
 * - 修改事务使用 [AffairIdModel.edit]
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

  /**
   * 添加新的事务，并上传到远端
   *
   * 删除请调用 [AffairIdModelEditor.clear]
   */
  suspend fun addAffair(
    remindTime: Int, // 提醒时间
    title: String,
    content: String,
    remoteId: Int = 0, // 如果大于等于 0，则认为是后端新下发的数据，就仅本地新增，不上传到后端
    action: suspend (AffairIdModelEditor) -> Unit
  ): Result<AffairIdModel>
}

// 单个事务，id 为唯一值
// 其下包含多个时间段 AffairWhatTimeModel
interface AffairIdModel {
  // 返回 false 时表示该 AffairIdModel 已经被删除
  val enable: StateFlow<Boolean>
  // 非后端返回 id，此 id 为客户端上的唯一 id
  val localId: String
  // 后端 id，如果 <= 0，则说明是本地临时事务
  val remoteId: MutableStateFlow<Int>
  val remindTime: EditorStateFlow<AffairIdModelEditor, Int>
  val title: EditorStateFlow<AffairIdModelEditor, String>
  val content: EditorStateFlow<AffairIdModelEditor, String>

  val whatTimeDate: EditorStateFlow<AffairIdModelEditor, out ImmutableMap<out AffairWhatTimeModel, ImmutableList<AffairDateModel>>>

  fun createEditor(): AffairIdModelEditor?

  suspend fun createEditorSuspend(): AffairIdModelEditor
}

// 事务时间段，timePair 表明了时间段的开始和结束
// 其下包含多个日期 AffairItemModel
interface AffairWhatTimeModel {
  // 返回 false 时表示该 AffairWhatTimeModel 已经被删除
  val enable: EditorStateFlow<AffairWhatTimeModelEditor, Boolean>

  val idModel: AffairIdModel

  val timePair: EditorStateFlow<AffairWhatTimeModelEditor, MinuteTimePair>
}

// 事务日期，作为整个事务的叶子结点
// 其对象在整个应用生命周期内不会改变，可用于绑定 Compose 结点
interface AffairDateModel {
  // 返回 false 时表示该 AffairLeafModel 已经被删除
  val enable: EditorStateFlow<AffairDateModelEditor, Boolean>

  val idModel: AffairIdModel

  // whatTime 是可能会发生改变的，例如从原先的 whatTimeList 中分裂出新的 whatTime
  val whatTime: EditorStateFlow<AffairDateModelEditor, AffairWhatTimeModel>

  val date: EditorStateFlow<AffairDateModelEditor, Date>
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

  suspend fun commit(
    needUpload: Boolean = true, // 是否需要上传到后端
  ): Result<EditResult>

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
abstract class EditorStateFlow<Editor, Value>(
  val valueFlow: StateFlow<Value>,
  val valueByEditorFlow: Flow<Pair<Editor, Value>>
) : StateFlow<Value> by valueFlow