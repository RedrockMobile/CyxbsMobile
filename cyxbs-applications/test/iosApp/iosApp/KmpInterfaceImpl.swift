//
// Created by 郭祥瑞 on 2025/10/31.
//

import Foundation
import UIKit
import CyxbsApplicationsTest

class KmpInterfaceImpl: IOSKmpInterface {

    func isDebug() -> Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }
}

