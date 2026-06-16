//
// Created by 郭祥瑞 on 2025/10/27.
// Copyright (c) 2025 Redrock. All rights reserved.
//

import Foundation
import UIKit
import CyxbsApplicationsMultiplatform

class KmpInterfaceImpl: IOSKmpInterface {

    func isDebug() -> Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    func setToken(token: String) {
        UserModel.default.setingTokenToOC(token: TokenModel(token: token))
    }

    func getDefaultExpandCourse() -> Bool {
        return UserDefaultsManager.shared.presentScheduleWhenOpenApp
    }

    func enableUsePlatformToast() -> Bool {
        return true
    }

    func toast(s: String, isLong: Bool) {
        if isLong {
            RemindHUD.shared().showDefaultHUDLong(withText: s)
        } else {
            RemindHUD.shared().showDefaultHUD(withText: s)
        }
    }

    func jumpSportDetail() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = SportAttendanceViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpTodoMain() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = ToDoVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpWeDate() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = WeDateVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpSchoolCalendar() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = CalendarViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpTestArrange() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = TestArrangeViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    // 找最顶层可 push 的 UINavigationController：key window → rootVC → 解 presentedVC →
    // 解 NavigationController。CMP 主页 rootVC 被 CustomNavigationController 包了一层
    // （见 AppDelegate.setupWindow），所以根上就能拿到 nav。
    private static func topNavigationController() -> UINavigationController? {
        var vc: UIViewController?
        if #available(iOS 13.0, *) {
            vc = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }?
                .rootViewController
        } else {
            vc = UIApplication.shared.keyWindow?.rootViewController
        }
        while let presented = vc?.presentedViewController { vc = presented }
        if let nav = vc as? UINavigationController { return nav }
        return vc?.navigationController
    }
}
