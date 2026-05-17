package com.cyxbs.pages.mine.page.feedback.edit.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.pages.mine.page.feedback.api
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * @Date : 2021/8/23   20:59
 * @By ysh
 * @Usage :
 * @Request : God bless my code
 **/
class FeedbackEditViewModel: BaseViewModel() {

    /**
     * Add
     * 图片的Uri地址
     */
    private val _uris: MutableLiveData<List<Uri>> = MutableLiveData(listOf())
    val uris: LiveData<List<Uri>>
        get() = _uris

    private val _finishEvent = MutableSharedFlow<Unit>()
    //finish
    val finishEvent: SharedFlow<Unit> get() = _finishEvent

    /**
     * Post数据
     */
    fun postFeedbackInfo(productId:String, type:String, title:String, content:String, file: List<File>){
        val productIdRB = productId.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val typeRB = type.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val titleRB = title.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val contentRB = content.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        val map = HashMap<String, RequestBody>()
        map.apply {
            put("product_id", productIdRB)
            put("type", typeRB)
            put("title", titleRB)
            put("content", contentRB)
        }
        val fileBody = if (file.isNotEmpty()){
            (file.indices).map {
                MultipartBody.Part.createFormData("file", file[it].name, file[it].asRequestBody("multipart/form-data".toMediaTypeOrNull()))
            }
        }else{
            null
        }
        api.postFeedbackInfo(map,fileBody)
            .setSchedulers()
            .doOnError {
                toast("提交失败：${it.message}")
            }
            .safeSubscribeBy {
                toast("提交成功  我们会尽快回复")
                launchByViewModelScope {
                    _finishEvent.emit(Unit)
                }
            }
    }

    /**
     * 更新选中的图片列表
     */
    fun updateSelectedImages(uris: List<Uri>) {
        _uris.value = uris
    }

    /**
     * 当移除图片的按钮被点击的时候移除对应的图片
     */
    fun removePic(uri: Uri) {
        val urls = uris.value ?: return
        _uris.value = urls.filter { it != uri }
    }
}