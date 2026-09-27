import Foundation
import SwiftUI
import WidgetKit

/// Android 横向日课表在 iOS systemMedium 上的静态甘特布局。
struct ScheduleSystemMedium: View {
    let entry: ScheduleTimelineEntry
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { size in
            let snapshot = entry.snapshot
            let day = (Calendar.current.component(.weekday, from: entry.date) + 5) % 7 + 1
            let week = snapshot?.currentWeek(on: entry.date) ?? 0
            let begin = snapshot?.timelineBeginMinute ?? 8 * 60
            let end = snapshot?.timelineEndMinute ?? 22 * 60 + 30
            let items = snapshot?.items(in: week).filter {
                !$0.isAllDay && $0.dayOfWeek == day &&
                    ($0.endMinute ?? 0) > begin && ($0.beginMinute ?? 1_440) < end
            } ?? []
            let bars = Self.place(items, begin: begin, end: end)
            let axisWidth: CGFloat = 26
            let horizontalPadding: CGFloat = 4
            let verticalPadding: CGFloat = 8
            let scaleHeight: CGFloat = 18
            let scaleTextCenterY: CGFloat = 8
            let trackWidth = max(1, size.size.width - axisWidth - 3 - horizontalPadding * 2)
            let timelineInset: CGFloat = 6
            let contentWidth = max(1, trackWidth - timelineInset * 2)
            let trackHeight = max(1, size.size.height - scaleHeight - 1 - verticalPadding * 2)
            let nowMinute = Calendar.current.component(.hour, from: entry.date) * 60
                + Calendar.current.component(.minute, from: entry.date)

            HStack(spacing: 3) {
                Text("今")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(CourseWidgetTheme.foreground(colorScheme))
                    // 与轨道共用 8pt 上下留白，避免左侧文字撑出组件高度。
                    .frame(width: axisWidth, height: max(1, size.size.height - verticalPadding * 2))
                VStack(spacing: 1) {
                    GeometryReader { marks in
                        ZStack(alignment: .topLeading) {
                            ForEach(Array((snapshot?.timelineMarks ?? []).enumerated()), id: \.offset) { pair in
                                let mark = pair.element
                                if mark.ratio >= 0 && mark.ratio <= 1 {
                                    Text(mark.label)
                                        .font(.system(size: 9))
                                        .foregroundColor(CourseWidgetTheme.axis(colorScheme))
                                        .fixedSize()
                                        .position(x: timelineInset + contentWidth * mark.ratio, y: scaleTextCenterY)
                                }
                            }
                            if nowMinute >= begin && nowMinute <= end {
                                // 标记与小时文字共用纵向中心，避免圆点上方短、下方长。
                                CourseWidgetMediumNowMarker()
                                    .position(
                                        x: timelineInset + contentWidth * CGFloat(nowMinute - begin) / CGFloat(max(end - begin, 1)),
                                        y: scaleTextCenterY
                                    )
                            }
                        }
                    }
                    .frame(height: scaleHeight)

                    GeometryReader { track in
                        ZStack(alignment: .topLeading) {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(CourseWidgetTheme.track(colorScheme))
                            ForEach(bars.filter { $0.lane < 4 }) { bar in
                                let left = CGFloat(bar.begin - begin) / CGFloat(max(end - begin, 1))
                                let right = CGFloat(bar.end - begin) / CGFloat(max(end - begin, 1))
                                let laneHeight = trackHeight / CGFloat(min(bar.laneCount, 4))
                                if right > left {
                                    CourseWidgetItemCard(item: bar.item, showText: true, cardHeight: laneHeight - 2)
                                        .frame(width: max(1, contentWidth * (right - left)), height: laneHeight - 2)
                                        .offset(x: timelineInset + contentWidth * left, y: CGFloat(bar.lane) * laneHeight + 1)
                                }
                            }
                        }
                    }
                }
                .frame(width: trackWidth)
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
        }
    }

