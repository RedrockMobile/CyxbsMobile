//
//  ScheduleWidget.swift
//  CyxbsWidgetExtension
//
//  Created by SSR on 2022/12/30.
//  Copyright © 2022 Redrock. All rights reserved.
//

import SwiftUI
import WidgetKit

struct ScheduleWidget: Widget {
    
    let kind: String = CyxbsWidgetKind.schedule

    var body: some WidgetConfiguration {
        
        IntentConfiguration(kind: kind, intent: ScheduleWidgetConfiguration.self, provider: ScheduleProvider()) { entry in
            ScheduleWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("掌邮课表")
        .description("小号看下一节，中号看今日时间条，大号看七天课表")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
