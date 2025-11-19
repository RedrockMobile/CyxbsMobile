package com.cyxbs.pages.map.viewmodel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.bean.*
import com.cyxbs.pages.map.model.DataSet
import com.cyxbs.pages.map.widget.*
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.interceptException
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.CommonApiService
import com.cyxbs.components.utils.network.DownMessageParams
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.network.mapOrThrowApiException
import com.cyxbs.pages.map.network.MapApiService
import com.cyxbs.pages.map.util.ObservableArrayList
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import top.limuyang2.photolibrary.LPhotoHelper
import top.limuyang2.photolibrary.util.LPPImageType
import java.io.File


/**
 *@author zhangzhe
 *@date 2020/8/8
 *@description 由于掌邮地图模块采用单activity多fragment模式，所以所有fragment都共用本viewModel
 */

class MapViewModel : BaseViewModel() {
    companion object {
        /** 从掌邮获取的数据为null*/
        const val PLACE_SEARCH_NULL = "null"

        /** 从掌邮获取的地点未找到*/
        const val PLACE_SEARCH_500 = "500"
        const val DOWN_MESSAGE_NAME = "zscy-map-vr-url"

        /** 图片选择的requestCode*/
        const val PICTURE_SELECT = 0x1024

    }

    init {
        //优先请求，避免进入vr还没有拿到url
        getVrUrl()
    }

    private lateinit var mapApiService: MapApiService

    //网络请求得到的数据(地图基本信息接口)，如何使用：在要使用的activity或者fragment观察即可
    val mapInfo = MutableLiveData<MapInfo>()

    //搜索框下方按钮内容
    val buttonInfo = MutableLiveData<ButtonInfo>()

    //喜欢列表内容
    val collectList = MutableLiveData<MutableList<FavoritePlace>>()

    //当前展示的地点信息
    val placeDetails = MutableLiveData<PlaceDetails>()

    //是否正在显示收藏页面
    val fragmentFavoriteEditIsShowing = MutableLiveData<Boolean>()

    //是否正在显示全部图片界面
    val fragmentAllPictureIsShowing = MutableLiveData<Boolean>()

    //详细页面正在显示的地点id
    var showingPlaceId = "-1"

    //是否显示bottomSheet，用于监听并隐藏
    val bottomSheetStatus = MutableLiveData<Int>()

    //是否在动画中
    val mapViewIsInAnimation = MutableLiveData<Boolean>()

    //搜索框的文字，只用在搜索界面fragment观察本变量即可实现搜索
    val searchText = MutableLiveData("")

    //搜索结果
    val searchResult = ObservableArrayList<PlaceItem>()

    //搜索记录
    var searchHistory = MutableLiveData<MutableList<String>>(mutableListOf())

    //当历史记录点击，通知输入框更新
    val searchHistoryString = MutableLiveData<String>("")

    //显示的地点id
    val showSomePlaceIconById = MutableLiveData<MutableList<String>>()

    //缩放到某一个地点id
    val showIconById = MutableLiveData<String>()

    //用于通知mainFragment关闭搜索框
    val closeSearchFragment = MutableLiveData<Boolean>()

    //网络请求失败，使用本地缓存
    val loadFail = MutableLiveData<Unit>()

    //是否锁定
    val isLock = MutableLiveData<Boolean>(false)

    //是否点击了标签或收藏
    val isClickSymbol = MutableLiveData<Boolean>()

    //通知收藏列表关闭
    val showPopUpWindow = MutableLiveData<Boolean>()

    //通知标签栏取消选择
    val unCheck = MutableLiveData<Boolean>()

    //掌邮传过来的OpenSiteId
    val openId = MutableLiveData<String>()

    //VR地图url
    var vrUrl = ""

