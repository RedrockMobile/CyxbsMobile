package com.cyxbs.pages.affair.model

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastMap
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/22
 */

class AffairGroupModelImpl(
  override val stuNum: String,
  var affairList: List<AffairEntity>,
  val addAction: suspend (entity: AffairEntity) -> Result<AffairEntity>,
  val deleteAction: suspend (id: Int) -> Result<Any>,
  val updateAction: suspend (entity: AffairEntity) -> Result<Any>
) : AffairGroupModel {

  private val itemsMutex = Mutex()

  override val itemList = MutableStateFlow(affairList.fastMap {
    createAffairItemModelImpl(it)
  }.toPersistentList())

  override suspend fun addAffair(
    remindTime: Int,
    title: String,
    content: String,
    action: suspend (AffairIdModelEditor) -> Unit
  ): Result<AffairIdModelImpl> {
    return itemsMutex.withLock(this) {
      runCatchingCoroutine {
        val itemModelImpl = createAffairItemModelImpl(
          AffairEntity(
            id = 0,
            remindTime = remindTime,
            title = title,
            content = content,
            whatTime = emptyList(),
          )
        )
        val editor = itemModelImpl.createEditor()
        action.invoke(editor)
        editor.commit()
        val affair = itemModelImpl.createAffair()
        if (affair == null) {
          throw IllegalArgumentException("不存在时间段可以展示, $affair")
        } else {
          if (needUpload) {
            addAction.invoke(affair).map {
              itemModelImpl.affair = it
              itemList.value = itemList.value.add(itemModelImpl)
              itemModelImpl
            }.getOrThrow()
          } else {
            itemList.value = itemList.value.add(itemModelImpl)
            itemModelImpl
          }
        }
      }
    }
  }

  fun createAffairItemModelImpl(affair: AffairEntity): AffairIdModelImpl {
    return AffairIdModelImpl(affair, deleteAction, updateAction)
  }

  suspend fun syncAffair(affairs: List<AffairEntity>) {
    if (affairList == affairs) return // 本地与远端数据完全一致
    itemsMutex.withLock(this) {
      val old = itemList.value
      if (old.isEmpty()) {
        // 本地无数据，使用远端数据
        itemList.value = affairs.map {
          createAffairItemModelImpl(it)
        }.toPersistentList()
      } else if (affairs.isEmpty()) {
        // 远端无数据，直接清空
        itemList.value = itemList.value.clear()
      } else {
        SyncAffairUtils.syncAffair(affairs, this)
      }
    }
  }
}

class AffairIdModelImpl(
  var affair: AffairEntity,
  val deleteAction: suspend (id: Int) -> Result<Any>,
  val updateAction: suspend (entity: AffairEntity) -> Result<Any>
) : AffairIdModel {
  override val enable = MutableStateFlow(false)
  override val id: Int
    get() = affair.id
  override val remindTime = MutableStateFlow(affair.remindTime)
  override val title = MutableStateFlow(affair.title)
  override val content = MutableStateFlow(affair.content)
  override val whatTimeList = MutableStateFlow(
    affair.whatTime.groupBy(
      keySelector = { it.timePair }, // 需要先聚合 timePair，防止 server 下发数据存在重复的 timePair
      valueTransform = { it.date },
    ).map {
      AffairWhatTimeModelImpl(
        this,
        AffairWhatTime(it.key, it.value.flatten())
      )
    }.toPersistentList()
  )

  private var editor: AffairIdModelEditorImpl? = null

  override suspend fun createEditor(): AffairIdModelEditorImpl {
    return AffairIdModelEditorImpl(this)
  }


  // mutex 是不支持可重入的，重入时会变成死锁，需要添加 owner 来进行检测
  private val editMutex = Mutex() // 编辑行为锁


  // 已经触发了删除的标记
  private var hasDelete: Boolean = false

  internal suspend fun editLock(
    needUpload: Boolean, // 是否需要上传到后端，在同步远端数据到本地时是不需要上传的
    action: suspend () -> Unit,
  ): EditResult {
    if (hasDelete) return EditResult.Deleted
    return editMutex.withLock(this) { // 重入 withLock 时会抛出异常
      runCatchingCoroutine {
        action.invoke()
        val affair = createAffair()
        if (affair == null) {
          hasDelete = true
        }
        if (needUpload) {
          if (affair == null) {
            deleteAction.invoke(id)
          } else {
            updateAction.invoke(affair)
          }
        }
        if (affair == null) EditResult.Deleted else EditResult.Success
      }.getOrElse {
        EditResult.Failed(it)
      }
    }
  }

  fun createAffair(): AffairEntity? {
    return AffairEntity(
      id = id,
      remindTime = remindTime.value,
      title = title.value,
      content = content.value,
      whatTime = whatTimeList.value.mapNotNull { whatTime ->
        AffairWhatTime(
          timePair = whatTime.timePair.value,
          date = whatTime.dateList.value.map { it.date.value }
        ).let {
          if (it.date.isEmpty()) null else it
        }
      },
    ).let {
      if (it.whatTime.isEmpty()) null else it
    }
  }
}

class AffairWhatTimeModelImpl(
  affairModel: AffairIdModelImpl,
  whatTime: AffairWhatTime
) : AffairWhatTimeModel {
  override val enable = MutableStateFlow(true)
  override val idModel = affairModel
  override val timePair = MutableStateFlow(whatTime.timePair)
  override val dateList = MutableStateFlow(
    whatTime.date.sorted().fastDistinctBy { it }.fastMap {
      AffairDateModelImpl(affairModel, this, it)
    }.toPersistentList()
  )

  fun delete() {
    enable.value = false
    dateList.getAndUpdate { persistentListOf() }.forEach {
      it.delete()
    }
  }
}

class AffairDateModelImpl(
  affairModel: AffairIdModelImpl,
  whatTimeModel: AffairWhatTimeModelImpl,
  date: Date,
) : AffairDateModel {
  override val enable = MutableStateFlow(true)
  override val idModel = affairModel
  override val whatTime = MutableStateFlow(whatTimeModel)
  override val date = MutableStateFlow(date)

  fun delete() {
    enable.value = false
  }
}



