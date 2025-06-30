package com.cyxbs.pages.schoolcar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CustomMapStyleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_SCHOOL_CAR
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.px2dp
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schoolcar.adapter.CarIconAdapter
import com.cyxbs.pages.schoolcar.adapter.CarSiteAdapter
import com.cyxbs.pages.schoolcar.bean.SchoolCarLocation
import com.cyxbs.pages.schoolcar.export.SchoolCarInterface
import com.cyxbs.pages.schoolcar.widget.SchoolCarsSmoothMove
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 *@Author:SnowOwlet
 *@Date:2022/5/6 19:33
 *
 */
const val IS_MAP_SAVED = "isMapSaved"
@KClassProvider(clazz = Activity::class, name = DISCOVER_SCHOOL_CAR)
class SchoolCarActivity: BaseActivity() {
  companion object {
    const val ADD_TIMER: Long = 3
    const val ADD_TIMER_AND_SHOW_MAP: Long = 55
    const val NOT_ADD_TIMER: Long = 0
  }
  //是否显示车辆
  private var showCarIcon = false
  private val vm by viewModels<SchoolCarViewModel>()
  //是否显示定位
  private var ifLocation = true
  //定位client
  private lateinit var locationClient: AMapLocationClient
  //地图
  lateinit var aMap: AMap
  //一个help类
  private var smoothMoveData: SchoolCarsSmoothMove? = null
  private var disposable: Disposable? = null
  private var savedInstanceState:Bundle ?=null
  //是否已经滑动
  var isBeginning = true

  private val schoolCarMvMap by R.id.school_car_mv_map.view<MapView>()
  private val schoolCarCvOut by R.id.school_car_cv_out.view<CardView>()
  private val schoolCarCvExpand by R.id.school_car_cv_expand.view<CardView>()
  private val schoolCarCvPositioning by R.id.school_car_cv_positioning.view<CardView>()
  private val schoolCarIvBack by R.id.school_car_iv_back.view<View>()
  private val schoolCarBts by R.id.school_car_bts.view<View>()
  private val schoolCarCvPositioningIv by R.id.school_car_cv_positioning_iv.view<ImageView>()
  private val schoolCarTvTitleBts by R.id.school_car_tv_title_bts.view<TextView>()
  private val schoolCarTvTimeBts by R.id.school_car_tv_time_bts.view<TextView>()
  private val schoolCarCardRunTypeBts by R.id.school_car_card_run_type_bts.view<TextView>()
  private val schoolCarCardLineTypeBts by R.id.school_car_card_line_type_bts.view<TextView>()
  private val schoolCarSiteRvBts by R.id.school_car_site_rv_bts.view<RecyclerView>()
  private val schoolCarRvBts by R.id.school_car_rv_bts.view<RecyclerView>()
  private val schoolCarCardTvChangeBts by R.id.school_car_card_tv_change_bts.view<TextView>()
  private val schoolCarCardIvChangeBts by R.id.school_car_card_iv_change_bts.view<ImageView>()
  private val schoolCarCardChangeBts by R.id.school_car_card_change_bts.view<View>()
  private val schoolCarCardRunTypeBtsCard by R.id.school_car_card_run_type_bts_card.view<CardView>()
  private val schoolCarCardLineTypeBtsCard by R.id.school_car_card_line_type_bts_card.view<CardView>()

  private var enterTime: Long = 0

  @Override
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.savedInstanceState = savedInstanceState
    //这里是用户协议申明同意
    MapsInitializer.updatePrivacyShow(this, true, true)
    MapsInitializer.updatePrivacyAgree(this, true)

    setContentView(R.layout.schoolcar_activity_schoolcar)

    if (checkActivityPermission()) {
      locationClient = AMapLocationClient(applicationContext)
      schoolCarMvMap.onCreate(savedInstanceState)
      initView()
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    var hasPermissionsDismiss = false

    if (requestCode == 200) {
      grantResults.forEach { grant ->
        if (grant == -1) {
          hasPermissionsDismiss = true
        }
      }
    }

    if (hasPermissionsDismiss) {
      ifLocation = false
    } else {
      locationClient = AMapLocationClient(applicationContext)
      schoolCarMvMap.onCreate(savedInstanceState)
      locationClient = AMapLocationClient(applicationContext)
      initView()
    }
  }

