package com.cyxbs.pages.affair.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.bean.AffairWhatTime
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/8/3
 */
class AffairIdModelEditorImpl(
  val affairModel: AffairIdModelImpl,
) : AffairIdModelEditor {

  // commit 已提交标记
  internal var hasCommit = false

  override val remindTime = MutableStateFlow(affairModel.remindTime.value)
  override val title = MutableStateFlow(affairModel.title.value)
  override val content = MutableStateFlow(affairModel.content.value)

  override val whatTimeList = MutableStateFlow(affairModel.whatTimeList.value.map {
    AffairWhatTimeModelEditorImpl(this, it)
  }.toPersistentList())

  override fun setRemindTime(remindTime: Int): String? {
    if (hasCommit) return "提交修改后不可再修改"
    if (remindTime < 0) return "remindTime 不能小于 0"
    this.remindTime.value = remindTime
    return null
  }

  override fun setTitle(title: String): String? {
    if (hasCommit) return "提交修改后不可再修改"
    if (title.isBlank()) return "title 不能为空"
    this.title.value = title
    return null
  }

  override fun setContent(content: String): String? {
    if (hasCommit) return "提交修改后不可再修改"
    this.content.value = content
    return null
  }


  private val synchronizedObject = SynchronizedObject()

  private val addList = mutableListOf<AffairWhatTimeModelEditorImpl>()
  private val removeList = mutableListOf<AffairWhatTimeModelEditorImpl>()

  override fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditorImpl? {
    if (hasCommit) return null
    synchronized(synchronizedObject) {
      whatTimeList.value.firstOrNull { it.timePair.value == timePair }
        ?.let { return null } // 已有的话则直接返回 null
      val whatTimeModel =
        AffairWhatTimeModelImpl(affairModel, AffairWhatTime(timePair, emptyList()))
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(this, whatTimeModel)
      whatTimeList.value = whatTimeList.value.remove(whatTimeModelEditor)
      addList.add(whatTimeModelEditor)
      return whatTimeModelEditor
    }
  }

  override fun remove(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (hasCommit) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    if (whatTime.idModelEditor !== this) return false
    synchronized(synchronizedObject) {
      if (whatTimeList.value.contains(whatTime)) {
        whatTimeList.value = whatTimeList.value.remove(whatTime)
        whatTime.hasRemoved = true
        if (!addList.remove(whatTime)) {
          removeList.add(whatTime)
        }
        return true
      }
      return false
    }
  }

  override fun clear() {
    if (hasCommit) return
    synchronized(synchronizedObject) {
      whatTimeList.value.forEach { it.hasRemoved = true }
      removeList.addAll(whatTimeList.value)
      removeList.removeAll(addList)
      addList.clear()
      whatTimeList.value = whatTimeList.value.clear()
    }
  }

  override suspend fun commit(): Result<AffairIdModelEditor.EditResult> {
    if (hasCommit) return Result.failure(IllegalStateException("提交修改后不可再修改"))

  }

  private fun update() {
    val newList = whatTimeList.value.mapNotNull { it.update() }
    if (newList.isEmpty()) {
      // 被移除了
      affairModel.enable.value = false
    } else {
      affairModel.remindTime.value = remindTime.value
      affairModel.title.value = title.value
      affairModel.content.value = content.value
      affairModel.whatTimeList.value = newList.toPersistentList()
    }
    removeList.forEach { it.update() }
    removeList.clear()
    addList.clear()
  }
}

