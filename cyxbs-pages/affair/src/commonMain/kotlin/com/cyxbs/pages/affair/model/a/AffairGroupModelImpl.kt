package com.cyxbs.pages.affair.model.a

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.model.SyncAffairUtils
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/14
 */
class AffairGroupModelImpl(
  override val stuNum: String,
) : AffairGroupModel {

  private val KEY_AFFAIR_ITEM_LIST = "affair_item_list"

  private val itemListLocal = AccountSettings.get(stuNum).let { settings ->
    runCatching {
      defaultJson.decodeFromString<List<String>>(settings.getString(KEY_AFFAIR_ITEM_LIST, "[]"))
    }.onFailure {
      settings.remove(KEY_AFFAIR_ITEM_LIST)
    }.getOrNull() ?: emptyList()
  }

  override val itemList = MutableStateFlow(
    itemListLocal.map {
      createAffairItemModelImpl(stuNum, it)
    }.toPersistentList()
  )

  override suspend fun addAffair(
    remindTime: Int,
    title: String,
    content: String,
    action: suspend (AffairIdModelEditor) -> Unit
  ): Result<AffairIdModel> {
    TODO()
  }

//  suspend fun syncAffair(affairs: List<AffairEntity>) {
//    if (affairList == affairs) return // 本地与远端数据完全一致
//    itemsMutex.withLock(this) {
//      val old = itemList.value
//      if (old.isEmpty()) {
//        // 本地无数据，使用远端数据
//        itemList.value = affairs.map {
//          createAffairItemModelImpl(it)
//        }.toPersistentList()
//      } else if (affairs.isEmpty()) {
//        // 远端无数据，直接清空
//        itemList.value = itemList.value.clear()
//      } else {
//        SyncAffairUtils.syncAffair(affairs, this)
//      }
//    }
//  }

  fun createAffairItemModelImpl(
    stuNum: String,
    localId: String,
  ): AffairIdModelImpl {
    return AffairIdModelImpl(
      stuNum = stuNum,
      localId = localId,
    )
  }
}