  private fun initView(){
    //放大缩小
    schoolCarCvOut.setOnClickListener {
      val update = CameraUpdateFactory.zoomOut()
      aMap.animateCamera(update)
    }

    schoolCarCvExpand.setOnClickListener {
      val update = CameraUpdateFactory.zoomIn()
      aMap.animateCamera(update)
    }
    //回到自己定位位置
    schoolCarCvPositioning.setOnClickListener {
      vm.chooseCar(-2)
      aMap.myLocation?.let { location->
        location.longitude.let {
          location.latitude.let {
            val update =
              if (aMap.myLocation.longitude != 0.0 || aMap.myLocation.latitude !== 0.0){
                CameraUpdateFactory.newLatLngZoom(LatLng(aMap.myLocation.latitude, aMap.myLocation.longitude), 17f)
              }else{
                CameraUpdateFactory.newLatLngZoom(LatLng(29.531876, 106.606789), 17f)
              }
            aMap.animateCamera(update)
          }
        }
      }
    }
    schoolCarIvBack.setOnClickListener {
      finishAfterTransition()
    }

    aMap = schoolCarMvMap.map
    //如果用户同意定位权限，则开启定位和初始化定位用到的类
    if (ifLocation) {
      initData()
    }
    //初始化地图配置
    initAMap(ifLocation)

    //限制缩放大小
    aMap.minZoomLevel = 15f
    //初始化help类
    smoothMoveData = SchoolCarsSmoothMove(this,vm)

    smoothMoveData!!.setCarMapInterface(object : SchoolCarInterface {
      // 回调是否显示地图，和是否开启一个timer轮询接口
      override fun processLocationInfo(carLocationInfo: ApiWrapper<SchoolCarLocation>, aLong: Long) {
        if (carLocationInfo.data.data.isEmpty()) {
          if (disposable != null) disposable!!.dispose()
          return
        }
        if (aLong == ADD_TIMER) {
          timer("initView")
        }
        if (aLong == ADD_TIMER_AND_SHOW_MAP) {
          if (disposable != null) disposable!!.dispose()       //取消之前所有的轮询订阅
          timer("showCarIcon")

          if (carLocationInfo.status == 200 ||carLocationInfo.status == 10000) {
            initLocationType()
          }
        }
      }
    })
    //进行一次接口数据请求
    smoothMoveData!!.loadCarLocation(ADD_TIMER)

    //完成后开始轮询并且显示地图
    smoothMoveData!!.loadCarLocation(ADD_TIMER_AND_SHOW_MAP)

    initBottomSheetBehavior()
  }
  //这里保证第一次显示最近的
  private fun initBottomSheetBehavior(){
    val behavior = BottomSheetBehavior.from(schoolCarBts)
    behavior.addBottomSheetCallback(object :BottomSheetBehavior.BottomSheetCallback(){
      var cvHeight:Float = 0.0F
      var ivHeight:Float = 0.0F
      var realPath = 100
      var path:Float = 285F
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if(vm.line.value == -1 && newState == BottomSheetBehavior.STATE_DRAGGING && isBeginning){
          vm.showRecently()
        }
      }
      override fun onSlide(bottomSheet: View, slideOffset: Float) {
        if (cvHeight == 0.0F && ivHeight == 0.0F) {
          cvHeight = schoolCarCvPositioning.y
          ivHeight = schoolCarCvPositioningIv.y
          val top = schoolCarCvOut.bottom
          val bottom = schoolCarCvPositioning.top
          realPath = bottom - top
          path = realPath.px2dp - 18F
        }
        schoolCarCvPositioning.y = cvHeight-(path*slideOffset).dp2px
        schoolCarCvPositioningIv.y = ivHeight-(path*slideOffset).dp2px
      }
    })

