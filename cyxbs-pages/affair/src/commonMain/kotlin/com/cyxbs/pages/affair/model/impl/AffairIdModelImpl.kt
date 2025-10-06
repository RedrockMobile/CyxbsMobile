package com.cyxbs.pages.affair.model.impl

import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.model.UpdateAffair
import com.cyxbs.pages.affair.model.editor.AffairIdModelEditorImpl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/14
 */
class AffairIdModelImpl(
  val groupModel: AffairGroupModelImpl,
  var entity: AffairEntity,
) : AffairIdModel {

  override val enable: MutableStateFlow<Boolean> = MutableStateFlow(true)
  override val localId: String
    get() = entity.localId

  override val remoteId: MutableStateFlow<Int> = MutableStateFlow(entity.remoteId)
  override val remindTime: EditorStateFlowImpl<AffairIdModelEditor, Int> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(entity.remindTime),
    )
  override val title: EditorStateFlowImpl<AffairIdModelEditor, String> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(entity.title),
    )
  override val content: EditorStateFlowImpl<AffairIdModelEditor, String> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(entity.content),
    )

  override val whatTimeDate: EditorStateFlowImpl<AffairIdModelEditor, ImmutableMap<AffairWhatTimeModelImpl, ImmutableList<AffairDateModelImpl>>> =
    EditorStateFlowImpl(
      valueFlow = entity.whatTime.associateTo(LinkedHashMap()) {
        val whatTimeModel = AffairWhatTimeModelImpl(
          idModel = this,
          timePair = it.timePair,
        )
        val dateList = it.date.map { date ->
          AffairDateModelImpl(
            idModel = this,
            whatTimeModel = whatTimeModel,
            date = date
          )
        }
        whatTimeModel to dateList.toPersistentList()
      }.toPersistentMap().let { MutableStateFlow(it) }
    )

  // 用于在创建 AffairIdModelEditorImpl 时上锁
  private val affairIdModelEditorFlow = MutableStateFlow<Any?>(null)

  override fun createEditor(): AffairIdModelEditor? {
    if (affairIdModelEditorFlow.compareAndSet(null, Unit)) {
      // 确保线程安全
      return AffairIdModelEditorImpl(idModel = this) { update(this, it) }
    }
    return null
  }

  override suspend fun createEditorSuspend(): AffairIdModelEditor {
    while (true) {
      val editor = createEditor()
      if (editor != null) {
        return editor
      }
      affairIdModelEditorFlow.first { it == null } // 挂起直到下一次为 null 时
    }
  }

  // 更新数据
  private suspend fun update(
    editor: AffairIdModelEditorImpl,
    needUpload: Boolean,
  ): Result<AffairIdModelEditor.EditResult> {
    return UpdateAffair.update(
      editor = editor,
      needUpload = needUpload,
    ).onSuccess {
      affairIdModelEditorFlow.value = null
    }
  }
}