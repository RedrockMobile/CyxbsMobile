import Foundation
import WidgetKit
import Intents

/// WidgetKit 单次渲染所需的课表快照；日期用于当前周与十分钟时间线的计算。
struct ScheduleTimelineEntry: TimelineEntry {
    let date: Date
    let configuration: ScheduleWidgetConfiguration
    let snapshot: CourseWidgetSnapshot?
}

/// 与 widget:api 的 CourseWidgetSnapshot 保持字段一致；业务含义由课表侧计算完成。
struct CourseWidgetSnapshot: Decodable {
    let schemaVersion: Int
    let firstWeekBeginEpochDays: Int?
    let maxWeek: Int
    let timelineBeginMinute: Int?
    let timelineEndMinute: Int?
    let timelineMarks: [CourseWidgetTimelineMark]
    let oversizedTimelineSections: [CourseWidgetTimelineSection]
    let weeks: [CourseWidgetWeekSnapshot]

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, firstWeekBeginEpochDays, maxWeek
        case timelineBeginMinute, timelineEndMinute, timelineMarks
        case oversizedTimelineSections, weeks
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        // defaultJson 会省略 Kotlin 默认值，未写 schemaVersion 即协议第 1 版。
        schemaVersion = try values.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        firstWeekBeginEpochDays = try values.decodeIfPresent(Int.self, forKey: .firstWeekBeginEpochDays)
        maxWeek = try values.decode(Int.self, forKey: .maxWeek)
        timelineBeginMinute = try values.decodeIfPresent(Int.self, forKey: .timelineBeginMinute)
        timelineEndMinute = try values.decodeIfPresent(Int.self, forKey: .timelineEndMinute)
        timelineMarks = try values.decodeIfPresent([CourseWidgetTimelineMark].self, forKey: .timelineMarks) ?? []
        oversizedTimelineSections = try values.decodeIfPresent([CourseWidgetTimelineSection].self, forKey: .oversizedTimelineSections) ?? []
        weeks = try values.decode([CourseWidgetWeekSnapshot].self, forKey: .weeks)
    }

    private init(source: CourseWidgetSnapshot, weeks: [CourseWidgetWeekSnapshot]) {
        schemaVersion = source.schemaVersion
        firstWeekBeginEpochDays = source.firstWeekBeginEpochDays
        maxWeek = source.maxWeek
        timelineBeginMinute = source.timelineBeginMinute
        timelineEndMinute = source.timelineEndMinute
        timelineMarks = source.timelineMarks
        oversizedTimelineSections = source.oversizedTimelineSections
        self.weeks = weeks
    }

    /// 以本地日历日期而非 UTC 时间戳求教学周，避免跨时区时周一被错判成周日。
    func currentWeek(on date: Date) -> Int {
        if let firstDay = firstWeekBeginEpochDays, maxWeek > 0 {
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(secondsFromGMT: 0)!
            let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
            if let day = utc.date(from: parts) {
                let todayEpochDay = Int(day.timeIntervalSince1970 / 86_400)
                let candidate = Int(floor(Double(todayEpochDay - firstDay) / 7)) + 1
                if (1...maxWeek).contains(candidate) { return candidate }
            }
        }
        guard maxWeek > 0 else { return 1 }
        return weeks.map(\.week).filter { (1...maxWeek).contains($0) }.min() ?? 1
    }

    /// 缺少指定周快照时返回空列表，不跨周展示旧课程。
    func items(in week: Int) -> [CourseWidgetRenderItem] {
        weeks.first(where: { $0.week == week })?.items ?? []
    }

    /// 时间线每十分钟一个条目，只保留该条目所处周，避免把整学期课程重复装入每张归档视图。
    func projected(on date: Date) -> CourseWidgetSnapshot {
        let week = currentWeek(on: date)
        return CourseWidgetSnapshot(
            source: self,
            weeks: weeks.filter { $0.week == week }
        )
    }

    /// 系统小组件选择器无真实快照时使用一周确定性示例，不写入 App Group 或影响用户课表。
    static func galleryPreview(on date: Date) -> CourseWidgetSnapshot? {
        let calendar = Calendar.current
        let dayOffset = (calendar.component(.weekday, from: date) + 5) % 7
        guard let monday = calendar.date(byAdding: .day, value: -dayOffset, to: calendar.startOfDay(for: date)) else {
            return nil
        }
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        let firstDay = Int((utc.date(from: calendar.dateComponents([.year, .month, .day], from: monday)) ?? monday)
            .timeIntervalSince1970 / 86_400)
        let names = ["通信原理", "IT科技与创新", "物流成本管理", "线性代数", "社团事务", "软件工程", "大学英语"]
        let places = ["教学楼 A", "实验楼", "4308", "逸夫楼", "活动中心", "第一教学楼", "2206"]
        let items: [[String: Any]] = (1...7).map { day in
            let isAffair = day == 5
            let light: [String: Any] = [
                "contentArgb": UInt64(isAffair ? 0xFF52673B : 0xFF15315B),
                "backgroundArgb": UInt64(isAffair ? 0xFFEAF2DF : 0xFFDCEBFF),
                "containerArgb": UInt64(0xFFFFFFFF),
                "stripeArgb": UInt64(0xFFB9D49A),
            ]
            let dark: [String: Any] = [
                "contentArgb": UInt64(0xFFF0F5FF),
                "backgroundArgb": UInt64(isAffair ? 0xFF405236 : 0xFF284C72),
                "containerArgb": UInt64(0xFF2D2D2D),
                "stripeArgb": UInt64(0xFF718963),
            ]
            return [
                "id": "gallery-\(day)", "dayOfWeek": day,
                "title": names[day - 1], "content": places[day - 1],
                "beginMinute": day == 3 ? 14 * 60 : 10 * 60,
                "endMinute": day == 3 ? 16 * 60 : 12 * 60,
                "lightStyle": light, "darkStyle": dark,
                "backgroundPattern": isAffair ? "DIAGONAL_STRIPE" : "SOLID",
                "action": ["week": 1, "itemId": "gallery-\(day)"],
            ]
        }
        let json: [String: Any] = [
            "firstWeekBeginEpochDays": firstDay,
            "maxWeek": 1,
            "timelineBeginMinute": 8 * 60,
            "timelineEndMinute": 22 * 60 + 30,
            "timelineMarks": [8, 10, 12, 14, 16, 18, 20, 22].map { hour in
                ["label": "\(hour)", "ratio": Double(hour - 8) / 14.5] as [String: Any]
            },
            "oversizedTimelineSections": [
                ["id": "day", "collapsedLabel": "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12",
                 "beginMinute": 8 * 60, "endMinute": 22 * 60 + 30,
                 "collapsedWeight": 1.0, "expandable": false] as [String: Any],
            ],
            "weeks": [["week": 1, "items": items]],
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: json) else { return nil }
        return try? JSONDecoder().decode(CourseWidgetSnapshot.self, from: data)
    }
}

