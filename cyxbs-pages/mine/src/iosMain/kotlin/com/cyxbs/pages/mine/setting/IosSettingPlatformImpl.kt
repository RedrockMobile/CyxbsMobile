package com.cyxbs.pages.mine.setting

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.ICourseNotificationReminderService
import com.cyxbs.pages.mine.home.MineIosPlatform
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * iOS 设置页的平台实现。
 *
 * 公共条目由 [SettingPlatform] 提供，持久化由 mine:api 管理；此处只桥接 iOS 原生能力。
 */
@ImplProvider(clazz = SettingPlatform::class)
object IosSettingPlatformImpl : SettingPlatform() {

  /** iOS 通过本地通知实现提醒，独立追加条目，不复用 Android 的日历权限或读写逻辑。 */
  override fun switchItems(): List<SettingItem.Switch> = super.switchItems() +
    SettingItem.Switch(
      key = "course_notification_reminder",
      title = "上课前提醒我",
      readChecked = { ICourseNotificationReminderService::class.implOrNull()?.isEnabled() == true },
      rememberOnCheckedChange = {
        { enabled ->
          val service = ICourseNotificationReminderService::class.implOrNull()
          if (!enabled) {
            service?.disable()
          } else {
            // 通知授权由此开关独立处理；服务恢复主线程上下文并校验授权期间账号未切换。
            if (service?.requestAuthorization() == true) {
              if (!service.enable()) toast("课前提醒开启失败，请重试")
            } else {
              toast("需要通知权限，请在系统设置中允许掌邮通知")
            }
          }
        }
      },
    )

  /** 打开仍由 UIKit 承载的账号与安全页面。 */
  override fun openAccountSecurity() {
    MineIosPlatform::class.implOrNull()?.jumpAccountSecurity() ?: toast("暂不支持跳转")
  }

  /** 退出登录前停止当前账号的提醒；课程服务也会监听其他入口触发的账号退出。 */
  override fun clearPlatformDataBeforeLogout() {
    ICourseNotificationReminderService::class.implOrNull()?.clearBeforeLogout()
  }

}