    /// 先拆重叠连通区间，再在各区间按 renderLayer 分轨；孤立课程恢复完整高度。
    private static func place(_ items: [CourseWidgetRenderItem], begin: Int, end: Int) -> [PlacedBar] {
        let candidates = items.enumerated().compactMap { offset, item -> BarCandidate? in
            guard let itemBegin = item.beginMinute, let itemEnd = item.endMinute else { return nil }
            let clippedBegin = max(begin, itemBegin)
            let clippedEnd = min(end, itemEnd)
            guard clippedEnd > clippedBegin else { return nil }
            return BarCandidate(item: item, begin: clippedBegin, end: clippedEnd, order: offset)
        }.sorted {
            $0.begin == $1.begin ? $0.end > $1.end : $0.begin < $1.begin
        }
        var groups: [[BarCandidate]] = []
        var groupEnd = Int.min
        for candidate in candidates {
            // 半开区间首尾相接时不算重叠，后一个区间应重新获得整层高度。
            if groups.isEmpty || candidate.begin >= groupEnd {
                groups.append([candidate])
                groupEnd = candidate.end
            } else {
                groups[groups.count - 1].append(candidate)
                groupEnd = max(groupEnd, candidate.end)
            }
        }
        return groups.flatMap { group -> [PlacedBar] in
            var lanes: [[Range<Int>]] = []
            var placements: [(BarCandidate, Int)] = []
            for candidate in group.sorted(by: {
                $0.item.renderLayer == $1.item.renderLayer
                    ? $0.order < $1.order : $0.item.renderLayer < $1.item.renderLayer
            }) {
                let range = candidate.begin..<candidate.end
                let lane = lanes.firstIndex { ranges in
                    ranges.allSatisfy { $0.upperBound <= range.lowerBound || range.upperBound <= $0.lowerBound }
                } ?? lanes.count
                if lane == lanes.count { lanes.append([]) }
                lanes[lane].append(range)
                placements.append((candidate, lane))
            }
            return placements.map { candidate, lane in
                PlacedBar(item: candidate.item, begin: candidate.begin, end: candidate.end,
                          lane: lane, laneCount: lanes.count)
            }
        }
    }

    private struct BarCandidate {
        let item: CourseWidgetRenderItem
        let begin: Int
        let end: Int
        let order: Int
    }

    private struct PlacedBar: Identifiable {
        let item: CourseWidgetRenderItem
        let begin: Int
        let end: Int
        let lane: Int
        let laneCount: Int
        var id: String { item.id }
    }
}

