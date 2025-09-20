package com.cyxbs.pages.store.service

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.store.api.IStoreService
import com.g985892345.provider.api.annotation.ImplProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.HttpException
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/6 15:51
 */
@ImplProvider
object StoreServiceImpl : IStoreService {

  override fun postTask(task: IStoreService.Task, onlyTag: String?,toast: String?) {

    when (task.type) {
      IStoreService.TaskType.BASE -> postTask(baseSp, task.title,toast)
      IStoreService.TaskType.MORE -> postTask(moreSp, task.title,toast)
    }
  }

  private fun postTask(sp: SharedPreferences, title: String, toast: String? =null) {
    // 先检查进度条是否已满
    val inEnd = sp.getBoolean(title, false)
    if (!inEnd) {
      if (checkOnlyTag(title)) {
        if(!toast.isNullOrEmpty()){
          toast(toast)
        }
        postTask(
          title,
          onSlopOver = {
            sp.edit { putBoolean(title, true) }
          }
        )
      }
    }
  }

  private fun checkOnlyTag(title: String, onlyTag: String? = null): Boolean {
    if (onlyTag != null) {
      val set = onlyTagSp.getStringSet(title, null)?.toHashSet() ?: hashSetOf()
      if (set.contains(onlyTag)) {
        return false
      } else {
        set.add(onlyTag)
        onlyTagSp.edit {
          putStringSet(title, set)
        }
        return true
      }
    } else {
      return true
    }
  }

  // 上一次发送任务的时间, 用于清空每日任务
  private var lastSaveDate: String
    get() = dateSp.getString("last_save_date", null) ?: ""
    set(value) {
      dateSp.edit { putString("last_save_date", value) }
    }
  private val dateSp = com.cyxbs.components.init.appContext.getSp("StoreServiceImpl_date")
  private val baseSp = com.cyxbs.components.init.appContext.getSp("StoreServiceImpl_base")
  private val moreSp = com.cyxbs.components.init.appContext.getSp("StoreServiceImpl_more")
  private val onlyTagSp = com.cyxbs.components.init.appContext.getSp("StoreServiceImpl_onlyTag")

  init {
    // Base 任务是每天刷新的, 不相等时就先清空所有本地保存的 sharedPreferences
    val nowDate = SimpleDateFormat("yyyy.M.d", Locale.CHINA).format(Date())
    if (lastSaveDate != nowDate) {
      lastSaveDate = nowDate
      baseSp.edit { clear() }
    }
  }

  // 发送请求, 该网络请求私有
  private fun postTask(
    title: String,
    onSlopOver: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null
  ) {
    ApiService::class.api
      .changeTaskProgress(title)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .throwOrInterceptException {
        if (it is HttpException && it.code() == 500) {
          // 在任务进度大于最大进度时, 后端返回 http 的错误码 500 导致回调到 onError 方法 所以这里手动拿到返回的 bean 类
          /*
          * todo 如果以后要改这里接口，我想说以下几点：
          *  1、把这个 http 的错误码 500 改成 200（这本来就是他们的不规范，我们端上也不好处理）；
          *  2、任务请求在我们端上点都不好做，希望能把逻辑写在后端
          * */
          onSlopOver?.invoke()
        }
      }
      .unsafeSubscribeBy {
        onSuccess?.invoke()
      }
  }

  private interface ApiService : IApi {
    // 用于改变积分商城界面的任务
    @POST("/magipoke-intergral/Integral/progress")
    @FormUrlEncoded
    fun changeTaskProgress(
      @Field("title") title: String
    ): Single<ApiStatus>
  }
}