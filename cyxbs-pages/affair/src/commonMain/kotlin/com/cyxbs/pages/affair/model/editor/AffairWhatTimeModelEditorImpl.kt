package com.cyxbs.pages.affair.model.editor

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.impl.AffairDateModelImpl
import com.cyxbs.pages.affair.model.impl.AffairWhatTimeModelImpl
import kotlin.collections.get

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairWhatTimeModelEditorImpl(
  override val idModelEditor: AffairIdModelEditorImpl,
  override val whatTimeModel: AffairWhatTimeModelImpl,
) : AffairWhatTimeModelEditor {
  override var timePair = whatTimeModel.timePair.value
  override val dateList: List<AffairDateModelEditorImpl>
    get() = idModelEditor.whatTimeDate[this] ?: emptyList()

  override val incrementAddList = mutableListOf<AffairDateModelEditorImpl>()
  override val incrementRemoveList = mutableListOf<AffairDateModelEditorImpl>()

  override fun setTimePair(timePair: MinuteTimePair): String? {
    if (!idModelEditor.enableModify()) return "提交修改后不可再修改"
    if (timePair.second < timePair.first) return "结束时间不能小于开始时间"
    if (timePair == this.timePair) return null
    if (idModelEditor.whatTimeDate.keys.any { it.timePair == timePair }) {
      return "已有相同时间段"
    }
    this.timePair = timePair
    whatTimeModel.timePair.valueByEditorStateFlow.tryEmit(this to timePair)
    return null
  }

  override fun add(date: Date): AffairDateModelEditor? {
    if (!idModelEditor.enableModify()) return null
    idModelEditor.editorWhatTimeDate { whatTimeDate ->
      val dateList = whatTimeDate[this] ?: return null
      dateList.firstOrNull { it.date == date }?.let { return null } // 已有的话则直接返回 null
      val dateModel = AffairDateModelImpl(
        idModel = idModelEditor.idModel,
        whatTimeModel = whatTimeModel,
        date = date,
      )
      val dateModelEditor = AffairDateModelEditorImpl(
        idModelEditor = idModelEditor,
        whatTimeEditor = this,
        dateModel = dateModel,
      )
      dateList.add(dateModelEditor)
      incrementAddList.add(dateModelEditor)
      idModelEditor.sendValueByEditorStateFlow()
      return dateModelEditor
    }
    return null
  }

  override fun remove(date: AffairDateModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    if (date.whatTimeEditor !== this) return false
    idModelEditor.editorWhatTimeDate { whatTimeDate ->
      val dateList = whatTimeDate[this] ?: return false
      if (!dateList.remove(date)) return false
      date.dateModel.enable.valueByEditorStateFlow.tryEmit(date to false)
      date.whatTimeEditor = null
      if (!incrementAddList.remove(date)) {
        incrementRemoveList.add(date)
      }
      idModelEditor.sendValueByEditorStateFlow()
      return true
    }
    return false
  }

  override fun clear() {
    if (!idModelEditor.enableModify()) return
    idModelEditor.editorWhatTimeDate { whatTimeDate ->
      val dateList = whatTimeDate[this] ?: return
      dateList.forEach {
        if (!incrementAddList.contains(it)) {
          incrementRemoveList.add(it)
        }
        it.dateModel.enable.valueByEditorStateFlow.tryEmit(it to false)
        it.whatTimeEditor = null
      }
      dateList.clear()
      incrementAddList.clear()
      idModelEditor.sendValueByEditorStateFlow()
    }
  }

  internal fun replace(date: AffairDateModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (date !is AffairDateModelEditorImpl) return false
    if (date.idModelEditor !== idModelEditor) return false
    val oldWhatTimeModelEditor = date.whatTimeEditor
    if (oldWhatTimeModelEditor != null && oldWhatTimeModelEditor.idModelEditor !== idModelEditor) return false
    if (oldWhatTimeModelEditor === this) return true
    idModelEditor.editorWhatTimeDate { whatTimeDate ->
      val newDateList = whatTimeDate[this] ?: return false
      if (newDateList.any { it.date == date.date }) return false // 有相同日期则添加失败
      val oldDateList = if (oldWhatTimeModelEditor != null) whatTimeDate[oldWhatTimeModelEditor] else null
      if (oldWhatTimeModelEditor != null && oldDateList?.remove(date) == true) {
        if (!oldWhatTimeModelEditor.incrementAddList.remove(date)) {
          oldWhatTimeModelEditor.incrementRemoveList.add(date)
        }
      }
      newDateList.add(date)
      date.whatTimeEditor = this
      date.dateModel.whatTime.valueByEditorStateFlow.tryEmit(date to whatTimeModel)
      if (oldWhatTimeModelEditor == null) {
        // 说明已经被移除了
        date.dateModel.enable.valueByEditorStateFlow.tryEmit(date to true)
      }
      if (!incrementRemoveList.remove(date)) {
        incrementAddList.add(date)
      }
      idModelEditor.sendValueByEditorStateFlow()
      return true
    }
    return false
  }

  fun reset() {
    if (!idModelEditor.enableModify()) return
    setTimePair(whatTimeModel.timePair.value)
    idModelEditor.editorWhatTimeDate { whatTimeDate ->
      val nowDateList = whatTimeDate[this] ?: return
      // 添加的都移除
      incrementAddList.forEach {
        nowDateList.remove(it)
        it.dateModel.enable.valueByEditorStateFlow.tryEmit(it to false)
        it.whatTimeEditor = null
      }
      incrementAddList.clear()
      // 移除的都添加回来
      incrementRemoveList.forEach {
        it.whatTimeEditor?.incrementAddList?.remove(it)
        whatTimeDate[it.whatTimeEditor]?.remove(it)
        nowDateList.add(it)
        it.dateModel.whatTime.valueByEditorStateFlow.tryEmit(it to whatTimeModel)
        if (it.whatTimeEditor == null) {
          it.dateModel.enable.valueByEditorStateFlow.tryEmit(it to true)
        }
        it.whatTimeEditor = this
      }
      incrementRemoveList.clear()
      // 下层节点重置
      nowDateList.forEach {
        it.reset()
      }
    }
  }
}