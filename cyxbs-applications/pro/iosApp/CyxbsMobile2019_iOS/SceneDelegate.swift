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

        // 冷启动的 URL 会随 Scene 创建参数到达，CMP 首帧尚未建立时由 Kotlin 入口排队处理。
        openNavigationURL(from: connectionOptions.urlContexts)
    }

    /// 已运行的应用由 Scene 接收小组件链接，和冷启动共用同一条 CMP 导航入口。
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        openNavigationURL(from: URLContexts)
    }

    /// 仅转交 CMP 能解析的 URL；旧分享 scheme 不在这里被误识别为课程链接。
    private func openNavigationURL(from contexts: Set<UIOpenURLContext>) {
        for context in contexts where context.url.scheme?.lowercased() == "cyxbs" {
            if IOSAppKt.openExternalAppUrl(url: context.url.absoluteString) { break }
        }
    }
}