/// iOS 大号固定展示七天，不提供无法滚动的折叠时间轴。
struct ScheduleSystemLarge: View {
    let entry: ScheduleTimelineEntry
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { size in
            let snapshot = entry.snapshot
            let week = snapshot?.currentWeek(on: entry.date) ?? 0
            let items = snapshot?.items(in: week) ?? []
            let sections = (snapshot?.oversizedTimelineSections ?? []).filter {
                $0.endMinute > $0.beginMinute && $0.collapsedWeight > 0
            }
            let usableSections = sections.isEmpty ? Self.fallbackSections : sections
            let totalWeight = max(usableSections.reduce(0) { $0 + $1.collapsedWeight }, 0.001)
            let monday = Self.monday(snapshot: snapshot, week: week, today: entry.date)
            let hasAllDay = items.contains(where: { $0.isAllDay })
            let headerHeight: CGFloat = 44
            let selectedDayHeight: CGFloat = 38
            let allDayHeight: CGFloat = hasAllDay ? 20 : 0
            let axisWidth: CGFloat = 28
            let trackHeight = max(1, size.size.height - headerHeight - allDayHeight)
            let dayWidth = max(1, (size.size.width - axisWidth) / 7)
            let todayIndex = (0..<7).first { index in
                let date = Calendar.current.date(byAdding: .day, value: index, to: monday) ?? monday
                return Calendar.current.isDate(date, inSameDayAs: entry.date)
            }
            let nowMinute = Calendar.current.component(.hour, from: entry.date) * 60
                + Calendar.current.component(.minute, from: entry.date)

            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    Text("\(Calendar.current.component(.month, from: monday))月")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(CourseWidgetTheme.foreground(colorScheme))
                        .frame(width: axisWidth, height: headerHeight)
                    ForEach(0..<7, id: \.self) { index in
                        let date = Calendar.current.date(byAdding: .day, value: index, to: monday) ?? monday
                        let selected = Calendar.current.isDate(date, inSameDayAs: entry.date)
                        VStack(spacing: 0) {
                            Text(Self.dayLabels[index]).font(.system(size: 10, weight: .bold))
                            Text("\(Calendar.current.component(.day, from: date))日")
                                .font(.system(size: 9))
                        }
                        .foregroundColor(selected ? .white : CourseWidgetTheme.foreground(colorScheme))
                        .frame(width: min(dayWidth - 2, selectedDayHeight), height: selectedDayHeight)
                        .background(selected ? Color(red: 0.16, green: 0.31, blue: 0.52) : .clear)
                        .cornerRadius(8)
                        .frame(width: dayWidth, height: headerHeight)
                    }
                }
                if hasAllDay {
                    HStack(spacing: 0) {
                        Text("全天")
                            .font(.system(size: 8))
                            .foregroundColor(CourseWidgetTheme.foreground(colorScheme))
                            .frame(width: axisWidth, height: allDayHeight)
                        ForEach(1...7, id: \.self) { day in
                            let item = items.first(where: { $0.dayOfWeek == day && $0.isAllDay })
                            Group {
                                if let item {
                                    CourseWidgetItemCard(item: item, showText: true, cardHeight: allDayHeight)
                                } else {
                                    Color.clear
                                }
                            }
                            .frame(width: dayWidth, height: allDayHeight)
                        }
                    }
                }
                HStack(spacing: 0) {
                    CourseWidgetTimeAxis(
                        sections: usableSections,
                        totalWeight: totalWeight,
                        nowMinute: nowMinute
                    )
                        .frame(width: axisWidth, height: trackHeight)
                    ForEach(1...7, id: \.self) { day in
                        GeometryReader { column in
                            ZStack(alignment: .topLeading) {
                                ForEach(items.filter { $0.dayOfWeek == day && !$0.isAllDay }
                                    .sorted { $0.renderLayer > $1.renderLayer }) { item in
                                    let ranges = item.timeRanges
                                    let textIndex = ranges.indices.max(by: {
                                        ranges[$0].upperBound - ranges[$0].lowerBound <
                                            ranges[$1].upperBound - ranges[$1].lowerBound
                                    })
                                    ForEach(ranges.indices, id: \.self) { index in
                                        let top = Self.position(ranges[index].lowerBound, sections: usableSections, total: totalWeight)
                                        let bottom = Self.position(ranges[index].upperBound, sections: usableSections, total: totalWeight)
                                        if bottom > top {
                                            let height = trackHeight * (bottom - top)
                                            CourseWidgetItemCard(item: item, showText: index == (textIndex ?? 0), cardHeight: height)
                                                .frame(width: column.size.width, height: height)
                                                .offset(y: trackHeight * top)
                                        }
                                    }
                                }
                            }
                        }
                        .frame(width: dayWidth, height: trackHeight)
                    }
                }
            }
            .background(alignment: .topLeading) {
                if let todayIndex {
                    // 与课表一致：今日底纹覆盖完整日期列，但始终位于日期标题和课程卡片下方。
                    CourseWidgetTodayColumnBackground(
                        color: CourseWidgetTheme.todayBackground(colorScheme),
                        width: dayWidth,
                        height: size.size.height,
                        // 向上伸入日期块一个圆角半径，由蓝色日期块盖住底纹起点并自然衔接。
                        startY: (headerHeight + selectedDayHeight) / 2 - CourseWidgetTodayColumnCornerRadius,
                        offsetX: axisWidth + CGFloat(todayIndex) * dayWidth
                    )
                }
            }
        }
    }

    private static let dayLabels = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    private static let fallbackSections = [CourseWidgetTimelineSection(
        id: "day", collapsedLabel: "", beginMinute: 480, endMinute: 1_350,
        collapsedWeight: 1, expandable: false
    )]

    /// 将课表给出的折叠权重映射到固定容器高度，不在 Widget 内猜测课节区间。
    fileprivate static func position(
        _ minute: Int,
        sections: [CourseWidgetTimelineSection],
        total: Double
    ) -> CGFloat {
        var before = 0.0
        for section in sections {
            if minute <= section.beginMinute { return CGFloat(before / total) }
            if minute < section.endMinute {
                let fraction = Double(minute - section.beginMinute)
                    / Double(section.endMinute - section.beginMinute)
                return CGFloat((before + section.collapsedWeight * fraction) / total)
            }
            before += section.collapsedWeight
        }
        return 1
    }

    /// 快照的 epochDays 表示日历日期，转换时先以 UTC 提取年月日再构造本地周一。
    private static func monday(snapshot: CourseWidgetSnapshot?, week: Int, today: Date) -> Date {
        guard let firstDay = snapshot?.firstWeekBeginEpochDays, week > 0 else {
            let offset = (Calendar.current.component(.weekday, from: today) + 5) % 7
            return Calendar.current.date(byAdding: .day, value: -offset, to: Calendar.current.startOfDay(for: today)) ?? today
        }
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        let source = Date(timeIntervalSince1970: TimeInterval(firstDay + (week - 1) * 7) * 86_400)
        let parts = utc.dateComponents([.year, .month, .day], from: source)
        return Calendar.current.date(from: parts) ?? today
    }
}

