package com.cyxbs.pages.map.model

import com.cyxbs.components.utils.extensions.defaultGson
import com.google.gson.reflect.TypeToken
import com.cyxbs.pages.map.bean.ButtonInfo
import com.cyxbs.pages.map.bean.FavoritePlace
import com.cyxbs.pages.map.bean.MapInfo
import com.cyxbs.pages.map.bean.PlaceDetails
import com.cyxbs.components.utils.network.ApiWrapper
import io.reactivex.rxjava3.core.Observable

/**
 *@author zhangzhe
 *@date 2020/8/11
 *@description 生成测试数据，所有测试数据应写在这里
 */


object TestData {
    /**
     * 地图基本信息接口
     * 目前只返回 中心食堂id2 和 腾飞门id1
     */
    private val gson = defaultGson
    fun getMapInfo() = Observable.create<ApiWrapper<MapInfo>> {
        it.onNext(gson.fromJson(mapInfoString, object : TypeToken<ApiWrapper<MapInfo>>() {}.type))
    }

    /**
     * 筛选接口
     * @param code 筛选条件
     * @return 返回list装int，符合条件的地点id
     * 现在暂时返回[1,2]，因为“地图基本信息”接口只返回了两个地点，测试用
     */
    fun getScreen(code: String) = Observable.create<ApiWrapper<List<Int>>> {
        it.onNext(gson.fromJson(screenString, object : TypeToken<ApiWrapper<List<Int>>>() {}.type))
    }

    /**
     * 获得收藏接口，{nickname：最爱去的食堂 id：2}  和  {nickname：漂亮的新校门 id：1}
     */
    fun getCollect() = Observable.create<ApiWrapper<List<FavoritePlace>>> {
        it.onNext(gson.fromJson(favoriteString, object : TypeToken<ApiWrapper<List<FavoritePlace>>>() {}.type))
    }

    /**
     * 地点详细接口
     * 目前只返回 中心食堂id2 和 腾飞门id1
     * @param placeId 返回对应地点的地点详细
     */
    fun getPlaceDetails(placeId: Int): Observable<ApiWrapper<PlaceDetails>> {
        var s = ""
        when (placeId) {
            29 -> {
                s = place1DetailsString
            }
            73 -> {
                s = place2DetailsString
            }
            54 -> {
                s = place3DetailsString
            }
        }
        return Observable.create<ApiWrapper<PlaceDetails>> { em ->
            em.onNext(gson.fromJson(s, object : TypeToken<ApiWrapper<PlaceDetails>>() {}.type))
        }
    }

    fun getButtonInfo(): Observable<ApiWrapper<ButtonInfo>> {
        return Observable.create<ApiWrapper<ButtonInfo>> { em ->
            em.onNext(gson.fromJson(buttonInfoString, object : TypeToken<ApiWrapper<ButtonInfo>>() {}.type))
        }
    }

}

/**
 * 接口的字符串
 */

const val buttonInfoString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"id\":0,\n" +
                "    \"data\":\n" +
                "    {\n" +
                "        \"button_info\":\n" +
                "        [\n" +
                "            {\n" +
                "                \"title\":\"新生报到处\",\n" +
                "                \"code\":\"新生报到点\",\n" +
                "                \"is_hot\":true\n" +
                "            },\n" +
                "            {\n" +
                "                \"title\":\"食堂\",\n" +
                "                \"code\":\"食堂\",\n" +
                "                \"is_hot\":false\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}"

//id = 29 的地点信息字符串
const val place1DetailsString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"id\":29,\n" +
                "    \"data\":\n" +
                "    {\n" +
                "        \"place_name\":\"腾飞门\",\n" +
                "        \"place_attribute\":\n" +
                "        [\n" +
                "            \"校门\",\n" +
                "            \"标签\",\n" +
                "            \"标签\",\n" +
                "            \"标签\",\n" +
                "            \"标签\",\n" +
                "            \"标签\",\n" +
                "            \"标签\",\n" +
                "            \"标签\"\n" +
                "        ],\n" +
                "        \"tags\":\n" +
                "        [\n" +
                "            \"新生报到\",\n" +
                "            \"开门时间：6:00-23:00\"\n" +
                "        ],\n" +
                "        \"images\":\n" +
                "        [\n" +
                "            \"https://ss3.bdstatic.com/70cFv8Sh_Q1YnxGkpoWK1HF6hhy/it/u=3271944503,17290708&fm=26&gp=0.jpg\",\n" +
                "            \"https://ss3.bdstatic.com/70cFv8Sh_Q1YnxGkpoWK1HF6hhy/it/u=3271944503,17290708&fm=26&gp=0.jpg\",\n" +
                "            \"https://ss3.bdstatic.com/70cFv8Sh_Q1YnxGkpoWK1HF6hhy/it/u=3271944503,17290708&fm=26&gp=0.jpg\"\n" +
                "        ]\n" +
                "    }\n" +
                "}"

//id = 73 的地点信息字符串
const val place2DetailsString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"id\":73,\n" +
                "    \"data\":\n" +
                "    {\n" +
                "        \"place_name\":\"中心食堂\",\n" +
                "        \"place_attribute\":\n" +
                "        [\n" +
                "            \"食堂\",\n" +
                "            \"标签\"\n" +
                "        ],\n" +
                "        \"is_collected\":true,\n" +
                "        \"tags\":\n" +
                "        [\n" +
                "           \"芜湖\",\n" +
                "           \"芜湖\",\n" +
                "           \"芜湖\",\n" +
                "           \"芜湖\",\n" +
                "           \"芜湖\",\n" +
                "            \"新生报到\",\n" +
                "           \"大司音乐食堂\",\n" +
                "           \"芜湖\",\n" +
                "            \"开饭时间：7:00-20:00\"\n" +
                "        ],\n" +
                "        \"images\":\n" +
                "        [\n" +
                "            \"https://ss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=982417989,4270285044&fm=26&gp=0.jpg\",\n" +
                "            \"https://ss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=3549813042,1783346451&fm=26&gp=0.jpg\",\n" +
                "            \"https://ss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=591674105,1532042351&fm=15&gp=0.jpg\"\n" +
                "        ]\n" +
                "    }\n" +
                "}"

