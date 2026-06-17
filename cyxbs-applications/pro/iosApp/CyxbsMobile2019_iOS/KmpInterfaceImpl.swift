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

    func launchNotification() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = MineMessageVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpCheckIn() {
        // 与原 RYFinderHeaderView.attendanceBtnTouched 一致：全屏 modal present 而不是 push。
        guard let topVC = Self.topViewController() else { return }
        let vc = CheckInViewController()
        vc.modalPresentationStyle = .fullScreen
        vc.hidesBottomBarWhenPushed = true
        topVC.present(vc, animated: true)
    }

    func jumpJwNewsList() {
        // iOS 原版 FinderNewsView 显示 "教务新闻功能暂时停止服务..." 且点击事件为空。
        // 这里给用户一个明确提示，对齐原版体验。
        toast(s: "教务新闻功能暂时停止服务", isLong: false)
    }

    func jumpJwNewsItem(newId: String) {
        // 同上：iOS 原版无详情页跳转。
        toast(s: "教务新闻功能暂时停止服务", isLong: false)
    }

    func onBannerClick(pictureGotoUrl: String, keyword: String) {
        // 与原 FinderBannerView.jxBanner(_:didSelectItemAt:) 一致：交给系统 Safari。
        // 这里不做埋点（Android 端 DiscoverNavPlatformImpl 里那段 TrackingUtils 是 Android 专用）。
        guard let url = URL(string: pictureGotoUrl) else { return }
        UIApplication.shared.open(url)
    }

    func jumpQaEntry() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = QAMainVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpUfieldMainEntry() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = ActivityMainViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    // 找最顶层可 push 的 UINavigationController：key window → rootVC → 解 presentedVC →
    // 解 NavigationController。CMP 主页 rootVC 被 CustomNavigationController 包了一层
    // （见 AppDelegate.setupWindow），所以根上就能拿到 nav。
    private static func topNavigationController() -> UINavigationController? {
        guard let vc = topViewController() else { return nil }
        if let nav = vc as? UINavigationController { return nav }
        return vc.navigationController
    }

    // 找最顶层 UIViewController：key window → rootVC → 递归解 presentedVC。
    // 用于 present 场景（不需要再解出 NavigationController）。
    private static func topViewController() -> UIViewController? {
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
        return vc
    }
}
