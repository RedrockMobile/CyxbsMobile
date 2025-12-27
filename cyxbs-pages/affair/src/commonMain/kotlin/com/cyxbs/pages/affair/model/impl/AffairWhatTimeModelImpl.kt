package com.cyxbs.pages.affair.model.impl

import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.editor.AffairWhatTimeModelEditorImpl
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairWhatTimeModelImpl(
  override val idModel: AffairIdModel,
  timePair: MinuteTimePair,
) : AffairWhatTimeModel {
  override val enable: EditorStateFlowImpl<AffairWhatTimeModelEditor, Boolean> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(true)
    )
  override val timePair: EditorStateFlowImpl<AffairWhatTimeModelEditor, MinuteTimePair> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(timePair),
    )

  fun update(editor: AffairWhatTimeModelEditorImpl) {
    timePair.valueStateFlow.value = editor.timePair
    editor.incrementRemoveList.forEach { dateModelEditor ->
      if (dateModelEditor.whatTimeEditor == null) {
        // 说明已经被移除
        dateModelEditor.dateModel.enable.valueStateFlow.value = false
      }
    }
    editor.incrementRemoveList.clear()
    editor.incrementAddList.clear()
  }

  override fun toString(): String {
    return "AffairWhatTimeModelImpl(enable=${enable.value}" +
        ", timePair=${timePair.value}" +
        ")"
  }
}