class AffairWhatTimeModelEditorImpl(
  override val idModelEditor: AffairIdModelEditorImpl,
  val whatTimeModel: AffairWhatTimeModelImpl,
) : AffairWhatTimeModelEditor {
  override val timePair = MutableStateFlow(whatTimeModel.timePair.value)

  override val dateList = MutableStateFlow(whatTimeModel.dateList.value.map {
    AffairDateModelEditorImpl(idModelEditor, MutableStateFlow(this), it)
  }.toPersistentList())

  override fun setTimePair(timePair: MinuteTimePair): String? {
    if (idModelEditor.hasCommit) return "提交修改后不可再修改"
    if (timePair.second < timePair.first) return "结束时间不能小于开始时间"
    if (idModelEditor.whatTimeList.value.any { it.timePair.value == timePair }) {
      return "已有相同时间段"
    }
    this.timePair.value = timePair
    return null
  }

  // 是否被移除，如果被移除一次，就不会再被添加
  var hasRemoved: Boolean = false

  private val synchronizedObject = SynchronizedObject()

  private val addList = mutableListOf<AffairDateModelEditorImpl>()
  private val removeList = mutableListOf<AffairDateModelEditorImpl>()

  override fun add(date: Date): AffairDateModelEditorImpl? {
    if (idModelEditor.hasCommit) return null
    synchronized(synchronizedObject) {
      dateList.value.firstOrNull { it.date.value == date }
        ?.let { return null } // 已有的话则直接返回 null
      val dateModel = AffairDateModelImpl(idModelEditor.affairModel, whatTimeModel, date)
      val editor = AffairDateModelEditorImpl(idModelEditor, MutableStateFlow(this), dateModel)
      dateList.value = dateList.value.add(editor)
      addList.add(editor)
      return editor
    }
  }

  internal fun replace(date: AffairDateModelEditor): Boolean {
    if (idModelEditor.hasCommit) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    synchronized(synchronizedObject) {
      if (date.whatTime.value === this) return true
      if (dateList.value.any { it.date.value == date.date.value }) return false // 有相同日期则添加失败
      date.whatTime.value?.remove(date) // 先从之前的 whatTimeModelEditor 中删除
      dateList.value = dateList.value.add(date)
      date.whatTime.value = this
      addList.add(date)
      removeList.remove(date)
      return true
    }
  }

  override fun remove(date: AffairDateModelEditor): Boolean {
    if (idModelEditor.hasCommit) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    synchronized(synchronizedObject) {
      if (date.whatTime.value !== this) return false
      if (dateList.value.contains(date)) {
        dateList.value = dateList.value.remove(date)
        date.whatTime.value = null
        removeList.add(date)
        addList.remove(date)
        return true
      }
      return false
    }
  }

  override fun clear() {
    if (idModelEditor.hasCommit) return
    synchronized(synchronizedObject) {
      removeList.addAll(dateList.value)
      addList.clear()
      dateList.value = dateList.value.clear()
    }
  }

  internal fun update(): AffairWhatTimeModelImpl? {
    val newList = dateList.value.mapNotNull { it.update() }
    if (newList.isEmpty()) {
      hasRemoved = true
    }
    if (hasRemoved) {
      whatTimeModel.enable.value = false
      return null
    } else {
      whatTimeModel.timePair.value = timePair.value
      whatTimeModel.dateList.value = newList.toPersistentList()
    }
    removeList.forEach { it.update() }
    removeList.clear()
    addList.clear()
    return whatTimeModel
  }
}

class AffairDateModelEditorImpl(
  override val idModelEditor: AffairIdModelEditorImpl,
  override val whatTime: MutableStateFlow<AffairWhatTimeModelEditorImpl?>,
  val dateModel: AffairDateModelImpl,
) : AffairDateModelEditor {

  override val date = MutableStateFlow(dateModel.date.value)

  override fun setDate(date: Date): String? {
    if (idModelEditor.hasCommit) return "提交修改后不可再修改"
    val whatTime = whatTime.value
    if (whatTime != null && whatTime.dateList.value.any { it.date.value == date }) {
      return "该日期已存在"
    }
    this.date.value = date
    return null
  }

  override fun replace(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (idModelEditor.hasCommit) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    return whatTime.replace(this)
  }

  internal fun update(): AffairDateModelImpl? {
    val whatTime = whatTime.value
    if (whatTime == null || whatTime.hasRemoved) {
      // 已经被删除了
      dateModel.enable.value = false
      return null
    } else {
      dateModel.whatTime.value = whatTime.whatTimeModel
      dateModel.date.value = date.value
    }
    return dateModel
  }
}