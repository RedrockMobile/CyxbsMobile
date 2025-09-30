package com.cyxbs.pages.store.utils

import android.content.Context
import com.cyxbs.components.config.route.DECLARE_ENTRY
import com.cyxbs.components.config.route.DISCOVER_NO_CLASS
import com.cyxbs.components.config.route.DISCOVER_VOLUNTEER
import com.cyxbs.components.config.route.FOOD_ENTRY
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.components.config.route.UFIELD_MAIN_ENTRY
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.store.api.IStoreService.Task.DAILY_SIGN
import com.cyxbs.pages.store.api.IStoreService.Task.JOIN_DECLARE
import com.cyxbs.pages.store.api.IStoreService.Task.JOIN_FOOD
import com.cyxbs.pages.store.api.IStoreService.Task.JOIN_NOCLASS
import com.cyxbs.pages.store.api.IStoreService.Task.JOIN_UFIELD
import com.cyxbs.pages.store.api.IStoreService.Task.JOIN_QA
import com.cyxbs.pages.store.api.IStoreService.Task.LOGIN_VOLUNTEER
import com.cyxbs.pages.store.bean.StampCenter

/**
 * 需要与后端交互时的类型区分
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2021/8/27
 */
class StoreType {
  object Product { // 货物分类时使用
    const val DRESS = 1 // 装扮
    const val GOODS = 0 // 邮货
  }
  
  object Task { // 邮票中心首页的邮票任务, 在进度修改时要我们自己上传任务进度 (会涉及不同模块)
    fun jumpOtherUi(context: Context, task: StampCenter.Task) {
      when (task.title) {
        DAILY_SIGN.title -> {
          startActivity(MINE_CHECK_IN)
        }
        JOIN_FOOD.title-> {
          startActivity(FOOD_ENTRY)
        }
        JOIN_NOCLASS.title-> {
          startActivity(DISCOVER_NO_CLASS)
        }
        JOIN_DECLARE.title-> {
          startActivity(DECLARE_ENTRY)
        }
        JOIN_QA.title->{
          startActivity(QA_ENTRY)
        }
        JOIN_UFIELD.title-> {
          startActivity(UFIELD_MAIN_ENTRY)
        }
        LOGIN_VOLUNTEER.title -> {
          startActivity(DISCOVER_VOLUNTEER)
        }
        /*
        * 注意, 如果后面接手的学弟要添加新的任务, 目前(2021/9/11)对于任务进度是要我们自己完成后向后端发送请求的
        * 发送请求的代码我已经封装进了 api 模块中
        * */
        else -> {
          toast("若点击无跳转，请向我们反馈，谢谢")
        }
      }
    }
  }
  
  object ExchangeError { // 请求兑换时使用
    const val OTHER_ERROR = Int.MIN_VALUE // 其他错误, 乱打的数字防止出现重复
    const val NOT_ENOUGH_MONEY = 50000 // 钱不够
    const val OUT_OF_STOCK = 50001 // 库存不足
    const val IS_PURCHASED = 50002 // 当商品为邮货时且已经购买过
  }
}