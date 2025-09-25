package com.cyxbs.pages.affair.model.b

import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.a.AffairIdModelImpl
import com.cyxbs.pages.affair.model.a.AffairWhatTimeModelImpl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairIdModelEditorImpl(
  override val idModel: AffairIdModelImpl,
  val commitAction: suspend AffairIdModelEditorImpl.(needUpload: Boolean) -> Result<AffairIdModelEditor.EditResult>,
) : AffairIdModelEditor {
  override val remindTime = MutableStateFlow(idModel.remindTime.value)
  override val title = MutableStateFlow(idModel.title.value)
  override val content = MutableStateFlow(idModel.content.value)
  override val whatTimeList = MutableStateFlow(
    idModel.whatTimeList.value.map {
      AffairWhatTimeModelEditorImpl(
        idModelEditor = this,
        whatTimeModel = it,
      )
    }.toPersistentList()
  )

  private var enableModify: Boolean = true

  private val whatTimeListLock = SynchronizedObject()

  private val addList = mutableListOf<AffairWhatTimeModelEditorImpl>()
  private val removeList = mutableListOf<AffairWhatTimeModelEditorImpl>()

  override fun setRemindTime(remindTime: Int): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    if (remindTime < 0) return "remindTime 不能小于 0"
    this.remindTime.value = remindTime
    return null
  }

  override fun setTitle(title: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    if (title.isBlank()) return "title 不能为空"
    this.title.value = title
    return null
  }

  override fun setContent(content: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    this.content.value = content
    return null
  }

  override fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditor? {
    if (!enableModify()) return null
    synchronized(whatTimeListLock) {
      whatTimeList.value.firstOrNull { it.timePair.value == timePair }
        ?.let { return null } // 已有的话则直接返回 null
      val whatTimeModel = AffairWhatTimeModelImpl(
        idModel = idModel,
        timePair = timePair,
      )
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(this, whatTimeModel)
      whatTimeList.value = whatTimeList.value.remove(whatTimeModelEditor)
      addList.add(whatTimeModelEditor)
      return whatTimeModelEditor
    }
  }

  override fun remove(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (!enableModify()) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    if (whatTime.idModelEditor !== this) return false
    synchronized(whatTimeListLock) {
      if (whatTimeList.value.contains(whatTime)) {
        whatTimeList.value = whatTimeList.value.remove(whatTime)
        if (!addList.remove(whatTime)) {
          removeList.add(whatTime)
        }
        return true
      }
      return false
    }
  }

  override fun clear() {
    if (!enableModify()) return
    synchronized(whatTimeListLock) {
      removeList.addAll(whatTimeList.value)
      removeList.removeAll(addList)
      addList.clear()
      whatTimeList.value = whatTimeList.value.clear()
    }
  }

  override fun enableModify(): Boolean {
    return enableModify
  }

  override suspend fun commit(needUpload: Boolean): Result<AffairIdModelEditor.EditResult> {
    if (!enableModify()) return Result.failure(IllegalStateException("提交修改后不可再修改"))
    return commitAction.invoke(this, needUpload).onSuccess {
      enableModify = false // 提交成功后不可再修改
    }
  }
}