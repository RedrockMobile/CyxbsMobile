package com.cyxbs.pages.affair.model.impl

import com.cyxbs.pages.affair.api.EditorStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/1
 */
class EditorStateFlowImpl<Value>(
  valueFlow: MutableStateFlow<Value>,
  valueByEditorFlow: MutableSharedFlow<Value> = MutableSharedFlow(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  ),
) : EditorStateFlow<Value>(
  valueFlow,
  valueByEditorFlow.debounce(10.milliseconds)
) {
  val valueStateFlow = valueFlow
  val valueByEditorStateFlow = valueByEditorFlow
}