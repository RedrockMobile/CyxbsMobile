//
// Created by 郭祥瑞 on 2025/10/31.
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
    }

    func getDefaultExpandCourse() -> Bool {
        return false
    }

    func enableUsePlatformToast() -> Bool {
        return false
    }

    func toast(s: String, isLong: Bool) {
    }

    // 以下跳转方法是 multiplatform 独立测试 stub 的空实现，与 pro/iosApp 的 KmpInterfaceImpl
    // 不同：这里不接 bridging-header / 业务 VC，只满足 IOSKmpInterface 协议。

    func jumpSportDetail() {
    }

    func jumpTodoMain() {
    }

    func jumpWeDate() {
    }

    func jumpSchoolCalendar() {
    }

    func jumpTestArrange() {
    }

    func launchNotification() {
    }

    func jumpCheckIn() {
    }

    func jumpJwNewsList() {
    }

    func jumpJwNewsItem(newId: String) {
    }

    func onBannerClick(pictureGotoUrl: String, keyword: String) {
    }

    func jumpQaEntry() {
    }

    func jumpUfieldMainEntry() {
    }

    func jumpStore() {
    }

    func jumpFeedbackCenter() {
    }

    func jumpSign() {
    }

    func jumpSetting() {
    }

    func jumpActivityCenter() {
    }

    func onLessonUpdated(stuNum: String, nowWeek: Int32, stuLessonBeanJson: String) {
    }
}

