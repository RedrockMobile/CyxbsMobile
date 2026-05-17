package com.cyxbs.components.utils.extensions

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Android 图片选择器工具类
 * 
 * 使用 Android 官方的 Photo Picker API，无需申请存储权限
 * - Android 13+ (API 33+): 使用系统级 Photo Picker
 * - Android 13-: 自动降级为传统文件选择器
 * 
 * @author GuoXiangrui
 * @time 2026/5/17
 */


/*
* 如果你需要在多平台上使用图片选择器，则可以参考 map 模块中的 UploadPhotoDialog
* 使用多平台文件选择库 FileKit https://github.com/vinceglb/FileKit
*
*
* */

// ==================== Activity 扩展函数 ====================

/**
 * 注册多选图片的 ActivityResultLauncher
 * 
 * @param maxImages 最大可选择图片数量，默认 9 张
 * @return ActivityResultLauncher<List<Uri>> 用于启动图片选择器
 * 
 * 使用示例：
 * ```kotlin
 * private val pickImagesLauncher = registerForPickMultipleImages(5) { uris ->
 *     // 处理选中的图片 URI 列表
 *     if (uris.isNotEmpty()) {
 *         handleSelectedImages(uris)
 *     }
 * }
 * 
 * // 启动选择器
 * pickImagesLauncher.launchPickImages()
 * ```
 */
fun FragmentActivity.registerForPickMultipleImages(
    maxImages: Int = 9,
    onResult: (List<Uri>) -> Unit
): ActivityResultLauncher<PickVisualMediaRequest> {
    return registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxImages)
    ) { uris ->
        onResult(uris)
    }
}

/**
 * 注册单选图片的 ActivityResultLauncher
 * 
 * @return ActivityResultLauncher<Uri?> 用于启动图片选择器
 * 
 * 使用示例：
 * ```kotlin
 * private val pickImageLauncher = registerForPickSingleImage { uri ->
 *     // 处理选中的图片 URI
 *     uri?.let {
 *         handleSelectedImage(it)
 *     }
 * }
 * 
 * // 启动选择器
 * pickImageLauncher.launchPickImages()
 * ```
 */
fun FragmentActivity.registerForPickSingleImage(
    onResult: (Uri?) -> Unit
): ActivityResultLauncher<PickVisualMediaRequest> {
    return registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onResult(uri)
    }
}

// ==================== Fragment 扩展函数 ====================

/**
 * Fragment 中注册多选图片的 ActivityResultLauncher
 * 
 * @param maxImages 最大可选择图片数量，默认 9 张
 * @return ActivityResultLauncher<List<Uri>> 用于启动图片选择器
 */
fun Fragment.registerForPickMultipleImages(
    maxImages: Int = 9,
    onResult: (List<Uri>) -> Unit
): ActivityResultLauncher<PickVisualMediaRequest> {
    return registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxImages)
    ) { uris ->
        onResult(uris)
    }
}

/**
 * Fragment 中注册单选图片的 ActivityResultLauncher
 * 
 * @return ActivityResultLauncher<Uri?> 用于启动图片选择器
 */
fun Fragment.registerForPickSingleImage(
    onResult: (Uri?) -> Unit
): ActivityResultLauncher<PickVisualMediaRequest> {
    return registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onResult(uri)
    }
}

// ==================== 便捷启动函数 ====================

/**
 * 启动多选图片选择器（仅图片）
 * 
 * 使用示例：
 * ```kotlin
 * pickImagesLauncher.launchPickImages()
 * ```
 */
fun ActivityResultLauncher<PickVisualMediaRequest>.launchPickImages() {
    this.launch(PickVisualMediaRequest(ImageOnly))
}

/**
 * 启动多选图片选择器（图片和视频）
 * 
 * 使用示例：
 * ```kotlin
 * pickMediaLauncher.launchPickImagesAndVideos()
 * ```
 */
fun ActivityResultLauncher<PickVisualMediaRequest>.launchPickImagesAndVideos() {
    this.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
}

/**
 * 启动多选图片选择器（仅视频）
 * 
 * 使用示例：
 * ```kotlin
 * pickVideosLauncher.launchPickVideos()
 * ```
 */
fun ActivityResultLauncher<PickVisualMediaRequest>.launchPickVideos() {
    this.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
}

/**
 * 启动多选图片选择器（指定 MIME 类型）
 * 
 * @param mimeType MIME 类型，例如 "image/gif"
 * 
 * 使用示例：
 * ```kotlin
 * pickGifLauncher.launchPickByMimeType("image/gif")
 * ```
 */
fun ActivityResultLauncher<PickVisualMediaRequest>.launchPickByMimeType(mimeType: String) {
    this.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType(mimeType)))
}
