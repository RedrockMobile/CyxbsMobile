import Foundation
import WidgetKit
import Intents

typealias ScheduleWidgetConfiguration = ConfigurationIntent

/// 只读取课表侧发布的 App Group 快照；扩展不再自行请求课程接口或维护第二套数据模型。
struct ScheduleProvider: IntentTimelineProvider {

    /// 占位阶段不读取跨进程文件，避免选择器尚未准备好 App Group 时阻塞展示。
    func placeholder(in context: Context) -> ScheduleTimelineEntry {
        ScheduleTimelineEntry(date: Date(), configuration: .init(), snapshot: nil)
    }

    /// 小组件选择器优先展示真实课表；尚无快照时只在预览里使用本地示例。
    func getSnapshot(
        for configuration: ScheduleWidgetConfiguration,
        in context: Context,
        completion: @escaping (ScheduleTimelineEntry) -> Void
    ) {
        let date = context.isPreview
            ? (Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date())
            : Date()
        completion(ScheduleTimelineEntry(
            date: date,
            configuration: configuration,
            snapshot: (Self.loadSnapshot() ?? (context.isPreview ? CourseWidgetSnapshot.galleryPreview(on: date) : nil))?
                .projected(on: date)
        ))
    }

    /// 预排今日十分钟刻度和次日零点条目；系统可延迟刷新，不保证精确到点。
    func getTimeline(
        for configuration: ScheduleWidgetConfiguration,
        in context: Context,
        completion: @escaping (Timeline<ScheduleTimelineEntry>) -> Void
    ) {
        let now = Date()
        let snapshot = Self.loadSnapshot()
        var dates = [now]
        let calendar = Calendar.current
        let todayEnd = calendar.date(bySettingHour: 22, minute: 30, second: 0, of: now) ?? now
        let todayStart = calendar.startOfDay(for: now)
        let nextDay = calendar.date(byAdding: .day, value: 1, to: todayStart) ?? now.addingTimeInterval(86_400)
        // 只为当前时间线可见的时段预排十分钟快照；跨日额外放一个条目更新星期和教学周。
        var nextTick = todayStart.addingTimeInterval(
            (floor(now.timeIntervalSince(todayStart) / 600) + 1) * 600
        )
        while nextTick <= todayEnd {
            dates.append(nextTick)
            nextTick.addTimeInterval(600)
        }
        dates.append(nextDay)
        let entries = dates.map {
            ScheduleTimelineEntry(date: $0, configuration: configuration, snapshot: snapshot?.projected(on: $0))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    /// 从专用 App Group 文件读取完整 JSON；缺失或协议版本变化时使用明确空态。
    private static func loadSnapshot() -> CourseWidgetSnapshot? {
        guard let appGroupID = widgetAppGroupID(),
              let container = FileManager.default.containerURL(
                  forSecurityApplicationGroupIdentifier: appGroupID
              ) else { return nil }
        let url = container.appendingPathComponent("course_widget_snapshot.json")
        guard let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(CourseWidgetSnapshot.self, from: data),
              snapshot.schemaVersion == 1,
              (0...60).contains(snapshot.maxWeek)
        else { return nil }
        return snapshot
    }

    /// 读取构建时由 Extension entitlement 复制的配置资源，避免换届改组后遗漏 Swift 常量。
    /// 缺失或包含多个组时无法确定容器，安全地展示空态。
    private static func widgetAppGroupID() -> String? {
        guard let url = Bundle.main.url(forResource: "CourseWidgetAppGroup", withExtension: "plist"),
              let data = try? Data(contentsOf: url),
              let plist = try? PropertyListSerialization.propertyList(from: data, format: nil),
              let values = plist as? [String: Any],
              let groups = values["com.apple.security.application-groups"] as? [String],
              groups.count == 1
        else { return nil }
        return groups[0]
    }
}
