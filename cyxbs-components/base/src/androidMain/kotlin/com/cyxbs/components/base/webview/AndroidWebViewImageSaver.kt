package com.cyxbs.components.base.webview

import android.Manifest
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import com.cyxbs.components.config.dir.DIR_PHOTO
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.loadBitmap
import com.cyxbs.components.utils.extensions.saveImage
import com.cyxbs.components.utils.extensions.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 保持旧 WebView 的确认、授权、下载和相册保存流程。 */
internal class AndroidWebViewImageSaver(
  private val activity: FragmentActivity?,
) {

  fun requestSave(url: String) {
    val targetActivity = activity ?: run {
      toast("当前页面无法保存图片")
      return
    }
    targetActivity.runOnUiThread {
      targetActivity.doPermissionAction(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
        doAfterGranted {
          MaterialAlertDialogBuilder(targetActivity)
            .setTitle("是否保存")
            .setMessage("这张图片将保存到手机")
            .setPositiveButton("确定") { dialog, _ ->
              targetActivity.loadBitmap(url) { bitmap ->
                targetActivity.saveImage(bitmap, imageName(url))
                MediaScannerConnection.scanFile(
                  targetActivity,
                  arrayOf("${Environment.getExternalStorageDirectory()}$DIR_PHOTO"),
                  arrayOf("image/jpeg"),
                  null,
                )
                targetActivity.runOnUiThread {
                  toast("图片保存于${Environment.DIRECTORY_PICTURES}${DIR_PHOTO}文件夹下哦")
                  dialog.dismiss()
                }
              }
            }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .show()
        }
      }
    }
  }

  private fun imageName(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    val extension = if (path.endsWith(".png", ignoreCase = true)) ".png" else ".jpg"
    return "${System.currentTimeMillis()}$extension"
  }
}
