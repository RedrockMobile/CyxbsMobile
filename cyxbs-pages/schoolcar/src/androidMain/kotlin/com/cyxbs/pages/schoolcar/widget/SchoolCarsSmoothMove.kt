package com.cyxbs.pages.schoolcar.widget

import android.graphics.*
import android.util.ArrayMap
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.amap.api.maps.model.animation.Animation
import com.amap.api.maps.model.animation.ScaleAnimation
import com.amap.api.maps.utils.overlay.MovingPointOverlay
import com.cyxbs.pages.schoolcar.export.SchoolCarInterface
import com.cyxbs.pages.schoolcar.SchoolCarActivity
import com.cyxbs.pages.schoolcar.SchoolCarViewModel
import com.cyxbs.pages.schoolcar.bean.SchoolCar
import com.cyxbs.pages.schoolcar.bean.Station
import com.cyxbs.pages.schoolcar.network.ApiService
import com.cyxbs.pages.schoolcar.R
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.network.ApiGenerator
import io.reactivex.rxjava3.disposables.Disposable
import okio.ByteString
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.math.abs


/**
 * Created by glossimar on 2018/9/12
 */

class SchoolCarsSmoothMove(
  private val schoolCarActivity: SchoolCarActivity?,
  private val vm: SchoolCarViewModel
){
  private lateinit var carInterface: SchoolCarInterface

  //key:id value:type
  private val carMap : ArrayMap<Int, SchoolCar> = ArrayMap()
  private val siteMap : ArrayMap<Int,List<Marker>> = ArrayMap()
  //markerId to site
  private val markerToSite : ArrayMap<String, Station> = ArrayMap()
  private val markerToCar : ArrayMap<String,Int> = ArrayMap()
  private val lineToBackGroundMarker : ArrayMap<Int,Marker> = ArrayMap()

  private val bitmapChanges: List<Bitmap> = getSmoothMakerBitmaps()
  private val siteChanges: List<Bitmap> = getSiteMakerBitmaps()
  private val markerBackGround: List<Bitmap> = getMarkerBackGroundBitmaps()

  private val apiService = ApiGenerator.getApiService(ApiService::class.java)
  private var lastPostTime = System.currentTimeMillis()

  private var chooseMarker:Marker ?= null
  var disposable: Disposable? = null
  private var line = -1
  init {
    //这里是站点or车外面的圈圈显示
    buildBackGrounds()
    schoolCarActivity?.aMap?.setOnMapClickListener {
      chooseMarker?.hideInfoWindow()
    }
    schoolCarActivity?.aMap?.setOnMarkerClickListener {
      chooseMarker = it
      if (markerToCar.contains(it.id)) {
        hideCheck()
        markerToCar[it.id]?.let { car ->
          carMap[car]?.apply {
            vm.chooseCar(this.type)
            smoothBackgroundMarker.setVisible(true)
            backgroundMarker.isVisible = true
            backgroundMarker.setAnimation(getAnimation())
            backgroundMarker.startAnimation()
          }
        }
      }
      if (markerToSite.contains(it.id)){
        hideCheck()
        markerToSite[it.id]?.id?.let { site -> vm.chooseSite(site) }
        val index = if(line==-1) 0 else line+1
        lineToBackGroundMarker[index]?.apply {
          position = LatLng(it.position.latitude,it.position.longitude)
          isVisible = true
          setAnimation(getAnimation())
          startAnimation()
        }
      }
      it.showInfoWindow()
      val camera = CameraUpdateFactory.newCameraPosition(CameraPosition(LatLng(it.position.latitude,it.position.longitude), 18F,0F,0F))
      schoolCarActivity.aMap.animateCamera(camera)
      return@setOnMarkerClickListener true
    }

    schoolCarActivity?.let { schoolCarActivity ->
      //初始化
      vm.line.observe(schoolCarActivity){
        line = it
        if (siteMap.contains(line) && line != -1) {
          siteMap.iterator().forEach { map ->
            if (map.key == line){
              siteMap[line]?.forEach { marker ->
                marker.setIcon(BitmapDescriptorFactory.fromBitmap(getSiteMakerBitmaps()[line+1]))
                marker.isVisible = true
              }
            }else{
              map.value.forEach { marker ->
                marker.isVisible = false
              }
            }
          }
        }else{
          val repetitionList = ArrayList<LatLng>()
          siteMap.iterator().forEach { map ->
            map.value.forEach { marker ->
              if (!repetitionList.contains(marker.position)) {
                repetitionList.add(marker.position)
                marker.setIcon(BitmapDescriptorFactory.fromBitmap(getSiteMakerBitmaps()[0]))
                marker.isVisible = true
              }else{
                marker.isVisible = false
              }
            }
          }
        }

        carMap.iterator().forEach { map ->
          if (line == -1){
            map.value.marker.isVisible = true
          }else{
            map.value.marker.isVisible = map.value.type == line
          }
        }
      }
      //选择路线时marker
      vm.mapInfo.observe(schoolCarActivity){ mapLines ->
        val repetitionList = ArrayList<LatLng>()
        mapLines.lines.forEach { lineInfo ->
          if (!siteMap.contains(lineInfo.id)) {
            siteMap[lineInfo.id] = buildList {
              lineInfo.stations.forEach { station ->
                val marker = buildSite(station.name,lineInfo.id, LatLng(station.lat,station.lng))
                marker?.let {
                  markerToSite[marker.id] = station
                  add(it)
                }
                if (repetitionList.contains(marker?.position)) {
                  marker?.isVisible = false
                }else{
                  marker?.position?.let { repetitionList.add(it) }
                }
              }
            }
          }
        }
      }
      //选择站点时marker显示
      vm.chooseSite.observe(schoolCarActivity){ id ->
        if (schoolCarActivity.isBeginning){
          schoolCarActivity.isBeginning = false
          markerToSite.iterator().forEach { markers ->
            if(markers.value.id == id){
              siteMap.iterator().forEach {
                it.value.forEach { marker ->
                  if (marker.id == markers.key){
                    val index = if(line==-1) 0 else line+1
                    lineToBackGroundMarker[index]?.apply {
                      position = LatLng(marker.position.latitude,marker.position.longitude)
                      isVisible = true
                      setAnimation(getAnimation())
                      startAnimation()
                    }
                    marker.showInfoWindow()
                    val camera = CameraUpdateFactory.newCameraPosition(CameraPosition(LatLng(marker.position.latitude,marker.position.longitude), 18F,0F,0F))
                    schoolCarActivity.aMap.animateCamera(camera)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  /**
   * 进行接口数据请求
   */
  fun loadCarLocation(ifAddTimer: Long) {
    disposable = apiService.schoolcar("Redrock",
      md5Hex(System.currentTimeMillis().toString().substring(0, 10) + "." + "Redrock"),
      System.currentTimeMillis().toString().substring(0, 10),
      md5Hex((System.currentTimeMillis() - 1).toString().substring(0, 10)))
      .setSchedulers()
      .unsafeSubscribeBy(
        onNext =  {
          lastPostTime = System.currentTimeMillis()
          //如果所有的数据都没有更新
          it.data.data.forEach {  data ->
            if(!carMap.contains(data.id)){
              val latLng = LatLng(data.lat,data.lon)
              val marker = buildMarker(data.id,data.type,latLng)
              val backgroundMarker = buildBackGround(data.type,data.lat,data.lon)
              marker?.let { marker ->
                backgroundMarker?.let {
                  markerToCar[marker.id] = data.id
                  val smoothMarker = MovingPointOverlay(schoolCarActivity?.aMap,marker)
                  val smoothBackgroundMarker = MovingPointOverlay(schoolCarActivity?.aMap,lineToBackGroundMarker[data.type])
                  carMap[data.id] = SchoolCar(data.id,data.type,data.upDate,data.upDate,smoothMarker,smoothBackgroundMarker,marker,backgroundMarker,mutableListOf(latLng,smoothMarker.position),getPolyline(data.type,latLng))
                }
              }
            }else{
              //这里因为轮询是1s一次，所以说判断一下更新的时间，
              if(carMap[data.id]?.upDate != data.upDate){
                carMap[data.id]?.upDate?.let { upDate ->
                  //将两次更新的时间差作为移动的时间
                  var mistime = upDate - data.upDate
                  carMap[data.id]?.upDate = data.upDate
                  carMap[data.id]?.latLngList?.add(LatLng(data.lat,data.lon))
                  mistime = abs(mistime)
                  mistime = if(mistime < 3) 3 else if( mistime > 15) 15 else mistime
                  carMap[data.id]?.let { car -> smoothMove(car,abs(mistime.toInt())) }
                }
              }
            }
          }
          carInterface.processLocationInfo(it, ifAddTimer)
        }
      )
  }

//    /**
//     * 在地图上绘出校车轨迹
//     */
//    private fun drawTraceLine(car: SchoolCar) {
//        car.polyline?.points = car.latLngList
//    }

  fun hideCheck(){
    lineToBackGroundMarker.iterator().forEach {
      it.value.isVisible = false
    }
    carMap.iterator().forEach {
      it.value.backgroundMarker.isVisible = false
      it.value.smoothBackgroundMarker.setVisible(false)
    }
  }
  //move设置
  private fun smoothMove(car: SchoolCar, mistime:Int = 3) {
    if (line != -1 && car.type != line) return
    if (car.latLngList.size > 3) {
      //停止上一次的移动，然后将上一次的位置加上之后再移动
      car.smoothMarker.stopMove()
      car.smoothBackgroundMarker.stopMove()
      val list = car.latLngList.subList(car.latLngList.size - 2, car.latLngList.size)
      list[0] = car.smoothMarker.position
      val smoothMarker = MovingPointOverlay(schoolCarActivity?.aMap,car.marker)
      val backgroundMoveMarker = MovingPointOverlay(schoolCarActivity?.aMap,car.backgroundMarker)
      smoothMarker.setPoints(list)
      backgroundMoveMarker.setPoints(list)
      smoothMarker.setTotalDuration(mistime)
      backgroundMoveMarker.setTotalDuration(mistime)
      smoothMarker.startSmoothMove()
      backgroundMoveMarker.startSmoothMove()
//            drawTraceLine(car)
      car.smoothMarker = smoothMarker
      car.smoothBackgroundMarker = backgroundMoveMarker
    }
    //如果线路过长就删除一些
    if (car.latLngList.size > 8){
      car.latLngList.removeFirstOrNull()
      car.latLngList.removeFirstOrNull()
    }
  }

  fun setCarMapInterface(carMapInterface: SchoolCarInterface) {
    this.carInterface = carMapInterface
  }

  private fun buildMarker(id:Int,type:Int,latLng:LatLng): Marker? {
    val markerOption = MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(bitmapChanges[type]))
    markerOption
//      .title("${id}号车")
//      .snippet("${type+1}号线路")
      .anchor(0.5f,0.5f)
      .position(latLng)
    return schoolCarActivity?.aMap?.addMarker(markerOption)
  }

  private fun buildSite(name:String,type:Int,latLng:LatLng): Marker? {
    val markerOption = MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(siteChanges[0]))
    markerOption
//      .title(name)
//      .snippet("${type+1}号线路")
      .anchor(0.5f,0.5f)
      .position(latLng)
    return schoolCarActivity?.aMap?.addMarker(markerOption)
  }

  fun clearAllList() {
    carMap.iterator().forEach {
      it.value.marker.remove()
      it.value.polyline?.remove()
    }
    carMap.clear()

  }

  private fun md5Hex(s: String): String {
    try {
      val messageDigest = MessageDigest.getInstance("MD5")
      val md5bytes = messageDigest.digest(s.toByteArray(charset("UTF-8")))
      return ByteString.of(*md5bytes).hex()
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: UnsupportedEncodingException) {
      throw AssertionError(e)
    }
  }

  //获得车辆的图标样式
  private fun getSmoothMakerBitmaps():List<Bitmap>{

    return buildList {
      schoolCarActivity?.let {
        add(getBitmapBySvg(R.drawable.schoolcar_ic_car_1))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_car_2))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_car_3))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_car_4))
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.mipmap.schoolcar_ic_school_car))
      }
    }
  }

  //获得站点的图标样式
  private fun getSiteMakerBitmaps():List<Bitmap>{
    return buildList {
      schoolCarActivity?.let {
        add(getBitmapBySvg(R.drawable.schoolcar_ic_site_0))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_site_1))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_site_2))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_site_3))
        add(getBitmapBySvg(R.drawable.schoolcar_ic_site_4))
      }
    }
  }


  private fun buildBackGrounds(){
    for (i in 0..4){
      lineToBackGroundMarker[i] = buildBackGround(line)
    }
  }

  private fun buildBackGround(line:Int,lat:Double =29.531876 ,lng:Double = 106.606789):Marker?{
    val index = if(line == -1) 0 else line+1
    val markerOption = MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(markerBackGround[index]))
    markerOption
      .position(LatLng(lat,lng))
      .anchor(0.5f,0.5f)
    val marker = schoolCarActivity?.aMap?.addMarker(markerOption)
    marker?.apply {
      isClickable = false
      isVisible = false
    }
    return marker
  }

  private fun getMarkerBackGroundBitmaps():List<Bitmap>{
    return buildList {
      schoolCarActivity?.let {
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.drawable.schoolcar_ic_background_0))
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.drawable.schoolcar_ic_background_1))
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.drawable.schoolcar_ic_background_2))
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.drawable.schoolcar_ic_background_3))
        add(BitmapFactory.decodeResource(schoolCarActivity.resources, R.drawable.schoolcar_ic_background_4))
      }
    }
  }

  private fun getBitmapBySvg(@DrawableRes id : Int):Bitmap{
    schoolCarActivity?.apply {
      val vectorDrawable = resources.getDrawable(id,null)
      val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
      vectorDrawable.draw(canvas)
      return bitmap
    }
    return Bitmap.createBitmap(0,0,Bitmap.Config.ARGB_8888)
  }

  //获得路径颜色
  private fun getPolyline(type: Int, latLng: LatLng): Polyline? {
    return when(type){
      0->{
        schoolCarActivity?.aMap?.addPolyline(PolylineOptions()
          .setDottedLine(true)
          .width(8f)
          .color(Color.argb(255, 93, 152, 255))
          .add(latLng))
      }
      1->{
        schoolCarActivity?.aMap?.addPolyline(PolylineOptions()
          .setDottedLine(true)
          .width(8f)
          .color(Color.argb(255, 93, 152, 255))
          .add(latLng))
      }
      2->{
        schoolCarActivity?.aMap?.addPolyline(PolylineOptions()
          .setDottedLine(true)
          .width(8f)
          .color(Color.argb(255, 93, 152, 255))
          .add(latLng))
      }
      3->{
        schoolCarActivity?.aMap?.addPolyline(PolylineOptions()
          .setDottedLine(true)
          .width(8f)
          .color(Color.argb(255, 93, 152, 255))
          .add(latLng))
      }
      else ->{
        schoolCarActivity?.aMap?.addPolyline(PolylineOptions()
          .setDottedLine(true)
          .width(8f)
          .color(Color.argb(255, 93, 152, 255))
          .add(latLng))
      }
    }
  }

  private fun getAnimation() = ScaleAnimation(0.8F,1F,0.8F,1F).apply {
    setInterpolator(LinearInterpolator())
    setDuration(3000)
    repeatMode = Animation.REVERSE
    repeatCount = Animation.INFINITE
  }
}


