//
// Created by 郭祥瑞 on 2025/10/27.
// Copyright (c) 2025 Redrock. All rights reserved.
//

import Foundation
import UIKit
import SwiftyJSON
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

    func jumpStore() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = StampCenterVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpFeedbackCenter() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = FeedBackMainPageViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpSign() {
        // 与原 MineViewController.signViewClicked 一致：push 进 CheckInVC，
        // 与 jumpCheckIn 的「present 全屏」不同（原版我的页是 push、发现页是 present）。
        guard let nav = Self.topNavigationController() else { return }
        let vc = CheckInViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpSetting() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = MineSettingViewController()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    func jumpActivityCenter() {
        guard let nav = Self.topNavigationController() else { return }
        let vc = ActivityCenterVC()
        vc.hidesBottomBarWhenPushed = true
        nav.pushViewController(vc, animated: true)
    }

    // 登录成功后同步 iOS 原生数据：待办、用户信息、Person 模型、邮箱绑定检查、登录时间。
    // 对齐 RYLoginViewController.loginIfNeeded() 成功后的处理逻辑。
    func onLoginSuccess(stuNum: String) {
        TodoSyncTool.share().logInSuccess()
        UserItem.default().getUserInfo()
        updatePersonModel()
        checkoutEmailBinding()
        UserDefaultsManager.shared.latestRequestToken = Date()
    }

    // 跳转找回密码页面。
    // 对齐 RYLoginViewController.forgotPassword()：先查询绑定状态，
    // 有邮箱或密保则 push ForgotViewController，否则提示联系 QQ 群。
    func jumpForgotPassword(stuNum: String) {
        guard let nav = Self.topNavigationController() else { return }

        let alertVC = UIAlertController(title: "忘记密码？", message: "输入你的学号，找回你的密码！", preferredStyle: .alert)
        alertVC.addTextField { textField in
            textField.keyboardType = .asciiCapable
            textField.placeholder = "输入你的学号"
            textField.text = stuNum.isEmpty ? nil : stuNum
        }
        let cancel = UIAlertAction(title: "取消", style: .cancel)
        let sure = UIAlertAction(title: "确定", style: .default) { _ in
            let inputStuNum = alertVC.textFields?.first?.text
            self.requestForgotPassword(sno: inputStuNum, nav: nav)
        }
        alertVC.addAction(cancel)
        alertVC.addAction(sure)

        guard let topVC = Self.topViewController() else { return }
        topVC.present(alertVC, animated: true)
    }

    // 打开用户协议页面
    func jumpUserAgreement() {
        guard let url = RYLoginViewController.agreementURL else { return }
        UIApplication.shared.open(url)
    }

    // 打开隐私政策页面
    func jumpPrivacyPolicy() {
        guard let url = RYLoginViewController.privacyURL else { return }
        UIApplication.shared.open(url)
    }

    // 退出应用（用户不同意用户协议时调用）
    func exitApp() {
        exit(0)
    }

    // CMP 课表请求成功后回调（cyxbs-pages/course LessonRepository.requestLesson 的 onSuccess
    // 之一）。把 StuLessonBean JSON 解析为旧 ScheduleModel，写入 App Group 共享缓存
    // （CacheManager.FilePath.schedule(sno:)），供 CyxbsWidgetExtension 课表小组件读取。
    //
    // 旧 CurriculumModel(json:) 的字段与 StuLessonBean.StuLesson 的 @SerialName 完全一致
    // （hash_day / begin_lesson / week / course / classroom / type / course_num / rawWeek
    // / teacher），无需再做字段适配。student 字段缺失时复用之前缓存。
    func onLessonUpdated(stuNum: String, nowWeek: Int32, stuLessonBeanJson: String) {
        guard let data = stuLessonBeanJson.data(using: .utf8),
              let root = try? JSON(data: data) else { return }

        var model = ScheduleModel(sno: stuNum)
        model.nowWeek = Int(nowWeek)
        model.curriculum = root["data"].arrayValue.map(CurriculumModel.init(json:))
        model.student = CacheManager.shared.getCodable(
            SearchStudentModel.self,
            in: .searchStudent(sno: stuNum)
        )
        CacheManager.shared.cache(codable: model, in: .schedule(sno: stuNum))
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

    // 更新 Person 模型，对齐 RYLoginViewController.updatePersonModel()。
    private func updatePersonModel() {
        HttpManager.shared.magipoke_Person_Search().ry_JSON { response in
            if case .success(let model) = response, model["status"].stringValue == "10000" {
                let person = PersonModel(json: model["data"])
                UserModel.default.person = person
            }
        }
    }

    // 检查邮箱绑定，未绑定则 push EmailBidingViewController。
    // 对齐 RYLoginViewController.checktoutEmailBiding()。
    private func checkoutEmailBinding() {
        EmailBidingViewController.isBiding { response in
            if let response, !response.email {
                guard let nav = Self.topNavigationController() else { return }
                let vc = EmailBidingViewController()
                vc.hidesBottomBarWhenPushed = true
                nav.pushViewController(vc, animated: true)
            }
        }
    }

    // 找回密码：查询绑定状态，有邮箱或密保则 push ForgotViewController，否则提示联系 QQ 群。
    // 对齐 RYLoginViewController.requestToForgot()。
    private func requestForgotPassword(sno: String?, nav: UINavigationController) {
        guard let sno = sno, !sno.isEmpty else {
            toast(s: "学号有误！！！", isLong: false)
            return
        }

        EmailBidingViewController.isBiding(sno: sno) { bidingType in
            guard let bidingType = bidingType else {
                self.toast(s: "学号有误！！！", isLong: false)
                return
            }

            if !bidingType.email && !bidingType.question {
                // 未绑定邮箱和密保，提示联系 QQ 群
                let alertVC = UIAlertController(
                    title: "无法找回密码",
                    message: "你从未绑定过你的邮箱，也未绑定过问题，请添加我们的QQ群，联系相关人员进行找回。QQ群:\(BaseTextFiledViewController.forgotQQGroup)",
                    preferredStyle: .alert
                )
                let cancel = UIAlertAction(title: "确定", style: .cancel)
                alertVC.addAction(cancel)
                Self.topViewController()?.present(alertVC, animated: true)
                return
            }

            let vc = ForgotViewController(sno: sno, bidingType: bidingType)
            if !bidingType.email {
                vc.retrieveWay = .question
            }
            vc.hidesBottomBarWhenPushed = true
            nav.pushViewController(vc, animated: true)
        }
    }
}
