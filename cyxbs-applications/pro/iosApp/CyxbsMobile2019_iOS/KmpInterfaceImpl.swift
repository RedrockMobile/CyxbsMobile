//
// Created by 郭祥瑞 on 2025/10/27.
// Copyright (c) 2025 Redrock. All rights reserved.
//

import Foundation
import UIKit
import CyxbsApplicationsPro

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

    func toast(s: String, isLong: Bool) {
        if isLong {
            RemindHUD.shared().showDefaultHUDLong(withText: s)
        } else {
            RemindHUD.shared().showDefaultHUD(withText: s)
        }
    }
}
