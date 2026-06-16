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

    func createTabBarController() -> UITabBarController {
        return TabBarController()
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
    // 解 TabBar.selectedVC → 取 NavigationController。
    // 在已 present 出 Compose 主页 + 仍保留 iOS TabBarController 的混合阶段也能用。
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
        if let tab = vc as? UITabBarController { vc = tab.selectedViewController }
        if let nav = vc as? UINavigationController { return nav }
        return vc?.navigationController
    }
}
