package com.cyxbs.pages.map.viewmodel

import android.content.Intent
import androidx.core.net.toUri
import com.cyxbs.components.init.MainNavController
import java.net.URLEncoder

actual class MapComposeViewModel : CommonMapComposeViewModel() {

  override fun jumpToNavigation(endPlace: String) {
    try {
      val uri = ("baidumap://map/direction?" +
          "destination=name:$endPlace" +
          "&mode=walking").toUri()
      val intent = Intent(Intent.ACTION_VIEW, uri)
      MainNavController.context.startActivity(intent)
    }catch (e: Exception){
      // 未安装百度地图App，跳转网页版
      // 对终点进行URL编码，处理特殊字符
      val encodedEndPlace = URLEncoder.encode(endPlace, "UTF-8")
      val webUrl = "http://api.map.baidu.com/geocoder?address=$encodedEndPlace&output=html&src=webapp.baidu.openAPIdemo"
      val browserIntent = Intent(Intent.ACTION_VIEW, webUrl.toUri())
      MainNavController.context.startActivity(browserIntent)
    }
  }

}