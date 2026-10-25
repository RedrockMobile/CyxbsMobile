package com.cyxbs.pages.schedule.ui.settings

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android 10 及以上把 ICS 写入公共 `Download/Cyxbs`，随后尝试交给日历或文件应用打开。
 * Android 8、9 使用系统保存面板，避免为了固定目录额外申请旧版外部存储权限。
 */
@Composable
internal actual fun rememberScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
    return rememberFileKitScheduleIcsFileSaveLauncher(onResult)
  }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentOnResult by rememberUpdatedState(onResult)
  return remember(context, coroutineScope) {
    ScheduleIcsFileSaveLauncher(isSupported = true) { fileName, content ->
      coroutineScope.launch {
        try {
          val savedFile = withContext(Dispatchers.IO) {
            saveToPublicDownloads(context, fileName, content)
          }
          currentOnResult(
            ScheduleIcsFileSaveResult.Saved(
              "保存位置：Download/$DOWNLOAD_DIRECTORY_NAME/${savedFile.displayName}",
            ),
          )
          // 打开失败不回滚文件；用户仍可从下载目录手动选择日历应用导入。
          runCatching { openIcsFile(context, savedFile.displayName) }
            .onFailure { throwable ->
              Log.w(ICS_EXPORT_LOG_TAG, "无法打开刚导出的 ICS 文件", throwable)
            }
        } catch (throwable: CancellationException) {
          throw throwable
        } catch (throwable: Throwable) {
          currentOnResult(ScheduleIcsFileSaveResult.Failed(throwable))
        }
      }
    }
  }
}

/** 记录 MediaStore 最终采用的文件名；重名时系统可能自动追加序号。 */
private data class SavedAndroidIcs(
  val displayName: String,
)

/**
 * 通过 MediaStore 原子写入公共下载目录。
 *
 * 写入完成前保持 `IS_PENDING=1`，异常时删除未完成行，防止下载目录残留损坏文件。
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToPublicDownloads(
  context: Context,
  fileName: String,
  content: String,
): SavedAndroidIcs {
  val resolver = context.contentResolver
  val initialValues = ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
    put(MediaStore.Downloads.MIME_TYPE, ICS_MIME_TYPE)
    put(
      MediaStore.Downloads.RELATIVE_PATH,
      "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIRECTORY_NAME",
    )
    put(MediaStore.Downloads.IS_PENDING, 1)
  }
  val uri = checkNotNull(
    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, initialValues),
  ) { "无法在公共下载目录创建 ICS 文件" }
  try {
    checkNotNull(resolver.openOutputStream(uri, "w")) {
      "无法打开 ICS 文件输出流"
    }.use { output ->
      output.write(content.encodeToByteArray())
    }
    val readyValues = ContentValues().apply {
      put(MediaStore.Downloads.IS_PENDING, 0)
    }
    check(resolver.update(uri, readyValues, null, null) == 1) {
      "无法完成 ICS 文件写入"
    }
    return SavedAndroidIcs(
      displayName = resolver.query(
        uri,
        arrayOf(MediaStore.Downloads.DISPLAY_NAME),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      } ?: fileName,
    )
  } catch (throwable: Throwable) {
    resolver.delete(uri, null, null)
    throw throwable
  }
}

/**
 * 使用只读 URI 权限打开刚导出的文件；选择器中只展示能够处理 ICS 的应用。
 *
 * 不能直接传 MediaStore URI：部分日历应用只检查 URI 最后一段是否以 `.ics` 结尾，而 MediaStore
 * 最后一段是数字 ID。这里通过项目已有的 FileProvider 为同一公共文件生成保留文件名的 URI。
 */
@Suppress("DEPRECATION")
private fun openIcsFile(context: Context, displayName: String) {
  val file = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "$DOWNLOAD_DIRECTORY_NAME/$displayName",
  )
  val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileProvider",
    file,
  )
  val openIntent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, ICS_MIME_TYPE)
    clipData = ClipData.newUri(context.contentResolver, "日程 ICS", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(
    Intent.createChooser(openIntent, "打开日程文件").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },
  )
}

private const val DOWNLOAD_DIRECTORY_NAME = "Cyxbs"
private const val ICS_MIME_TYPE = "text/calendar"
private const val ICS_EXPORT_LOG_TAG = "ScheduleIcsExport"
