//
//  CyxbsWidgetBundle.swift
//  CyxbsWidget
//
//  Created by SSR on 2023/9/28.
//  Copyright © 2023 Redrock. All rights reserved.
//

import WidgetKit
import SwiftUI

/// 仅注册仍在使用的课表组件；沿用原 kind，让桌面已有组件继续由新布局更新。
@main
struct CyxbsWidgetBundle: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        ScheduleWidget()
    }
}
