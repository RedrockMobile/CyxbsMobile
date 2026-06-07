package com.cyxbs.pages.mine.edit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.launchPickImages
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.registerForPickSingleImage
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.mine.edit.network.EditApiService
import com.yalantis.ucrop.UCrop
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * 透明的头像选取 Activity。
 *
 * 由 [EditInfoPlatformImpl] 启动，承担：
 * 1. 弹「拍照 / 相册」选择 dialog
 * 2. 走 ActivityResultLauncher 取得图片 Uri
 * 3. 用 UCrop 做 1:1 裁剪
 * 4. 上传到服务器，把返回的 photo_src URL 通过 [EditInfoPlatformImpl.consumeCallback] 回调出去
 *
 * 流程结束 / 用户取消 / 上传失败 都会 finish。
 */
internal class AvatarPickerActivity : BaseActivity() {

  private val cameraImageFile by lazy {
    File(
      getExternalFilesDir(Environment.DIRECTORY_DCIM)?.absolutePath +
        File.separator + System.currentTimeMillis() + ".png"
    )
  }
  private val destinationFile by lazy {
    File(
      getExternalFilesDir(Environment.DIRECTORY_DCIM)?.absolutePath +
        File.separator + IAccountService::class.impl().stuNum + ".png"
    )
  }

  private val takePictureLauncher =
    registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
      if (ok) {
        startCropActivity(
          FileProvider.getUriForFile(this, "$packageName.fileProvider", cameraImageFile)
        )
      } else {
        finishWithCancel()
      }
    }

  private val pickImageLauncher = registerForPickSingleImage { uri ->
    if (uri != null) {
      startCropActivity(uri)
    } else {
      finishWithCancel()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showChooseDialog()
  }

  private fun showChooseDialog() {
    MaterialDialog(this).show {
      listItems(items = listOf("拍照", "从相册中选择")) { _, index, _ ->
        if (index == 0) getImageFromCamera() else getImageFromAlbum()
      }
      cornerRadius(res = com.mredrock.cyxbs.common.R.dimen.common_corner_radius)
      cancelOnTouchOutside(true)
      setOnCancelListener { finishWithCancel() }
    }
  }

  private fun getImageFromCamera() {
    doPermissionAction(Manifest.permission.CAMERA) {
      reason = "拍照需要访问你的相机哦~"
      doAfterGranted {
        takePictureLauncher.launch(
          FileProvider.getUriForFile(
            this@AvatarPickerActivity,
            "$packageName.fileProvider",
            cameraImageFile
          )
        )
      }
      doAfterRefused { finishWithCancel() }
    }
  }

  private fun getImageFromAlbum() {
    pickImageLauncher.launchPickImages()
  }

  private fun startCropActivity(uri: Uri) {
    val uCrop = UCrop.of(uri, Uri.fromFile(destinationFile))
    val options = UCrop.Options().apply {
      setCropGridStrokeWidth(5)
      setCompressionFormat(Bitmap.CompressFormat.PNG)
      setCompressionQuality(100)
      setLogoColor(
        ContextCompat.getColor(
          this@AvatarPickerActivity,
          com.mredrock.cyxbs.common.R.color.common_level_two_font_color
        )
      )
      setToolbarColor(
        ContextCompat.getColor(
          this@AvatarPickerActivity,
          com.cyxbs.components.config.R.color.colorPrimaryDark
        )
      )
    }
    uCrop.withOptions(options)
      .withAspectRatio(300f, 300f)
      .withMaxResultSize(300, 300)
      .start(this)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode != UCrop.REQUEST_CROP) return
    if (resultCode != Activity.RESULT_OK || data == null) {
      if (resultCode == UCrop.RESULT_ERROR && data != null) {
        toast(UCrop.getError(data)?.message ?: "Unexpected error")
      }
      finishWithCancel()
      return
    }
    uploadCroppedImage(data)
  }

  private fun uploadCroppedImage(data: Intent) {
    val resultUri = UCrop.getOutput(data)
    if (resultUri == null) {
      toast("无法获得裁剪结果")
      finishWithCancel()
      return
    }
    lifecycleScope.launch {
      val fileBytes = try {
        destinationFile.readBytes()
      } catch (e: IOException) {
        e.printStackTrace()
        toast("图片加载失败")
        finishWithCancel()
        return@launch
      }
      val stuNum = IAccountService::class.impl().stuNum.orEmpty()
      val body = MultiPartFormDataContent(
        formData {
          append("stunum", stuNum)
          append("fold", fileBytes, Headers.build {
            append(HttpHeaders.ContentType, "image/png")
            append(HttpHeaders.ContentDisposition, "filename=\"${destinationFile.name}\"")
          })
        }
      )
      runCatchingCoroutine {
        EditApiService::class.impl().uploadAvatar(body)
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onSuccess {
        // 仅负责上传文件到 OSS / 拿到 URL，把 URL 通过事件流交给 ViewModel；
        // 真正的"提交个人信息"由 ViewModel 统一处理，业务逻辑不散落在 Activity。
        EditInfoPlatformImpl.consumeCallback(it.photoSrc)
        finish()
      }.onFailure {
        toast("上传头像失败")
        logg(it.message)
        finishWithCancel()
      }
    }
  }

  private fun finishWithCancel() {
    EditInfoPlatformImpl.consumeCallback(null)
    finish()
  }
}