/// 今日底纹从选中日期块下方露出，顶部藏在日期块背后，底部延伸至组件边缘。
private struct CourseWidgetTodayColumnBackground: View {
    let color: Color
    let width: CGFloat
    let height: CGFloat
    let startY: CGFloat
    let offsetX: CGFloat

    @ViewBuilder
    var body: some View {
        if #available(iOS 17.0, *) {
            CourseWidgetExpandedTodayColumnBackground(
                color: color, width: width, height: height,
                startY: startY, offsetX: offsetX
            )
        } else {
            CourseWidgetTodayColumnShape(radius: CourseWidgetTodayColumnCornerRadius)
                .fill(color)
                .frame(width: width, height: max(0, height - startY))
                .offset(x: offsetX, y: startY)
        }
    }
}

/// iOS 17 起组件内容自动内缩；底纹不侵入日期表头，只补足底部边距。
@available(iOS 17.0, *)
private struct CourseWidgetExpandedTodayColumnBackground: View {
    let color: Color
    let width: CGFloat
    let height: CGFloat
    let startY: CGFloat
    let offsetX: CGFloat
    @Environment(\.widgetContentMargins) private var margins

    var body: some View {
        CourseWidgetTodayColumnShape(radius: CourseWidgetTodayColumnCornerRadius)
            .fill(color)
            .frame(width: width, height: max(0, height - startY + margins.bottom))
            .offset(x: offsetX, y: startY)
    }
}

/// 今日列只在顶部保留圆角，底部与课程区域连续衔接。
private struct CourseWidgetTodayColumnShape: Shape {
    let radius: CGFloat

    /// 在给定矩形内只圆滑顶部两角，底部保持直边以连续铺满当天课程列。
    func path(in rect: CGRect) -> Path {
        let corner = min(radius, rect.width / 2, rect.height / 2)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + corner))
        path.addQuadCurve(
            to: CGPoint(x: rect.minX + corner, y: rect.minY),
            control: CGPoint(x: rect.minX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX - corner, y: rect.minY))
        path.addQuadCurve(
            to: CGPoint(x: rect.maxX, y: rect.minY + corner),
            control: CGPoint(x: rect.maxX, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// 日期块与今日底纹共用的圆角/重叠高度，避免调整样式时出现接缝。
private let CourseWidgetTodayColumnCornerRadius: CGFloat = 8

/// 按折叠权重绘制与课表同一套分段标签，不提供无效的展开按钮。
private struct CourseWidgetTimeAxis: View {
    let sections: [CourseWidgetTimelineSection]
    let totalWeight: Double
    let nowMinute: Int
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                ForEach(Array(sections.enumerated()), id: \.offset) { pair in
                    let section = pair.element
                    let labels = section.collapsedLabel.split(separator: "\n").map(String.init)
                    ForEach(Array(labels.enumerated()), id: \.offset) { labelPair in
                        let index = labelPair.offset
                        let label = labelPair.element
                        let minute = section.beginMinute +
                            (section.endMinute - section.beginMinute) * (index * 2 + 1) / max(labels.count * 2, 1)
                        Text(label)
                            .font(.system(size: 8))
                            .foregroundColor(CourseWidgetTheme.foreground(colorScheme))
                            .lineLimit(1)
                            .frame(width: geometry.size.width - 3)
                            .position(
                                x: (geometry.size.width - 3) / 2,
                                y: geometry.size.height * ScheduleSystemLarge.position(
                                    minute, sections: sections, total: totalWeight
                                )
                            )
                    }
                }
                if let first = sections.first, let last = sections.last,
                   nowMinute >= first.beginMinute && nowMinute <= last.endMinute {
                    CourseWidgetNowLine()
                        .frame(width: geometry.size.width - 2, height: 5)
                        .offset(y: geometry.size.height * ScheduleSystemLarge.position(
                            nowMinute, sections: sections, total: totalWeight
                        ) - 2.5)
                }
            }
        }
    }
}

