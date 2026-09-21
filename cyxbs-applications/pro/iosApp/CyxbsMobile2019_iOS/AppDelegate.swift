//
//  AppDelegate.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/1.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import XBSBugly
import CyxbsApplicationsMultiplatform

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    // 应用程序启动时调用的方法
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // 版本迁移会清空整个 UserDefaults，必须在写入网络环境前完成。
        // 提前初始化也避免首次登录才触发清理，导致旧原生模块丢失 baseURL。
        _ = CacheManager.shared
        // 网络环境必须先于 CMP 初始化，避免恢复登录态时旧原生模块读取不到 baseURL。
        setupAlicloudSDK() // 设置网络环境和阿里云SDK
        IOSAppKt.doInitApp(impl: KmpInterfaceImpl()) // Kotlin Multiplatform 工程初始化
        XBSBugly.buglyInit() // 设置bugly

        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(
            name: "Default Configuration",
            sessionRole: connectingSceneSession.role
        )
        configuration.delegateClass = SceneDelegate.self
        return configuration
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

    // 设置阿里云SDK
    func setupAlicloudSDK() {
        APIConfig.current.apply(APIConfig.current.environment)
        AliyunConfig.ip(byHost: APIConfig.current.environment.host)
    }

    // 设置结束操作
    func setupEnd() {
        UserDefaultsManager.shared.latestOpenApp = Date()
    }
}
