package com.mredrock.cyxbs.common.utils.extensions

import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.cyxbs.components.init.appContext
import java.io.File

/**
 * Created By jay68 on 2018/8/15.
 */
val File.uri: Uri
    get() = if (Build.VERSION.SDK_INT >= 24) {
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileProvider", this)
    } else {
        Uri.fromFile(this)
    }
