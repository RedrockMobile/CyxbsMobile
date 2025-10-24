//
//  AppDelegate.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/1.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import XBSBugly
// import CyxbsApplicationsPro

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var window: UIWindow?
    
    // 应用程序启动时调用的方法
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        setupWindow() // 设置应用程序窗口
        setupAlicloudSDK() // 设置阿里云SDK
        XBSBugly.buglyInit() // 设置bugly
        // IOSAppKt.doInitApp() // Kotlin Multiplatform 工程初始化
        
        return true
    }
    
    // 当应用程序从后台进入前台时调用的方法
    func applicationWillEnterForeground(_ application: UIApplication) {
        //检查token是否过期
        RYLoginViewController.checkToken(rootVC: window?.rootViewController)
        //更新Tabbar上方的课表topView显示的课程信息
        if let vc = window?.rootViewController as? TabBarController {
            vc.reloadData()
        }
    }
    
    // 当应用程序进入后台时调用的方法
    func applicationDidEnterBackground(_ application: UIApplication) {
        setupEnd() // 设置结束操作
    }
    
    // 当应用程序终止时调用的方法
    func applicationWillTerminate(_ application: UIApplication) {
        setupEnd() // 设置结束操作
    }
}

// MARK: - 设置

extension AppDelegate {
    
    // 设置应用程序窗口
    func setupWindow() {
        let rootVC = TabBarController()
        
        window = UIWindow()
        window?.rootViewController = rootVC
        window?.makeKeyAndVisible()
    }
    
    // 设置阿里云SDK
    func setupAlicloudSDK() {
        AliyunConfig.ip(byHost: APIConfig.current.environment.host)
        let baseURL = APIConfig.current.environment.url + "/"
        UserDefaultsManager.shared.set(baseURL, forKey: "baseURL")
    }
    
    // 设置结束操作
    func setupEnd() {
        UserDefaultsManager.shared.latestOpenApp = Date()
    }
}
