package com.cyxbs.pages.affair.model.impl

import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.bean.AffairWhenBean
import com.cyxbs.pages.affair.net.AddAffairRequest
import com.cyxbs.pages.affair.repos.AffairRepository2
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/14
 */
class AffairGroupModelImpl(
  override val stuNum: String,
  entityList: List<AffairEntity>,
) : AffairGroupModel {

  override val itemList = MutableStateFlow(
    entityList.map {
      createAffairItemModelImpl(it)
    }.toPersistentList()
  )

  @OptIn(ExperimentalUuidApi::class)
  override suspend fun addAffair(
    remindTime: Int,
    title: String,
    content: String,
    remoteId: Int,
    action: suspend (AffairIdModelEditor) -> Unit
  ): Result<AffairIdModel> {
    if (remoteId > 0 && itemList.value.any { it.remoteId.value == remoteId }) {
      return Result.failure(IllegalStateException("remoteId 已存在, $remoteId"))
    }
    val entity = AffairEntity(
      remoteId = remoteId,
      localId = Uuid.random().toString(),
      title = title,
      content = content,
      remindTime = remindTime,
      whatTime = emptyList(),
    )
    val model = createAffairItemModelImpl(entity)
    val editor = model.createEditorSuspend()
    action(editor)
    editor.commit(needUpload = false)
    if (model.whatTimeDate.value.isEmpty()) {
      // 添加的数据无效
      return Result.failure(IllegalStateException("whatTimeDate 为空，数据无效, $entity"))
    }
    if (remoteId <= 0) {
      return AffairRepository2.addAffair(
        stuNum = stuNum,
        localId = entity.localId,
        request = AddAffairRequest(
          remindTime = entity.remindTime,
          title = entity.title,
          content = entity.content,
          whenList = model.whatTimeDate.value.map { entry ->
            AffairWhenBean(
              entry.key.timePair.value.first.minuteOfDay,
              entry.key.timePair.value.second.minuteOfDay,
              entry.value.map { it.date.value })
          },
        ),
        allowLocal = true,
        needShowException = true,
      ).onSuccess {
        model.remoteId.value = it.remoteId
        model.entity = it
      }.onSuccess {
        itemList.update {
          it.add(model)
        }
      }.map {
        model
      }
    } else {
      itemList.update {
        it.add(model)
      }
      return Result.success(model)
    }
  }

  fun createAffairItemModelImpl(
    entity: AffairEntity,
  ): AffairIdModelImpl {
    return AffairIdModelImpl(
      groupModel = this,
      entity = entity,
    )
  }
}