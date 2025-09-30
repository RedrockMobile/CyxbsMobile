package com.cyxbs.pages.store.api

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/6 15:49
 */
interface IStoreService {
  
  /**
   * 用于向后端发送任务更新的请求(接手改积分商城项目的后端老哥后面也承认任务进度该他们做)
   *
   * **NOTE:** 发一次请求则该任务进度自动加一, 我们不用管进度是否溢出, 因为后端在溢出时会在请求结果中返回错误
   *
   * [onlyTag] 为发送该类型任务的唯一标记, 比如我要看 5 个动态, 总不能让我看 5 个相同的就能得到积分,
   * 该形参就用于传入一个唯一的标记, 然后会自动判断是否发送过该标记的请求, 你只管在看了评论后调用该方法即可
   *
   * @param task 任务
   * @param onlyTag 唯一标记
   */
  fun postTask(
    task: Task,
    onlyTag: String?,
    toast: String?=null
  )
  
  enum class TaskType(val type: String) {
    BASE("base"),
    MORE("more")
  }
  
  enum class Task(val title: String, val type: TaskType) {
    // 以下跳转到 module_qa 的 DailySignActivity
    DAILY_SIGN("今日打卡", TaskType.BASE),
    
    //以下跳转到美食板块
    JOIN_FOOD("使用美食板块", TaskType.BASE),
    //以下是跳转到没课约
    JOIN_NOCLASS("使用一次没课约", TaskType.BASE),
    //以下跳转到QA
    JOIN_QA("提一个问题", TaskType.BASE),
    //以下跳转到表态
    JOIN_DECLARE("发表一次表态", TaskType.BASE),
    //以下跳转到module_ufield
    JOIN_UFIELD("参加一次活动", TaskType.MORE),

    // 以下跳转到 module_volunteer 的 VolunteerLoginActivity
    LOGIN_VOLUNTEER("绑定志愿者账号", TaskType.MORE)
  }
}