struct CourseWidgetTimelineMark: Decodable {
    let label: String
    let ratio: Double
}

struct CourseWidgetTimelineSection: Decodable {
    let id: String
    let collapsedLabel: String
    let beginMinute: Int
    let endMinute: Int
    let collapsedWeight: Double
    let expandable: Bool
}

struct CourseWidgetWeekSnapshot: Decodable {
    let week: Int
    let items: [CourseWidgetRenderItem]
}

/// 单个已完成课程样式决策的绘制项；可见范围可用于被遮盖项目的裁剪。
struct CourseWidgetRenderItem: Decodable, Identifiable {
    let id: String
    let renderLayer: Int
    let dayOfWeek: Int
    let title: String
    let content: String
    let beginMinute: Int?
    let endMinute: Int?
    let beginRatio: Double?
    let endRatio: Double?
    let visibleRanges: [CourseWidgetVisibleRange]
    let isAllDay: Bool
    let lightStyle: CourseWidgetItemStyle
    let darkStyle: CourseWidgetItemStyle
    let backgroundPattern: String
    let action: CourseWidgetAction

    private enum CodingKeys: String, CodingKey {
        case id, renderLayer, dayOfWeek, title, content
        case beginMinute, endMinute, beginRatio, endRatio, visibleRanges
        case isAllDay, lightStyle, darkStyle, backgroundPattern, action
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        renderLayer = try values.decodeIfPresent(Int.self, forKey: .renderLayer) ?? 0
        dayOfWeek = try values.decode(Int.self, forKey: .dayOfWeek)
        title = try values.decode(String.self, forKey: .title)
        content = try values.decode(String.self, forKey: .content)
        beginMinute = try values.decodeIfPresent(Int.self, forKey: .beginMinute)
        endMinute = try values.decodeIfPresent(Int.self, forKey: .endMinute)
        beginRatio = try values.decodeIfPresent(Double.self, forKey: .beginRatio)
        endRatio = try values.decodeIfPresent(Double.self, forKey: .endRatio)
        visibleRanges = try values.decodeIfPresent([CourseWidgetVisibleRange].self, forKey: .visibleRanges) ?? []
        isAllDay = try values.decodeIfPresent(Bool.self, forKey: .isAllDay) ?? false
        lightStyle = try values.decode(CourseWidgetItemStyle.self, forKey: .lightStyle)
        darkStyle = try values.decode(CourseWidgetItemStyle.self, forKey: .darkStyle)
        backgroundPattern = try values.decodeIfPresent(String.self, forKey: .backgroundPattern) ?? "SOLID"
        action = try values.decode(CourseWidgetAction.self, forKey: .action)
    }

    /// 普通条目回退原始时间；被完全或部分遮盖的条目优先采用课表下发的可见范围。
    var timeRanges: [ClosedRange<Int>] {
        if !visibleRanges.isEmpty {
            return visibleRanges.map { $0.beginMinute ... $0.endMinute }
        }
        guard let beginMinute, let endMinute, endMinute > beginMinute else { return [] }
        return [beginMinute ... endMinute]
    }
}

struct CourseWidgetVisibleRange: Decodable {
    let beginMinute: Int
    let endMinute: Int
    let beginRatio: Double
    let endRatio: Double
}

struct CourseWidgetItemStyle: Decodable {
    let contentArgb: UInt64
    let backgroundArgb: UInt64
    let stripeArgb: UInt64?
    let containerArgb: UInt64?
}

/// 当前只消费重叠提示；点击详情链路会在课表选择状态打通后再读取这些 ID。
struct CourseWidgetAction: Decodable {
    let week: Int
    let itemId: String?
    let overlapItemIds: [String]

    private enum CodingKeys: String, CodingKey { case week, itemId, overlapItemIds }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        week = try values.decode(Int.self, forKey: .week)
        itemId = try values.decodeIfPresent(String.self, forKey: .itemId)
        overlapItemIds = try values.decodeIfPresent([String].self, forKey: .overlapItemIds) ?? []
    }
}
