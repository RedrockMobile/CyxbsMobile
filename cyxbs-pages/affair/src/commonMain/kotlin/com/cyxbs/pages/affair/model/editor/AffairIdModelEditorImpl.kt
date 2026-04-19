package com.cyxbs.pages.affair.model.editor

import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.impl.AffairIdModelImpl
import com.cyxbs.pages.affair.model.impl.AffairWhatTimeModelImpl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairIdModelEditorImpl(
  override val idModel: AffairIdModelImpl,
  val cancelEdit: AffairIdModelEditorImpl.() -> Unit,
  val commitAction: suspend AffairIdModelEditorImpl.(needUpload: Boolean, needAdd: Boolean) -> Result<AffairIdModelEditor.EditResult>,
) : AffairIdModelEditor {
  override var remindTime = idModel.remindTime.value
  override var title = idModel.title.value
  override var content = idModel.content.value

  override val whatTimeDate: MutableMap<AffairWhatTimeModelEditorImpl, MutableList<AffairDateModelEditorImpl>> =
    idModel.whatTimeDate.value.entries.associateTo(LinkedHashMap()) {
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(
        idModelEditor = this,
        whatTimeModel = it.key,
      )
      val dateEditorList = it.value.mapTo(mutableListOf()) { dateModel ->
        AffairDateModelEditorImpl(
          idModelEditor = this,
          whatTimeEditor = whatTimeModelEditor,
          dateModel = dateModel,
        )
      }
      whatTimeModelEditor to dateEditorList
    }

  override val incrementAddList = mutableListOf<AffairWhatTimeModelEditorImpl>()
  override val incrementRemoveList = mutableListOf<AffairWhatTimeModelEditorImpl>()

  @Volatile
  private var enableModify: Boolean = true

  val whatTimeDateLock = SynchronizedObject()

  // 上锁编辑 whatTimeDate
  inline fun editorWhatTimeDate(
    action: (MutableMap<AffairWhatTimeModelEditorImpl, MutableList<AffairDateModelEditorImpl>>) -> Unit
  ) {
    synchronized(whatTimeDateLock) {
      if (!enableModify()) return
      action.invoke(whatTimeDate)
    }
  }


  override fun setRemindTime(remindTime: Int): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    if (remindTime < 0) return "remindTime 不能小于 0"
    this.remindTime = remindTime
    idModel.remindTime.valueByEditorStateFlow.tryEmit(remindTime)
    return null
  }

  override fun setTitle(title: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    this.title = title
    idModel.title.valueByEditorStateFlow.tryEmit(title)
    return null
  }

  override fun setContent(content: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    this.content = content
    idModel.content.valueByEditorStateFlow.tryEmit(content)
    return null
  }

  override fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditor? {
    if (!enableModify()) return null
    editorWhatTimeDate { whatTimeDate ->
      whatTimeDate.keys.firstOrNull { it.timePair == timePair }?.let { return null } // 已有的话则直接返回 null
      val whatTimeModel = AffairWhatTimeModelImpl(
        idModel = idModel,
        timePair = timePair,
      )
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(this, whatTimeModel)
      whatTimeDate[whatTimeModelEditor] = mutableListOf()
      incrementAddList.add(whatTimeModelEditor)
      return whatTimeModelEditor
    }
    return null
  }

  override fun remove(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (!enableModify()) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    if (whatTime.idModelEditor !== this) return false
    editorWhatTimeDate { whatTimeDate ->
      if (whatTimeDate.contains(whatTime)) {
        whatTime.clear()
        whatTime.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(false)
        whatTimeDate.remove(whatTime)
        if (!incrementAddList.remove(whatTime)) {
          incrementRemoveList.add(whatTime)
        }
        return true
      }
    }
    return false
  }

  override fun clear() {
    if (!enableModify()) return
    editorWhatTimeDate { whatTimeDate ->
      whatTimeDate.keys.forEach {
        it.clear()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(false)
        incrementRemoveList.add(it)
      }
      incrementRemoveList.removeAll(incrementAddList)
      incrementAddList.clear()
      whatTimeDate.clear()
    }
  }

  fun reset() {
    if (!enableModify) return
    setRemindTime(idModel.remindTime.value)
    setTitle(idModel.title.value)
    setContent(idModel.content.value)
    editorWhatTimeDate { whatTimeDate ->
      incrementAddList.forEach {
        it.reset()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(false)
        whatTimeDate.remove(it)
      }
      incrementAddList.clear()
      incrementRemoveList.forEach {
        whatTimeDate[it] = mutableListOf()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(true)
      }
      incrementRemoveList.clear()
      whatTimeDate.keys.forEach {
        it.reset()
      }
    }
  }

  override fun findDateModelEditor(dateModel: AffairDateModel): AffairDateModelEditor? {
    return whatTimeDate.firstNotNullOfOrNull { entry ->
      if (entry.key.whatTimeModel == dateModel.whatTime.value)
        entry.value.find { it.dateModel == dateModel }
      else null
    }
  }

  override fun enableModify(): Boolean {
    return enableModify
  }

  private val commitMutex = Mutex()

  override suspend fun commit(
    needUpload: Boolean,
    needAdd: Boolean
  ): Result<AffairIdModelEditor.EditResult> {
    if (!enableModify()) return Result.failure(IllegalStateException("提交修改后不可再修改"))
    return commitMutex.withLock {
      commitAction.invoke(this, needUpload, needAdd).onSuccess {
        enableModify = false // 提交成功后不可再修改
      }
    }
  }

  override fun cancelEdit(): Boolean {
    if (!enableModify()) return false
    if (commitMutex.isLocked) return false
    cancelEdit.invoke(this)
    enableModify = false // 提交成功后不可再修改
    return true
  }
}