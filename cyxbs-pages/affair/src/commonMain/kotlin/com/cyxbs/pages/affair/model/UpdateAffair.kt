package com.cyxbs.pages.affair.model

import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.model.editor.AffairIdModelEditorImpl
import com.cyxbs.pages.affair.repos.AffairRepository2
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

/**
 * .
 *
 * @author 985892345
 * @date 2025/8/3
 */
object UpdateAffair {

  suspend fun update(
    editor: AffairIdModelEditorImpl,
    needUpload: Boolean,
  ): Result<AffairIdModelEditor.EditResult> {
    val idModel = editor.idModel
    // 进行网络请求
    if (needUpload) {
      // 判断自身的 whatTimeDate 是否为空，如果为空则说明事务本身被移除了
      if (editor.whatTimeDate.isEmpty()) {
        // 事务被移除
        return AffairRepository2.deleteAffair(
          stuNum = idModel.groupModel.stuNum,
          localId = idModel.localId,
          allowLocal = true,
          needShowException = true,
        ).onSuccess {
          updateModel(editor)
        }.onFailure {
          editor.reset()
        }.map {
          AffairIdModelEditor.EditResult.Deleted
        }
      } else {
        // 事务被修改
        val newEntity = createAffair(editor)
        return AffairRepository2.updateAffair(
          stuNum = idModel.groupModel.stuNum,
          affair = newEntity,
          allowLocal = true,
          needShowException = true,
        ).onSuccess {
          idModel.entity = newEntity
          updateModel(editor)
        }.onFailure {
          editor.reset()
        }.map {
          AffairIdModelEditor.EditResult.Success
        }
      }
    } else {
      updateModel(editor)
      return Result.success(
        if (editor.whatTimeDate.isEmpty()) AffairIdModelEditor.EditResult.Deleted
        else AffairIdModelEditor.EditResult.Success
      )
    }
  }

  private fun updateModel(editor: AffairIdModelEditorImpl) {
    val idModel = editor.idModel
    idModel.remindTime.valueStateFlow.value = editor.remindTime
    idModel.title.valueStateFlow.value = editor.title
    idModel.content.valueStateFlow.value = editor.content
    // 编辑数据转换为最终数据
    idModel.whatTimeDate.valueStateFlow.value = editor.whatTimeDate.entries.associateByTo(
      destination = LinkedHashMap(),
      keySelector = {
        val whatTimeModel = it.key.whatTimeModel
        whatTimeModel.update(it.key)
        whatTimeModel
      },
      valueTransform = { entry ->
        entry.value.map {
          val dateModel = it.dateModel
          dateModel.update(it)
          dateModel
        }.toPersistentList()
      }
    ).toPersistentMap()
    // 通知被移除的节点
    editor.incrementRemoveList.forEach {
      it.whatTimeModel.update(it)
      it.whatTimeModel.enable.valueStateFlow.value = false
    }
    // 事务被移除
    if (editor.whatTimeDate.isEmpty()) {
      idModel.enable.value = false
      idModel.groupModel.itemList.value = idModel.groupModel.itemList.value.remove(idModel)
    }
  }

  private fun createAffair(editor: AffairIdModelEditor): AffairEntity {
    return AffairEntity(
      remoteId = editor.idModel.remoteId.value,
      localId = editor.idModel.localId,
      remindTime = editor.remindTime,
      title = editor.title,
      content = editor.content,
      whatTime = editor.whatTimeDate.entries.map { entry ->
        AffairWhatTime(
          timePair = entry.key.timePair,
          date = entry.value.map { it.date },
        )
      }
    )
  }
}