    vm.bsbState.observe(this){
      when(it){
        0->{
          if (!isBeginning){
            behavior.isDraggable = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
          }
        }
        1->{
          behavior.isDraggable = true
          behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        2->{
          behavior.isDraggable = false
          behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
      }
    }
    var carIconAdapter: CarIconAdapter?= null
    var siteAdapter: CarSiteAdapter?= null

    vm.mapInfo.observe(this){ mapLines ->
      carIconAdapter?: run {
        carIconAdapter = CarIconAdapter(this,mapLines.lines)

        carIconAdapter?.setOnItemListener { position,isIcon ->
          isBeginning = false
          if (isIcon){
            smoothMoveData?.hideCheck()
            siteAdapter?.clear()
          }
          //-1，-2 特殊值
          if(position == -1 || position == -2){
            vm.bsbHide()
            vm.changeLine()
            siteAdapter?.clear()
            if (position == -1 && isIcon){
              startActivity(Intent(this@SchoolCarActivity, SchoolDetailActivity::class.java))
            }
          }else{
            if (isIcon){
              changeSiteView(false)
            }
            if (isIcon){
              schoolCarTvTitleBts.text = mapLines.lines[position].name
            }
            schoolCarTvTimeBts.text = "运行时间: ${mapLines.lines[position].runTime}"
            schoolCarCardRunTypeBts.text = mapLines.lines[position].runType
            schoolCarCardLineTypeBts.text = mapLines.lines[position].sendType
            vm.bsbShow()
            if (isIcon){
              vm.changeLine(mapLines.lines[position].id)
            }
            siteAdapter = CarSiteAdapter(this,mapLines.lines[position].stations,mapLines.lines[position].id)

            schoolCarSiteRvBts.apply {
              this.adapter = siteAdapter
              this.layoutManager = LinearLayoutManager(this@SchoolCarActivity).apply {
                orientation = LinearLayoutManager.HORIZONTAL
              }
            }
            if (!isIcon){
              vm.chooseSite.observe(this){
                vm.mapInfo.value?.lines?.get(position)?.stations?.forEachIndexed { index, station ->
                  if (station.id == it){
                    if (vm.line.value != -1) {
                      goneSiteView()
                    }
                      siteAdapter?.choose(index)
                  }
                }
              }
            }
          }
        }
        schoolCarRvBts.apply {
          adapter = carIconAdapter
          layoutManager = LinearLayoutManager(this@SchoolCarActivity).apply {
            orientation = LinearLayoutManager.HORIZONTAL
          }
        }
      }
    }

    vm.carLine.observe(this){ line ->
      if (line == -2){
        carIconAdapter?.let { adapter ->
          adapter.clear()
        }
      }else{
        carIconAdapter?.let { adapter ->
          adapter.choose(line,true)
        }
      }
      siteAdapter?.clear()
    }

    vm.chooseSite.observe(this){
      vm.mapInfo.value?.lines?.forEach { line ->
        line.stations.forEachIndexed { index, station ->
          if (station.id == it){
            schoolCarTvTitleBts.text = station.name
          }
        }
      }
    }

    vm.mapLine.observe(this){ arrays ->
      var i = 0
      if (arrays.size < 1) return@observe
      if (arrays.size == 1){
        vm.mapInfo.value?.lines?.forEach { line ->
          if (line.id == arrays[0]) {
            schoolCarCardTvChangeBts.text = line.name
            schoolCarCardTvChangeBts.setTextColor(resources.getColor(com.cyxbs.components.config.R.color.config_level_one_font_color))
            schoolCarCardIvChangeBts.setImageResource(R.drawable.schoolcar_bts_btn_change)
            schoolCarCardChangeBts.setBackgroundResource(R.drawable.schoolcar_bts_btn_change_shape)
          }
        }
          vm.bsbShow()
        changeSiteView()
        carIconAdapter?.let { adapter ->
          if (vm.line.value != -1){
            adapter.choose(arrays[0],isShowIcon = true)
          }else{
            adapter.choose(arrays[0])
          }
        }
        schoolCarCardChangeBts.isClickable = false
        return@observe
      }

      schoolCarCardChangeBts.isClickable = true

      var choose = arrays[i]
      if (i < arrays.size-1) i++ else i = 0
      var next = arrays[i]
      vm.mapInfo.value?.lines?.forEach { line ->
        if (line.id == choose) {
            schoolCarCardTvChangeBts.text = line.name
            schoolCarCardTvChangeBts.setTextColor(0xffffff)
            schoolCarCardChangeBts.setBackgroundResource(R.drawable.schoolcar_bts_btn_change_shape_select)
            schoolCarCardIvChangeBts.setImageResource(R.drawable.schoolcar_bts_btn_change_select)
        }
      }
      vm.bsbShow()
      changeSiteView()
      schoolCarCardChangeBts.setOnClickListener {
        vm.mapInfo.value?.lines?.forEach { line ->
          if (line.id == next) {
            schoolCarCardTvChangeBts.text = line.name
            carIconAdapter?.let { adapter ->
              adapter.choose(choose)
            }
          }
        }
        if (i < arrays.size-1) i++ else i = 0
        choose = next
        next = arrays[i]
      }
      carIconAdapter?.let { adapter ->
        if (vm.line.value != -1){
          adapter.choose(choose,isShowIcon = true)
        }else{
          adapter.choose(choose)
        }
      }
      vm.bsbShow()
    }

    vm.initMapInfo()
  }


  private fun initAMap(ifLocation: Boolean) {
    if (ifLocation) {
      aMap.isMyLocationEnabled = true
      aMap.myLocationStyle = initLocationType()
    }

    //加载地图材质包
    aMap.uiSettings.isZoomControlsEnabled = false
    val parent = File(filesDir , "/maoXhMap")

    if(!defaultSp.getBoolean(IS_MAP_SAVED, false)) MapStyleHelper(this).saveMapStyle{
      initAMap(ifLocation)
    }
    val styleExtra = File(parent, "style_extra.data")
    val style = File(parent, "style.data")
    val customMapStyleOptions = CustomMapStyleOptions()
    customMapStyleOptions.apply {
      isEnable = true
      styleDataPath = style.absolutePath
      styleExtraPath = styleExtra.absolutePath
    }
    aMap.setCustomMapStyle(customMapStyleOptions)
    aMap.uiSettings.isMyLocationButtonEnabled = false
    //地图开始时显示的中心和缩放大小
    val update = CameraUpdateFactory.newLatLngZoom(LatLng(29.531876, 106.606789), 17f)
    aMap.animateCamera(update)
  }

  private fun initData() {
    val locationListener = AMapLocationListener {
      if (!showCarIcon) {
          if (!vm.showRecently.hasObservers()){
            vm.showRecently.observe(this){ i->
              if (isBeginning && i != 0){
                vm.recentlySite(it.latitude,it.longitude)
              }
            }
          }
        val myDistance = AMapUtils.calculateLineDistance(LatLng(29.531876, 106.606789), LatLng(it.latitude, it.longitude))
        if (myDistance > 1300) {
          aMap.isMyLocationEnabled = false
        }
      }
    }

    locationClient = AMapLocationClient(application)
    val locationClientOption = AMapLocationClientOption()
    locationClient.setLocationOption(locationClientOption)
    locationClient.setLocationListener(locationListener)
    locationClient.startLocation()
  }

  private fun changeSiteView(show:Boolean = true){
      if (show){
        schoolCarCardChangeBts.visibility = View.VISIBLE
        schoolCarCardLineTypeBts.visibility = View.INVISIBLE
        schoolCarCardRunTypeBts.visibility = View.INVISIBLE
        schoolCarCardRunTypeBtsCard.visibility = View.INVISIBLE
        schoolCarCardLineTypeBtsCard.visibility = View.INVISIBLE
        schoolCarTvTimeBts.visibility = View.INVISIBLE
      }else{
        schoolCarCardChangeBts.visibility = View.GONE
        schoolCarCardLineTypeBts.visibility = View.VISIBLE
        schoolCarCardRunTypeBts.visibility = View.VISIBLE
        schoolCarCardRunTypeBtsCard.visibility = View.VISIBLE
        schoolCarCardLineTypeBtsCard.visibility = View.VISIBLE
        schoolCarTvTimeBts.visibility = View.VISIBLE
      }
  }

  private fun goneSiteView(){
    schoolCarCardChangeBts.visibility = View.GONE
    schoolCarCardLineTypeBts.visibility = View.INVISIBLE
    schoolCarCardRunTypeBts.visibility = View.INVISIBLE
    schoolCarCardRunTypeBtsCard.visibility = View.INVISIBLE
    schoolCarCardLineTypeBtsCard.visibility = View.INVISIBLE
    schoolCarTvTimeBts.visibility = View.INVISIBLE
  }

  private fun timer(name: String) {
    disposable?.dispose()
    disposable = Observable.interval(1, TimeUnit.SECONDS)
      .doOnNext {
        smoothMoveData!!.loadCarLocation(NOT_ADD_TIMER)
      }.observeOn(AndroidSchedulers.mainThread()).subscribe()
  }

  //权限检查
  private fun checkActivityPermission(): Boolean {
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        200
      )
    }
    return ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  //得到自己定位图标样式
  private fun initLocationType(): MyLocationStyle = MyLocationStyle().apply {
    val descriptor: BitmapDescriptor =
      BitmapDescriptorFactory.fromResource(R.drawable.schoolcar_ic_my)
    interval(2000)
    strokeWidth(0f)
    radiusFillColor(Color.alpha(0))
    myLocationIcon(descriptor)
    myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW_NO_CENTER)
  }


  override fun onDestroy() {
    super.onDestroy()

    if (checkActivityPermission()) {
      locationClient.onDestroy()
    }
    schoolCarMvMap.onDestroy()
    if (disposable != null) {
      disposable!!.dispose()
    }
    smoothMoveData?.let {
      disposable?.let { if (!it.isDisposed) it.dispose() }
    }
  }

  override fun onPause() {
    super.onPause()
    schoolCarMvMap.onPause()
    if (smoothMoveData != null) {
      smoothMoveData!!.clearAllList()
    }
    val exitTime = System.currentTimeMillis()
    val duration = exitTime - enterTime
    appCoroutineScope.launch {
      TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_XCGJ,duration,enterTime)
    }
  }

  override fun onResume() {
    super.onResume()
    schoolCarMvMap.onResume()

    vm.bsbHide(isBeginning)
    if (smoothMoveData != null) {
      smoothMoveData!!.clearAllList()
      smoothMoveData!!.loadCarLocation(ADD_TIMER_AND_SHOW_MAP)
    }
    enterTime = System.currentTimeMillis()
  }

  override fun onStop() {
    super.onStop()
    if (smoothMoveData != null) {
      smoothMoveData!!.clearAllList()
    }
  }
}