package com.cyxbs.pages.affair.model

import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.bean.AffairWhenBean
import com.cyxbs.pages.affair.model.editor.AffairIdModelEditorImpl
import com.cyxbs.pages.affair.net.AddAffairRequest
import com.cyxbs.pages.affair.repos.AffairRepository2
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

/**
 * .
 *
 * @author 985892345
 * @date 2025/8/3
 */
object EditAffairUtils {

  suspend fun commit(
    editor: AffairIdModelEditorImpl,
    needUpload: Boolean, // 是否上传到后端
    needAdd: Boolean, // 是否添加进事务列表中
  ): Result<AffairIdModelEditor.EditResult> {
    val idModel = editor.idModel
    val newEntity = createAffair(editor)
    // 进行网络请求
    if (needUpload) {
      if (newEntity.remoteId == 0) {
        // 新增事务
        if (newEntity.whatTime.isEmpty()) {
          // whatTime 为空，不存在时间段，无法构成事务
          return Result.failure(IllegalStateException("不存在时间段，无法构成事务"))
        }
        return AffairRepository2.addAffair(
          stuNum = idModel.groupModel.stuNum,
          localId = newEntity.localId,
          request = AddAffairRequest(
            remindTime = newEntity.remindTime,
            title = newEntity.title,
            content = newEntity.content,
            whenList = newEntity.whatTime.map {
              AffairWhenBean(
                start = it.timePair.first.minuteOfDay,
                end = it.timePair.second.minuteOfDay,
                date = it.date
              )
            },
          ),
          allowLocal = true,
          needShowException = false,
        ).onSuccess {
          idModel.entity = newEntity
          updateModel(editor, needAdd)
        }.map {
          AffairIdModelEditor.EditResult.Success
        }
      } else if (editor.whatTimeDate.isEmpty() || editor.whatTimeDate.values.all { it.isEmpty() }) {
        // 判断 whatTime 是否为空，如果为空则说明事务本身被移除了
        return AffairRepository2.deleteAffair(
          stuNum = idModel.groupModel.stuNum,
          localId = newEntity.localId,
          allowLocal = true,
          needShowException = false,
        ).onSuccess {
          updateModel(editor, needAdd)
        }.onFailure {
          editor.reset()
        }.map {
          AffairIdModelEditor.EditResult.Deleted
        }
      } else if (newEntity.remoteId != 0) {
        // 事务被修改
        return AffairRepository2.updateAffair(
          stuNum = idModel.groupModel.stuNum,
          affair = newEntity,
          allowLocal = true,
          needShowException = false,
        ).onSuccess {
          idModel.entity = newEntity
          updateModel(editor, needAdd)
        }.onFailure {
          editor.reset()
        }.map {
          AffairIdModelEditor.EditResult.Success
        }
      } else {
        return Result.failure(IllegalStateException("未知 commit 状态: " +
            "\neditor = $editor\nnewEntity = $newEntity"))
      }
    } else {
      updateModel(editor, needAdd)
      return Result.success(
        if (editor.whatTimeDate.isEmpty() || editor.whatTimeDate.values.all { it.isEmpty() }) {
          AffairIdModelEditor.EditResult.Deleted
        } else AffairIdModelEditor.EditResult.Success
      )
    }
  }


  private fun updateModel(editor: AffairIdModelEditorImpl, needAdd: Boolean) {
    val idModel = editor.idModel
    idModel.remindTime.valueStateFlow.value = editor.remindTime
    idModel.title.valueStateFlow.value = editor.title
    idModel.content.valueStateFlow.value = editor.content
    // 编辑数据转换为最终数据
    idModel.whatTimeDate.value = editor.whatTimeDate.entries.associateByTo(
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
    if (editor.whatTimeDate.isEmpty() || editor.whatTimeDate.values.all { it.isEmpty() }) {
      // 事务被移除
      idModel.enable.value = false
      idModel.groupModel.removeAffairInternal(idModel)
    } else if (needAdd) {
      if (idModel.remoteId.value == 0) {
        // 新增的本地临时事务
        idModel.groupModel.addAffairInternal(idModel)
      } else if (idModel.groupModel.itemList.value.all { it.remoteId.value != idModel.remoteId.value }) {
        // 新增的事务
        idModel.groupModel.addAffairInternal(idModel)
      }
    }
  }

  private fun createAffair(editor: AffairIdModelEditor): AffairEntity {
    return AffairEntity(
      remoteId = editor.idModel.remoteId.value,
      localId = editor.idModel.localId,
      remindTime = editor.remindTime,
      title = editor.title,
      content = editor.content,
      whatTime = editor.whatTimeDate.entries.mapNotNull { entry ->
        if (entry.value.isEmpty()) null else AffairWhatTime(
          timePair = entry.key.timePair,
          date = entry.value.map { it.date },
        )
      }
    )
  }
}