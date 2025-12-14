package com.cyxbs.pages.affair.model.impl

import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.model.EditAffairUtils
import com.cyxbs.pages.affair.model.editor.AffairIdModelEditorImpl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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

  override val whatTimeDate: MutableStateFlow<ImmutableMap<AffairWhatTimeModelImpl, ImmutableList<AffairDateModelImpl>>> =
    MutableStateFlow(
      entity.whatTime.associateTo(LinkedHashMap()) {
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
      }.toPersistentMap()
    )

  override val addedDateModel = MutableSharedFlow<AffairDateModel>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  // 用于在创建 AffairIdModelEditorImpl 时上锁
  private val affairIdModelEditorFlow = MutableStateFlow<Any?>(null)

  override fun createEditor(): AffairIdModelEditor? {
    if (affairIdModelEditorFlow.compareAndSet(null, Unit)) {
      // 确保线程安全
      return AffairIdModelEditorImpl(idModel = this) {
        EditAffairUtils.commit(
          editor = this,
          needUpload = it,
        ).onSuccess {
          affairIdModelEditorFlow.value = null
        }
      }
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

  override fun toString(): String {
    return "AffairIdModelImpl(enable=${enable.value}" +
        ", localId=$localId" +
        ", remoteId=${remoteId.value}" +
        ", remindTime=${remindTime.value}" +
        ", title=${title.value}" +
        ", content=${content.value}" +
        ", whatTimeDate=${whatTimeDate.value}" +
        ")"
  }
}