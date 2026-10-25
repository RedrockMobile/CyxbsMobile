package com.cyxbs.pages.widget.page

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.widget.widget.normal.NormalWidget
import com.cyxbs.pages.widget.widget.oversize.OversizedAppWidget
import com.cyxbs.pages.widget.widget.single.SingleWidgetReceiver
import com.g985892345.provider.api.annotation.ImplProvider

/** Android 小组件预览与固定请求实现。 */
@ImplProvider(clazz = CourseWidgetPagePlatform::class)
object AndroidCourseWidgetPagePlatform : CourseWidgetPagePlatform {

  private val appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(appContext)

  override val isPinRequestSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      appWidgetManager.isRequestPinAppWidgetSupported

  // 小米桌面可能在系统 API 返回 true 后继续用独立的“桌面快捷方式”权限拒绝请求。
  override val needsPinPermissionGuide: Boolean
    get() = appContext.packageManager.resolveActivity(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
      0,
    )?.activityInfo?.packageName == "com.miui.home"

  /** Android 8.0+ 把具体 Receiver 交给 Launcher，成功回调只在用户确认固定后发送。 */
  override fun requestPinWidget(kind: CourseWidgetKind): Boolean {
    if (!isPinRequestSupported) return false
    // 锁屏或页面失去前台时平台可能抛出异常；此时交给目录页展示手动添加方法。
    return runCatching {
      // 系统需填入新增实例 ID，因此 Android 12+ 使用指向应用内显式广播的可变 PendingIntent。
      val callback = PendingIntent.getBroadcast(
        appContext,
        kind.ordinal,
        Intent(appContext, CourseWidgetPinSuccessReceiver::class.java)
          .setAction(CourseWidgetPinSuccessReceiver.ACTION_PIN_SUCCEEDED),
        PendingIntent.FLAG_UPDATE_CURRENT or
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
      )
      appWidgetManager.requestPinAppWidget(
        ComponentName(appContext, receiverClass(kind)),
        null,
        callback,
      )
    }.getOrDefault(false)
  }

  /** 固定成功后系统会增加对应 Receiver 的实例数；只检查本应用自己的组件。 */
  override fun pinnedWidgetCount(kind: CourseWidgetKind): Int = appWidgetManager
    .getAppWidgetIds(ComponentName(appContext, receiverClass(kind))).size

  /** 小组件目录样式到系统 Receiver 的唯一映射。 */
  private fun receiverClass(kind: CourseWidgetKind): Class<*> = when (kind) {
    CourseWidgetKind.CURRENT_COURSE -> SingleWidgetReceiver::class.java
    CourseWidgetKind.DAY_TIMELINE -> NormalWidget::class.java
    CourseWidgetKind.WEEK_TIMETABLE -> OversizedAppWidget::class.java
  }

  /** 特殊桌面权限没有标准授权弹窗，只能把用户带到当前应用的系统信息页。 */
  override fun openAppSettings(): Boolean = runCatching {
    appContext.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${appContext.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }.isSuccess

  /** 目录页渲染可直接导出图片的 Compose 示例，示例数据不会混入真实课表快照。 */
  @Composable
  override fun RenderPreview(kind: CourseWidgetKind, modifier: Modifier) {
    CourseWidgetSamplePreview(kind, modifier)
  }
}
