//
//  LoginOut.swift
//  CyxbsMobile2019_iOS
//
//  Created by 潘申冰 on 2023/11/10.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import CyxbsApplicationsMultiplatform

class SwiftToOC: NSObject {

    // 退出登录
    @objc
    class func loginOut() {
        UserModel.default.logout()
        
//        let loginVC = RYLoginViewController()
//        loginVC.modalPresentationStyle = .fullScreen
//
//        guard let tabBarVC = UIApplication.shared.delegate?.window??.rootViewController as? UITabBarController else {
//            return
//        }
//        
//        if tabBarVC.presentedViewController != nil {
//            tabBarVC.dismiss(animated: true) {
//                tabBarVC.present(loginVC, animated: true, completion: nil)
//            }
//        } else {
//            tabBarVC.present(loginVC, animated: true, completion: nil)
//        }
        // 跳转到 Compose 的登录页
        IOSAppKt.onLogout()
        
        // 假销毁单例
        UserItem.default().attemptDealloc()
        
        // 删除偏好设置，删除时保留 baseURL 和 版本号 的偏好信息
        let dic = UserDefaults.standard.dictionaryRepresentation()
        for (key, _) in dic {
            if key != "baseURL" && key != "BUNDLE_SHORT_VERSION" {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }
        
        // 删除归档
        let filePath = "\(NSTemporaryDirectory())" + "UserItem.data"
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: filePath) {
            do {
                try fileManager.removeItem(atPath: filePath)
            } catch {
                print("Error removing file: \(error)")
            }
        }
        
        print(NSHomeDirectory())
        
        let remAndLesDataDirectoryPath = (NSHomeDirectory() as NSString).appendingPathComponent("Documents/rem&les")
        
        // 清除课表数据和备忘数据
        try? FileManager.default.removeItem(atPath: remAndLesDataDirectoryPath)
        
        // 清除ToDo
        TodoSyncTool.share().logOutSuccess()
    
        // 标记为未读
//        UserDefaultsManager.shared.didReadUserAgreementBefore = false
    }
    
    @objc
    class func getNowWeek() -> Int {
        return UserModel.default.nowWeek() ?? 0
    }
    
    @objc 
    class func getStartDate() -> NSDate {
        let nsDate = NSDate(timeIntervalSince1970: UserModel.default.start?.timeIntervalSince1970 ?? 0)
        return nsDate
    }
}
