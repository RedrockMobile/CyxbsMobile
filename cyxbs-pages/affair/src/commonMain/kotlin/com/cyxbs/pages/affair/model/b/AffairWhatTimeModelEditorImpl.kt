package com.cyxbs.pages.affair.model.b

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.a.AffairDateModelImpl
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
class AffairWhatTimeModelEditorImpl(
  override val idModelEditor: AffairIdModelEditor,
  val whatTimeModel: AffairWhatTimeModelImpl,
) : AffairWhatTimeModelEditor {
  override val timePair = MutableStateFlow(whatTimeModel.timePair.value)
  override val dateList = MutableStateFlow(
    whatTimeModel.dateList.value.map {
      AffairDateModelEditorImpl(
        idModelEditor = idModelEditor,
        whatTime = MutableStateFlow(this),
        dateModel = it,
      )
    }.toPersistentList()
  )

  private val dateListLock = SynchronizedObject()

  private val addList = mutableListOf<AffairDateModelEditorImpl>()
  private val removeList = mutableListOf<AffairDateModelEditorImpl>()

  override fun setTimePair(timePair: MinuteTimePair): String? {
    if (!idModelEditor.enableModify()) return "提交修改后不可再修改"
    if (timePair.second < timePair.first) return "结束时间不能小于开始时间"
    if (idModelEditor.whatTimeList.value.any { it.timePair.value == timePair }) {
      return "已有相同时间段"
    }
    this.timePair.value = timePair
    return null
  }

  override fun add(date: Date): AffairDateModelEditor? {
    if (!idModelEditor.enableModify()) return null
    synchronized(dateListLock) {
      dateList.value.firstOrNull { it.date.value == date }
        ?.let { return null } // 已有的话则直接返回 null
      val dateModel = AffairDateModelImpl(
        idModel = idModelEditor.idModel,
        whatTimeModelImpl = whatTimeModel,
        date = date,
      )
      val dateModelEditor = AffairDateModelEditorImpl(
        idModelEditor = idModelEditor,
        whatTime = MutableStateFlow(this),
        dateModel = dateModel,
      )
      dateList.value = dateList.value.add(dateModelEditor)
      addList.add(dateModelEditor)
      return dateModelEditor
    }
  }

  override fun remove(date: AffairDateModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    synchronized(dateListLock) {
      if (date.whatTime.value !== this) return false
      if (dateList.value.contains(date)) {
        dateList.value = dateList.value.remove(date)
        date.whatTime.value = null
        if (!addList.remove(date)) {
          removeList.add(date)
        }
        return true
      }
      return false
    }
  }

  override fun clear() {
    if (!idModelEditor.enableModify()) return
    synchronized(dateListLock) {
      removeList.addAll(dateList.value)
      removeList.removeAll(addList)
      addList.clear()
      dateList.value = dateList.value.clear()
    }
  }

  internal fun replace(date: AffairDateModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    val oldWhatTimeModelEditor = date.whatTime.value
    if (oldWhatTimeModelEditor != null && oldWhatTimeModelEditor.idModelEditor !== idModelEditor) return false
    if (oldWhatTimeModelEditor === this) return true
    synchronized(oldWhatTimeModelEditor?.dateListLock ?: dateListLock) {
      synchronized(dateListLock) {
        if (dateList.value.any { it.date.value == date.date.value }) return false // 有相同日期则添加失败
        if (oldWhatTimeModelEditor != null) {
          if (oldWhatTimeModelEditor.dateList.value.contains(date)) {
            oldWhatTimeModelEditor.dateList.value = oldWhatTimeModelEditor.dateList.value.remove(date)
            if (!oldWhatTimeModelEditor.addList.remove(date)) {
              oldWhatTimeModelEditor.removeList.add(date)
            }
          }
        }
        dateList.value = dateList.value.add(date)
        date.whatTime.value = this
        addList.add(date)
        removeList.remove(date)
        return true
      }
    }
  }
}