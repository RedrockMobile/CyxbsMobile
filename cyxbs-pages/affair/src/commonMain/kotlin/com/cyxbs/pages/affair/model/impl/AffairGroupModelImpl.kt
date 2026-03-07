package com.cyxbs.pages.affair.model.impl

import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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


  private val _itemList = MutableStateFlow(
    entityList.map {
      createAffairItemModelImpl(it)
    }.toPersistentList()
  )
  override val itemList: StateFlow<PersistentList<AffairIdModelImpl>> = _itemList

  override val addedAffair = MutableSharedFlow<AffairIdModel>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  override val deletedAffair = MutableSharedFlow<AffairIdModel>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  @OptIn(ExperimentalUuidApi::class)
  override fun createAddAffairEditor(
    remoteId: Int,
  ): AffairIdModelEditor {
    if (remoteId > 0 && itemList.value.any { it.remoteId.value == remoteId }) {
      throw IllegalStateException("remoteId 已存在, $remoteId")
    }
    val entity = AffairEntity(
      remoteId = remoteId,
      localId = Uuid.random().toString(),
      title = "标题",
      content = "内容",
      remindTime = 0,
      whatTime = emptyList(),
    )
    val model = createAffairItemModelImpl(entity)
    return model.tryCreateEditor()!! // 添加进 _itemList 由内部 commit() 时进行处理
  }

  fun addAffairInternal(idModel: AffairIdModelImpl) {
    _itemList.value = itemList.value.add(idModel)
    addedAffair.tryEmit(idModel)
  }

  fun removeAffairInternal(idModel: AffairIdModelImpl) {
    _itemList.value = itemList.value.remove(idModel)
    deletedAffair.tryEmit(idModel)
  }

  private fun createAffairItemModelImpl(
    entity: AffairEntity,
  ): AffairIdModelImpl {
    return AffairIdModelImpl(
      groupModel = this,
      entity = entity,
    )
  }

  override fun toString(): String {
    return "AffairGroupModelImpl(stuNum=$stuNum" +
        ", itemList=${itemList.value}" +
        ")"
  }
}