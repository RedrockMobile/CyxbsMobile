package com.cyxbs.pages.widget.service

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.widget.IosMainBuildConfig
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.ICourseWidgetSnapshotPublisher
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.create
import platform.Foundation.writeToURL

/**
 * iOS 课表小组件的跨进程快照发布端。
 *
 * 课表仍只调用公共接口；这里把最终渲染数据写入独立的 App Group 文件，Swift WidgetKit 扩展
 * 无需链接课程或 Kotlin 框架。原子写入保证扩展只会读到完整的旧版或新版快照。
 */
@ImplProvider(clazz = ICourseWidgetSnapshotPublisher::class)
object IosCourseWidgetSnapshotPublisher : ICourseWidgetSnapshotPublisher {

  const val SNAPSHOT_FILE_NAME = "course_widget_snapshot.json"
  const val SNAPSHOT_CHANGED_NOTIFICATION = "CourseWidgetSnapshotDidChange"

  /** 保存 [snapshot] 后通知 iOS 应用进程请求 WidgetKit 重载；无 App Group 权限时明确报错。 */
  @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
  override suspend fun replace(snapshot: CourseWidgetSnapshot) {
    val container = NSFileManager.defaultManager
      .containerURLForSecurityApplicationGroupIdentifier(IosMainBuildConfig.APP_GROUP_ID)
      ?: error("课表小组件 App Group 容器不可用")
    val file = container.URLByAppendingPathComponent(SNAPSHOT_FILE_NAME)
      ?: error("课表小组件快照路径不可用")
    val bytes = defaultJson.encodeToString(snapshot).encodeToByteArray()
    val data = bytes.usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    check(data.writeToURL(file, atomically = true)) { "课表小组件快照写入失败" }
    NSNotificationCenter.defaultCenter.postNotificationName(
      aName = SNAPSHOT_CHANGED_NOTIFICATION,
      `object` = null,
    )
  }
}
