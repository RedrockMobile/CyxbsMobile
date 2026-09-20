package com.cyxbs.pages.widget.service

import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.ICourseWidgetSnapshotPublisher
import com.cyxbs.pages.widget.repo.CourseWidgetSnapshotStore
import com.cyxbs.pages.widget.widget.glance.CourseWidgetGeneratedPreviewPublisher
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 端课表最终渲染快照的发布实现。
 *
 * 课表 Manager 只调用跨平台接口；本实现负责在 IO 线程持久化并唤醒 AppWidget，不参与任何网络请求或业务数据合并。
 */
@ImplProvider(clazz = ICourseWidgetSnapshotPublisher::class)
object CourseWidgetSnapshotPublisher : ICourseWidgetSnapshotPublisher {

  /** 原子替换磁盘快照，成功提交后才发送 AppWidget 更新广播。 */
  override suspend fun replace(snapshot: CourseWidgetSnapshot) {
    withContext(Dispatchers.IO) {
      CourseWidgetSnapshotStore.replace(snapshot)
      CourseWidgetSnapshotStore.notifyWidgets()
      CourseWidgetGeneratedPreviewPublisher.publishIfAllowed(snapshot.generatedAtEpochMillis)
    }
  }
}
