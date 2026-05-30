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

    func createTabBarController() -> UITabBarController {
        return UITabBarController()
    }

    func getDefaultExpandCourse() -> Bool {
        return false
    }

    func enableUsePlatformToast() -> Bool {
        return false
    }

    func toast(s: String, isLong: Bool) {
    }
}

