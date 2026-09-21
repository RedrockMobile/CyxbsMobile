//
//  SceneDelegate.swift
//  CyxbsMobile2019_iOS
//
//  Created by Codex on 2026/9/20.
//  Copyright © 2026 Redrock. All rights reserved.
//

import UIKit
import CyxbsApplicationsMultiplatform

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let rootVC = IOSAppKt.MainViewController() // 使用 CMP 主页
        // 扩展到安全区域以下显示
        rootVC.edgesForExtendedLayout = .all
        rootVC.extendedLayoutIncludesOpaqueBars = true

        // 用 CustomNavigationController 包一层，让 CMP 主页仍可跳转到体育打卡等原生页面。
        let nav = CustomNavigationController(rootViewController: rootVC)

        window = UIWindow(windowScene: windowScene)
        window?.rootViewController = nav
        window?.makeKeyAndVisible()
    }
}
