package com.cyxbs.functions.code.editor.project

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import java.io.File

/**
 * Android 将项目固定在公共下载目录的 `CyxbsProjects` 子目录。
 *
 * 首次安装或重装后通过 SAF 让用户确认一次该目录，系统会授予可持久化的目录读写权限；源码本身位于
 * 公共存储，卸载应用不会删除。选择器打开前使用 Toast 说明唯一需要执行的确认操作。
 */
internal actual suspend fun resolveDefaultCodeProjectStorageRoot(
  settings: Settings,
  requestIfMissing: Boolean,
): CodeProjectStorageRoot? = resolveExternalCodeProjectStorageRoot(
  settings = settings,
  requestIfMissing = requestIfMissing,
  selectedDirectoryIsProjectRoot = true,
  fixedDisplayPath = ANDROID_CODE_PROJECTS_DISPLAY_PATH,
  directoryValidator = ::isAndroidCodeProjectsDirectory,
  directoryPicker = {
    // 个别厂商的 MediaStore 可能拒绝创建隐藏标记；此时仍应打开选择器，让已有目录可以继续授权。
    runCatching(::ensureAndroidCodeProjectsDirectoryExists)
    Toast.makeText(
      FileKit.context,
      "已定位到项目目录，请点击底部“使用此文件夹”",
      Toast.LENGTH_LONG,
    ).show()
    FileKit.openDirectoryPicker(
      directory = PlatformFile(androidCodeProjectsDocumentUri()),
    )
  },
)

/**
 * 在打开系统选择器前创建公共项目目录，确保初始 URI 能直接落到目标文件夹。
 *
 * Android 10 起通过 MediaStore 创建一个极小的标记文件，从而无需申请广泛存储权限；旧系统仅在已经
 * 获得传统外部存储权限时直接创建目录，否则交由系统选择器处理已有目录。
 */
private fun ensureAndroidCodeProjectsDirectoryExists() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    ensureAndroidCodeProjectsDirectoryWithMediaStore()
    return
  }
  if (
    FileKit.context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
      PackageManager.PERMISSION_GRANTED
  ) {
    @Suppress("DEPRECATION")
    File(
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
      CODE_PROJECTS_DIRECTORY_NAME,
    ).mkdirs()
  }
}

/** 通过 MediaStore 在公共下载目录中物化 `CyxbsProjects`，不依赖应用私有目录。 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun ensureAndroidCodeProjectsDirectoryWithMediaStore() {
  val resolver = FileKit.context.contentResolver
  val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$CODE_PROJECTS_DIRECTORY_NAME/"
  val selection =
    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
  val selectionArgs = arrayOf(ANDROID_PROJECT_ROOT_MARKER_NAME, relativePath)
  val markerExists = resolver.query(
    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
    arrayOf(MediaStore.MediaColumns._ID),
    selection,
    selectionArgs,
    null,
  )?.use { it.moveToFirst() } == true
  if (markerExists) return

  val markerUri = resolver.insert(
    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
    ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, ANDROID_PROJECT_ROOT_MARKER_NAME)
      put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
      put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
    },
  ) ?: return
  resolver.openOutputStream(markerUri)?.use { output ->
    output.write("{}".encodeToByteArray())
  }
}

/** 构造系统 DocumentsUI 可识别的公共下载子目录 URI，供选择器直接定位。 */
private fun androidCodeProjectsDocumentUri(): Uri = DocumentsContract.buildDocumentUri(
  ANDROID_EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
  "primary:${Environment.DIRECTORY_DOWNLOADS}/$CODE_PROJECTS_DIRECTORY_NAME",
)

/**
 * 限制授权范围为固定项目目录，避免用户误选其他位置后出现多个项目根目录。
 */
private fun isAndroidCodeProjectsDirectory(directory: PlatformFile): Boolean {
  val uri = (directory.androidFile as? AndroidFile.UriWrapper)?.uri ?: return false
  val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    ?: return false
  return documentId ==
    "primary:${Environment.DIRECTORY_DOWNLOADS}/$CODE_PROJECTS_DIRECTORY_NAME"
}

private const val ANDROID_EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY =
  "com.android.externalstorage.documents"
private const val ANDROID_PROJECT_ROOT_MARKER_NAME = ".cyxbs-project-root.json"
private const val ANDROID_CODE_PROJECTS_DISPLAY_PATH = "Download/$CODE_PROJECTS_DIRECTORY_NAME"
