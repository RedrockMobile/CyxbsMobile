package com.cyxbs.pages.affair.model.editor

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.impl.AffairDateModelImpl

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairDateModelEditorImpl(
  override val idModelEditor: AffairIdModelEditorImpl,
  override var whatTimeEditor: AffairWhatTimeModelEditorImpl?,
  override val dateModel: AffairDateModelImpl,
) : AffairDateModelEditor {

  override var date = dateModel.date.value

  override fun setDate(date: Date): String? {
    if (!idModelEditor.enableModify()) return "提交修改后不可再修改"
    if (date == this.date) return null
    if (whatTimeEditor?.dateList?.any { it.date == date } == true) {
      return "该日期已存在"
    }
    this.date = date
    dateModel.date.valueByEditorStateFlow.tryEmit(date)
    return null
  }

  override fun replace(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    return whatTime.replace(this)
  }

  fun reset() {
    if (!idModelEditor.enableModify()) return
    setDate(dateModel.date.value)
  }
}