    fun init() {

        /**
         * 初始化网络请求
         */
        mapApiService = ApiGenerator.getApiService(MapApiService::class.java)
        /**
         * 获得地图基本信息，地点坐标等
         */
        mapApiService.getMapInfo()//网络请求替换为：apiService.getMapInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                toast(R.string.map_network_connect_error)
                //使用缓存数据
                val mapInfoStore = DataSet.getMapInfo()
                if (mapInfoStore != null) {
                    mapInfo.postValue(mapInfoStore!!)
                }
                loadFail.postValue(Unit)
            }
            .safeSubscribeBy {
                mapInfo.value = it
                DataSet.saveMapInfo(it)
            }
        mapApiService.getButtonInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                toast(R.string.map_network_connect_error,)
                //使用缓存数据
                val buttonInfoStore = DataSet.getButtonInfo()
                if (buttonInfoStore != null) {
                    buttonInfo.postValue(buttonInfoStore!!)
                }
            }
            .safeSubscribeBy {
                buttonInfo.postValue(it)
                DataSet.saveButtonInfo(it)
            }
        /**
         * 刷新收藏列表
         */
        refreshCollectList(false)

    }


    /**
     * 获得地点详细信息，如 图片、标签 等
     * 当地图标签被点击，执行此网络请求，在对应的fragment观察数据即可
     */
    fun getPlaceDetails(placeId: String, showBottomSheetAfterSuccess: Boolean) {
        mapApiService.getPlaceDetails(placeId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                toast(R.string.map_network_connect_error)
                //有缓存则使用缓存（上面和下面的逻辑基本和这里是一样的，所有缓存在DataSet里
                //去掉有DataSet的语句就是基本的业务逻辑了（逻辑就很清晰了））
                val t = DataSet.getPlaceDetails(placeId)
                if (t != null) {
                    showingPlaceId = placeId
                    placeDetails.postValue(t!!)
                    if (bottomSheetStatus.value == BottomSheetBehavior.STATE_HIDDEN) {
                        bottomSheetStatus.postValue(BottomSheetBehavior.STATE_COLLAPSED)
                    }
                    if (showBottomSheetAfterSuccess) {
                        bottomSheetStatus.postValue(BottomSheetBehavior.STATE_EXPANDED)
                    }
                } else {
                    bottomSheetStatus.postValue(BottomSheetBehavior.STATE_HIDDEN)
                }
            }
            .safeSubscribeBy {
                showingPlaceId = placeId
                placeDetails.postValue(it)
                if (bottomSheetStatus.value == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetStatus.postValue(BottomSheetBehavior.STATE_COLLAPSED)
                }
                DataSet.savePlaceDetails(it, placeId)

                if (showBottomSheetAfterSuccess) {
                    bottomSheetStatus.postValue(BottomSheetBehavior.STATE_EXPANDED)
                }
            }
    }

    /**
     * 刷新收藏列表
     * @param showCollectedPlace 是否在获得网络数据后，显示全部收藏地点并缩放
     */
    fun refreshCollectList(showCollectedPlace: Boolean) {
        mapApiService.getCollect()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                val list = DataSet.getCollect()
                if (list != null) {
                    collectList.postValue(list!!)
                }
                if (collectList.value?.size == 0) {
                    toast(R.string.map_favorite_empty)
                } else {
                    if (showCollectedPlace) {
                        val listTmp = mutableListOf<String>()
                        if (list != null) {
                            listTmp.addAll(list.map { t -> t.placeId })
                        }
                        showSomePlaceIconById.value = listTmp
                    }
                }
            }
            .safeSubscribeBy {
                /**
                 * 同步服务端与缓存。因为之前是可以取placeNickname的所以这里还可以简化，
                 * 但保留功能扩展性，而且本来这里数据量很小，故下列代码也可以接受
                 */
                it.placeIdList.forEach { item ->
                    DataSet.addCollect(item)
                }

                DataSet.getCollect()?.forEach { t ->
                    var existInServer = false
                    for (i in it.placeIdList) {
                        if (t.placeId == i) {
                            existInServer = true
                            break
                        }
                    }
                    if (!existInServer) {
                        DataSet.deleteCollect(t.placeId)
                    }
                }

                collectList.value = DataSet.getCollect()

                if (collectList.value?.size == 0) {
                    toast(R.string.map_favorite_empty)
                } else {
                    if (showCollectedPlace) {
                        showSomePlaceIconById.value = it.placeIdList
                    }
                }
            }
    }

    /**
     * 添加收藏
     */
    fun addCollect(placeNickname: String, id: String) {
        var notExist = true
        if (collectList.value != null) {
            for (t: FavoritePlace in collectList.value!!) {
                if (t.placeId == id) {
                    notExist = false
                }
            }
        }

        if (notExist) {
            mapApiService.addCollect(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .interceptException {
                    toast(R.string.map_network_connect_error)
                    ProgressDialog.hide()
                    refreshCollectList(true)
                }
                .safeSubscribeBy {
                    if (it.isSuccess()) {
                        DataSet.addCollect(FavoritePlace(placeNickname, id))
                        toast(R.string.map_collect_success)
                    } else {
                        toast(R.string.map_network_connect_error)
                    }
                    ProgressDialog.hide()
                    refreshCollectList(true)
                }
        } else {
            DataSet.addCollect(FavoritePlace(placeNickname, id))
            ProgressDialog.hide()
            refreshCollectList(true)
        }


    }

    /**
     * 删除收藏
     */
    fun deleteCollect(id: String) {
        mapApiService.deleteCollect(id.toInt())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .interceptException {
                toast(R.string.map_network_connect_error)
                ProgressDialog.hide()
                refreshCollectList(true)
            }
            .safeSubscribeBy {
                if (it.isSuccess()) {
                    DataSet.deleteCollect(id)
                } else {
                    toast(R.string.map_network_connect_error)
                }
                ProgressDialog.hide()
                refreshCollectList(true)
            }

    }

    /**
     * 搜索历史记录变化，从缓存中重新读取，并通知更新
     */
    fun notifySearchHistoryChange() {
        searchHistory.value = DataSet.getSearchHistory() ?: mutableListOf()
        searchHistory.value!!.reverse()
    }

    /**
     * 进入地图获取要聚焦的点
     * @param openString 从其他activity的intent过来传的数据。
     * 通过该数据请求服务器（服务器解析该string），返回一个地点id并放大显示
     */
    fun getPlaceSearch(openString: String?) {
        mapApiService.placeSearch(openString ?: "-1")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .safeSubscribeBy {
                // 这里手动判断 statue，不使用 mapOrThrowApiException()
                if (it.status == 200) {
                    openId.value = it.data.placeId
                    getPlaceDetails(it.data.placeId, false)
                    bottomSheetStatus.value = BottomSheetBehavior.STATE_COLLAPSED
                }
                if (it.status == 500) {
                    if (openString == null) {
                        openId.value = PLACE_SEARCH_NULL
                    } else {
                        openId.value = PLACE_SEARCH_500
                    }
                    getPlaceDetails(mapInfo.value!!.openSiteId.toString(), false)
                    bottomSheetStatus.value = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
    }

    /**
     * 上传搜索热度
     * 由于搜索是本地搜索，所以如果要添加搜索的热度，则必须上传
     * @param placeId 增加该地点的搜索量
     */
    fun addHot(placeId: String) {
        mapApiService.addHot(placeId.toInt())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .interceptException {
                toast(appContext.getString(R.string.map_add_hot_failed))
            }
            .safeSubscribeBy()
    }

    /**
     * 获得vr界面的链接
     */

    private fun getVrUrl() {
        ApiGenerator.getCommonApiService(CommonApiService::class.java)
            .getDownMessage(DownMessageParams(DOWN_MESSAGE_NAME))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrThrowApiException()
            .safeSubscribeBy {
                if (!it.textList.isNullOrEmpty()) {
                    vrUrl = it.textList[0].content
                }
            }

    }

    /**
     * 上传图片
     * @param imgPath 要上传图片的路径列表
     * @param context 用于显示dialog
     */

    fun uploadPicture(imgPath: ArrayList<String>, context: Context) {
        val fileList = imgPath.map { File(it) }
        val pictureTotal = fileList.size
        var uploadSuccessCount = 0
        var uploadFailedCount = 0
        for (i in fileList.indices) {
            val file = fileList[i]
            val requestFile: RequestBody =
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
            val fileName: String = System.currentTimeMillis().toString() + "${i}.jpg"
            val body: MultipartBody.Part =
                MultipartBody.Part.createFormData("file", fileName, requestFile)
            val params: MutableMap<String, Int> = HashMap()
            params["place_id"] = showingPlaceId.toInt()

            mapApiService.uploadPicture(body, params)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    if (uploadFailedCount + uploadSuccessCount == pictureTotal) {
                        ProgressDialog.hide()
                        MapDialogTips.show(
                            context,
                            context.resources.getString(R.string.map_upload_picture_success_title),
                            context.resources.getString(R.string.map_upload_picture_success_content) + "\n上传成功数量：$uploadSuccessCount\n上传失败数量：$uploadFailedCount",
                            true,
                            object : OnSelectListenerTips {
                                override fun onPositive() {}
                            })
                    }
                }
                .interceptException {
                    uploadFailedCount++
                }
                .safeSubscribeBy {
                    if (it.isSuccess()) {
                        uploadSuccessCount++
                    } else {
                        uploadFailedCount++
                    }
                }
        }

    }

    /**
     * 打开图片选择器
     * @param activity 用于intent
     */

    fun selectPic(activity: Activity) {
        LPhotoHelper.Builder()
            .maxChooseCount(9) //最多选几个
            .columnsNumber(3) //每行显示几列图片
            .imageType(LPPImageType.ofAll()) // 文件类型
            .pauseOnScroll(false) // 是否滑动暂停加载图片显示
            .isSingleChoose(false) // 是否是单选
            .isOpenLastAlbum(false) // 是否直接打开最后一次选择的相册
            .selectedPhotos(ArrayList())
            .theme(com.cyxbs.components.config.R.style.ConfigLPhotoTheme)
            .build()
            .start(activity, PICTURE_SELECT)
    }

    /**
     * 分享图片逻辑
     * 判断是否登录了
     * @param context 用于判断是否登录
     * @param fragment 供requireSharePermission方法使用
     */

    fun sharePicture(context: Context, fragment: BaseFragment) {
        fragment.doIfLogin("分享") {
            fragment.doPermissionAction(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ) {
                reason = appContext.getString(R.string.map_require_permission_tips)
                doAfterGranted {
                    requireSharePermission(context, fragment)
                }
                doAfterRefused {
                    requireSharePermission(context, fragment)
                }
            }
        }
    }

    /**
     * 获取分享权限逻辑
     * @param context 获取权限用
     * @param fragment 用于获得fragment依附的activity（这里是MapActivity），
     * 去调用selectPic(activity)方法，以至于用intent，打开的activity返回后，
     * 回到的是MapActivity的onActivityResult方法
     */
    private fun requireSharePermission(context: Context, fragment: Fragment) {
        if (appContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            toast(R.string.map_no_permission_store)
            return
        }
        context.let {
            MapDialog.show(
                it,
                appContext.getString(R.string.map_share_picture_title),
                appContext.getString(R.string.map_share_picture),
                object : OnSelectListener {
                    override fun onDeny() {
                    }

                    override fun onPositive() {
                        fragment.activity?.let { it1 -> selectPic(it1) }
                    }
                })
        }
    }

}


