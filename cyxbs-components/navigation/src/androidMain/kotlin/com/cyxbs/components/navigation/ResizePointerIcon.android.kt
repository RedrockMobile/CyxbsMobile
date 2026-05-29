package com.cyxbs.components.navigation

import androidx.compose.ui.input.pointer.PointerIcon
import com.cyxbs.components.init.appContext
import android.view.PointerIcon as AndroidPointerIcon

internal actual val HorizontalResizePointerIcon: PointerIcon by lazy {
  PointerIcon(
    AndroidPointerIcon.getSystemIcon(appContext, AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
  )
}
