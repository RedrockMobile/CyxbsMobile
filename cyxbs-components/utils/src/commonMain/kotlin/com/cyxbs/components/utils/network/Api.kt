package com.cyxbs.components.utils.network

import com.cyxbs.components.config.isDebug

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/29 22:33
 */

//6.1.0版本之后后端环境更新，baseUrl分为测试环境和线上环境
/*
* 这个 dev 不建议修改，因为后端他们是先推到 dev，再推到 prod，即使修改大部分情况下也是如此，
* 所以 dev 与 prod 在数据结构上会基本保持一致
* */
const val END_POINT_REDROCK_DEV = "https://be-dev.redrock.cqupt.edu.cn"//测试环境url
const val END_POINT_REDROCK_PROD = "https://be-prod.redrock.cqupt.edu.cn"//线上环境url
const val BASE_NORMAL_IMG_URL = "$END_POINT_REDROCK_PROD/app/Public/photo/"

const val BASE_THUMBNAIL_IMG_URL = BASE_NORMAL_IMG_URL + "thumbnail/"


const val BASE_NORMAL_BACKUP_GET = "https://be-prod.redrock.team/cloud-manager/check"

//获取baseUrl的方法
fun getBaseUrl() = if (isDebug()) END_POINT_REDROCK_DEV else END_POINT_REDROCK_PROD
//fun getBaseUrl() = END_POINT_REDROCK_PROD