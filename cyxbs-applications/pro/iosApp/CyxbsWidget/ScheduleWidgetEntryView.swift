import SwiftUI
import WidgetKit

/// 三档 WidgetFamily 分别承载当前课程、横向日时间条和七天课表。
struct ScheduleWidgetEntryView: View {
    let entry: ScheduleTimelineEntry

    @Environment(\.widgetFamily) var family
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                ScheduleSystemSmall(entry: entry)
                    .widgetBackground(Color.clear)
            case .systemMedium:
                ScheduleSystemMediumWithMargins(entry: entry)
                    .background { ScheduleWidgetSurface(color: CourseWidgetTheme.surface(colorScheme)) }
                    .widgetBackground(CourseWidgetTheme.surface(colorScheme))
                    // 旧系统中号不保证逐条 Link 可用；点到非条目区域仍能进入课表。
                    .widgetURL(URL(string: "cyxbs://home"))
            case .systemLarge:
                ScheduleSystemLarge(entry: entry)
                    .background { ScheduleWidgetSurface(color: CourseWidgetTheme.surface(colorScheme)) }
                    .widgetBackground(CourseWidgetTheme.surface(colorScheme))
                    .widgetURL(URL(string: "cyxbs://home"))
            default:
                Color.clear
            }
        }
    }
}

/// 中号组件保留水平 4pt、垂直 8pt 的内部留白；iOS 17 起抵消系统额外内容边距。
private struct ScheduleSystemMediumWithMargins: View {
    let entry: ScheduleTimelineEntry

    @ViewBuilder
    var body: some View {
        if #available(iOS 17.0, *) {
            ScheduleSystemMediumExpanded(entry: entry)
        } else {
            ScheduleSystemMedium(entry: entry)
        }
    }
}

/// 只扩展中号内容的可用区域，不修改其它尺寸档位或 WidgetKit 的整体背景。
@available(iOS 17.0, *)
private struct ScheduleSystemMediumExpanded: View {
    let entry: ScheduleTimelineEntry
    @Environment(\.widgetContentMargins) private var margins

    var body: some View {
        ScheduleSystemMedium(entry: entry)
            .padding(EdgeInsets(
                top: -margins.top,
                leading: -margins.leading,
                bottom: -margins.bottom,
                trailing: -margins.trailing
            ))
    }
}

/// 中、大号在内容层绘制底色，避免系统单独调色 Widget 背景后与课程外圈产生色差。
private struct ScheduleWidgetSurface: View {
    let color: Color

    @ViewBuilder
    var body: some View {
        if #available(iOS 17.0, *) {
            ScheduleWidgetExpandedSurface(color: color)
        } else {
            color
        }
    }
}

/// iOS 17 起内容会自动内缩，底色需覆盖这些边距，但不能改变课表本身的布局尺寸。
@available(iOS 17.0, *)
private struct ScheduleWidgetExpandedSurface: View {
    let color: Color
    @Environment(\.widgetContentMargins) private var margins

    var body: some View {
        GeometryReader { geometry in
            color
                .frame(
                    width: geometry.size.width + margins.leading + margins.trailing,
                    height: geometry.size.height + margins.top + margins.bottom
                )
                .offset(x: -margins.leading, y: -margins.top)
        }
    }
}

/// 对齐 Android compact 的选择顺序：正在进行的课程优先，否则展示今天下一项。
struct ScheduleSystemSmall: View {
    let entry: ScheduleTimelineEntry

    private var item: CourseWidgetRenderItem? {
        guard let snapshot = entry.snapshot else { return nil }
        let day = Calendar.current.component(.weekday, from: entry.date)
        let isoDay = (day + 5) % 7 + 1
        let minutes = Calendar.current.component(.hour, from: entry.date) * 60
            + Calendar.current.component(.minute, from: entry.date)
        let candidates = snapshot.items(in: snapshot.currentWeek(on: entry.date))
            .filter { !$0.isAllDay && $0.dayOfWeek == isoDay && !$0.timeRanges.isEmpty }
            .sorted { ($0.timeRanges.first?.lowerBound ?? 0) < ($1.timeRanges.first?.lowerBound ?? 0) }
        return candidates.first(where: { candidate in
            candidate.timeRanges.contains { $0.lowerBound <= minutes && minutes < $0.upperBound }
        }) ?? candidates.first(where: { candidate in
            candidate.timeRanges.contains { $0.lowerBound > minutes }
        })
    }

    private var timeLabel: String {
        guard let item else { return "今日" }
        let minutes = Calendar.current.component(.hour, from: entry.date) * 60
            + Calendar.current.component(.minute, from: entry.date)
        let active = item.timeRanges.first { $0.lowerBound <= minutes && minutes < $0.upperBound }
        let minute = active?.upperBound ?? item.timeRanges.first?.lowerBound ?? 0
        return "\(active == nil ? "今日" : "结束")：\(String(format: "%02d:%02d", minute / 60, minute % 60))"
    }

    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 3) {
                Text(timeLabel)
                    .font(.system(size: 15))
                    .lineLimit(1)
                Text(item?.title ?? "今天没有后续安排~")
                    .font(.system(size: 16, weight: .medium))
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
                Rectangle()
                    .fill(Color.white)
                    .frame(height: 1)
                Text(item?.content ?? "")
                    .font(.system(size: 15))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .foregroundColor(.white)
            .frame(width: max(1, geometry.size.width - 20), height: geometry.size.height, alignment: .center)
            .padding(.horizontal, 10)
        }
        // 小号 Widget 整体只有一个点击目标，优先打开当前课程详情，否则打开课表。
        .widgetURL(item.flatMap { $0.action.destinationURL() } ?? URL(string: "cyxbs://home"))
    }
}