/// iOS 小组件按桌面外观选择课表快照中的明暗样式；Android 仍固定消费浅色样式。
private struct CourseWidgetItemCard: View {
    let item: CourseWidgetRenderItem
    let showText: Bool
    let cardHeight: CGFloat
    @Environment(\.colorScheme) private var colorScheme

    private var style: CourseWidgetItemStyle {
        colorScheme == .dark ? item.darkStyle : item.lightStyle
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 5)
                .fill(style.containerArgb.map(Color.init(argb:)) ?? CourseWidgetTheme.surface(colorScheme))
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(argb: style.backgroundArgb))
                if item.backgroundPattern == "DIAGONAL_STRIPE" {
                    Canvas { context, size in
                        let stripeColor = Color(argb: style.stripeArgb ?? style.contentArgb)
                        for start in stride(from: -size.height, through: size.width + size.height, by: 7) {
                            var path = Path()
                            path.move(to: CGPoint(x: start, y: 0))
                            path.addLine(to: CGPoint(x: start + size.height, y: size.height))
                            context.stroke(path, with: .color(stripeColor), lineWidth: 1)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                if showText {
                    VStack(alignment: .center, spacing: 0) {
                        Text(item.title)
                            .font(.system(size: 9, weight: .medium))
                            .lineLimit(cardHeight >= 42 ? 3 : cardHeight >= 25 ? 2 : 1)
                            .minimumScaleFactor(0.9)
                            // 文字占满内容区后逐行居中，不能只把整个 Text 视图居中。
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        if cardHeight >= 39 && !item.content.isEmpty {
                            Spacer(minLength: 0)
                            Text(item.content)
                                .font(.system(size: 9))
                                .lineLimit(cardHeight >= 58 ? 2 : 1)
                                .minimumScaleFactor(0.9)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .foregroundColor(Color(argb: style.contentArgb))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .padding(4)
                }
            }
            .padding(2)
            if !item.action.overlapItemIds.isEmpty {
                Circle()
                    .fill(Color(argb: style.contentArgb))
                    .frame(width: 5, height: 5)
                    .padding(3)
            }
        }
        .clipped()
    }
}

/// 中号组件的当前时间标记限于顶部小时刻度区，圆点位于竖线起点。
private struct CourseWidgetMediumNowMarker: View {
    var body: some View {
        VStack(spacing: 0) {
            Circle().fill(Color.gray).frame(width: 4, height: 4)
            Rectangle().fill(Color.gray).frame(width: 1, height: 14)
        }
        .frame(width: 4, height: 18)
    }
}

/// 大号左侧时间轴的 1pt 横线与 4pt 圆点。
private struct CourseWidgetNowLine: View {
    var body: some View {
        GeometryReader { geometry in
            // 圆点完整位于横线左侧，横线从圆点右缘开始，避免穿过圆点。
            HStack(spacing: 0) {
                Circle().fill(Color.gray).frame(width: 4, height: 4)
                Rectangle().fill(Color.gray).frame(height: 1)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
    }
}

/// 中、大号小组件共用的画布与时间轴配色；深色画布对应课表快照的底卡颜色。
enum CourseWidgetTheme {
    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 45.0 / 255.0, green: 45.0 / 255.0, blue: 45.0 / 255.0) : .white
    }

    /// 与课表本体的今日列底纹使用同一组明暗 ARGB 值。
    static func todayBackground(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 1.0 / 255.0, green: 1.0 / 255.0, blue: 1.0 / 255.0, opacity: 38.0 / 255.0)
            : Color(red: 232.0 / 255.0, green: 240.0 / 255.0, blue: 252.0 / 255.0, opacity: 147.0 / 255.0)
    }

    static func track(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 38.0 / 255.0, green: 38.0 / 255.0, blue: 38.0 / 255.0)
            : Color(red: 0.96, green: 0.97, blue: 0.99)
    }

    static func foreground(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 0.94, green: 0.94, blue: 0.95)
            : Color(red: 0.08, green: 0.19, blue: 0.36)
    }

    static func axis(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.69, green: 0.72, blue: 0.77) : .gray
    }
}

private extension Color {

    /// Kotlin 快照以无符号化的 ARGB Long 传色，SwiftUI 保留原 Alpha 与 RGB。
    init(argb: UInt64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xff) / 255,
            green: Double((argb >> 8) & 0xff) / 255,
            blue: Double(argb & 0xff) / 255,
            opacity: Double((argb >> 24) & 0xff) / 255
        )
    }
}