const val place3DetailsString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"id\":54,\n" +
                "    \"data\":\n" +
                "    {\n" +
                "        \"place_name\":\"第二教学楼/计算机工程与技术学院/软件工程学院\",\n" +
                "        \"place_attribute\":\n" +
                "        [\n" +
                "            \"教学楼\",\n" +
                "            \"标签\"\n" +
                "        ],\n" +
                "        \"is_collected\":true,\n" +
                "        \"tags\":\n" +
                "        [\n" +
                "           \"二教\",\n" +
                "           \"离宿舍近\",\n" +
                "            \"开放时间：7:00-20:00\"\n" +
                "        ],\n" +
                "        \"images\":\n" +
                "        [\n" +
                "            \"https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597558537136&di=a9eb1538ffa41ab3d6eb1b0c2a7784b0&imgtype=0&src=http%3A%2F%2Fres.co188.com%2Fdata%2Fdrawing%2Fimg640%2F6227336063520.jpg%3Fm%3Db\",\n" +
                "            \"https://ss1.bdstatic.com/70cFuXSh_Q1YnxGkpoWK1HF6hhy/it/u=3549813042,1783346451&fm=26&gp=0.jpg\",\n" +
                "            \"https://ss0.bdstatic.com/70cFuHSh_Q1YnxGkpoWK1HF6hhy/it/u=591674105,1532042351&fm=15&gp=0.jpg\"\n" +
                "        ]\n" +
                "    }\n" +
                "}"

const val favoriteString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"id\":0,\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"data\":\n" +
                "    [\n" +
                "        {\n" +
                "            \"place_nickname\":\"最爱去的食堂\",\n" +
                "            \"place_id\":2\n" +
                "        },\n" +
                "        {\n" +
                "            \"place_nickname\":\"漂亮的新校门\",\n" +
                "            \"place_id\":1\n" +
                "        }\n" +
                "    ]\n" +
                "}"

const val screenString =
        "{\n" +
                "    \"status\":200,\n" +
                "    \"info\":\"success\",\n" +
                "    \"id\":0,\n" +
                "    \"version\":\"1.0\",\n" +
                "    \"data\":\n" +
                "    [\n" +
                "        1,2\n" +
                "    ]\n" +
                "}"

const val mapInfoString = "{\n" +
        "    \"status\":200,\n" +
        "    \"info\":\"success\",\n" +
        "    \"version\":\"1\"," +
        "    \"id\":0," +
        "    \"data\":" +
        "       {\n" +
        "        \"hot_word\": \"腾飞门\",\n" +
        "        \"place_list\": [\n" +
        "            {\n" +
        "                \"place_name\": \"重邮腾飞门\",\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        //
        "            {\n" +
        "                \"place_name\": \"重邮腾飞门1\",\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        "            {\n" +
        "                \"place_name\": \"重邮腾飞门12\",\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        "            {\n" +
        "                \"place_name\": \"重邮腾飞门123\",\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        "            {\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        "            {\n" +
        "                \"place_id\": 29,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 1396,\n" +
        "                        \"building_right\": 1990,\n" +
        "                        \"building_top\": 9154,\n" +
        "                        \"building_bottom\": 9384\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 1734,\n" +
        "                \"place_center_y\": 9372,\n" +
        "                \"tag_left\": 1918,\n" +
        "                \"tag_right\": 2172,\n" +
        "                \"tag_top\": 9310,\n" +
        "                \"tag_bottom\": 9428\n" +
        "            },\n" +
        "            {\n" +
        "                \"place_name\": \"第二教学楼/计算机工程与技术学院/软件工程学院\",\n" +
        "                \"place_id\": 54,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 2908,\n" +
        "                        \"building_right\": 3384,\n" +
        "                        \"building_top\": 8328,\n" +
        "                        \"building_bottom\": 8554\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 2868,\n" +
        "                \"place_center_y\": 8502,\n" +
        "                \"tag_left\": 2494,\n" +
        "                \"tag_right\": 2984,\n" +
        "                \"tag_top\": 8340,\n" +
        "                \"tag_bottom\": 8504\n" +
        "            },\n" +
        //
        "            {\n" +
        "                \"place_name\": \"中心食堂\",\n" +
        "                \"place_id\": 73,\n" +
        "                \"building_list\": [\n" +
        "                    {\n" +
        "                        \"building_left\": 4002,\n" +
        "                        \"building_right\": 4292,\n" +
        "                        \"building_top\": 7840,\n" +
        "                        \"building_bottom\": 7960\n" +
        "                    }\n" +
        "                ],\n" +
        "                \"place_center_x\": 4074,\n" +
        "                \"place_center_y\": 7904,\n" +
        "                \"tag_left\": 3676,\n" +
        "                \"tag_right\": 3840,\n" +
        "                \"tag_top\": 7852,\n" +
        "                \"tag_bottom\": 7930\n" +
        "            }" +
        "        ],\n" +
        "        \"map_url\": \"./xxxxx/xxxx/xxx.png\",\n" +
        "        \"map_width\": 7447,\n" +
        "        \"map_height\": 13255,\n" +
        "        \"map_background_color\": \"#96ECBB\"\n" +
        "       }" +
        "}"
