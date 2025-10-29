package com.cyxbs.components.utils.compose

import android.content.Intent
import com.cyxbs.components.init.appContext

actual fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            text
        )
    }
    appContext.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}