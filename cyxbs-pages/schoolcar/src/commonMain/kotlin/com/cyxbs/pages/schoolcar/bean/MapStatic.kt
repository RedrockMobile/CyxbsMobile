package com.cyxbs.pages.schoolcar.bean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**  
 * description ： TODO:类的作用
 * author : HI-IR
 * email : qq2420226433@outlook.com
 *  * date : 2026/3/18 18:15
 *  */

@Serializable
data class MapStatic(
    @SerialName("map_background_color")
    val mapBackgroundColor: String,
    @SerialName("map_heigth")
    val mapHeight: Int,
    @SerialName("map_url")
    val mapUrl: String,
    @SerialName("map_width")
    val mapWidth: Int
)
