//
//  TabBarController.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/1.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import RYTransitioningDelegateSwift
import SwiftyJSON

open class TabBarController: UITabBarController {

    open override func viewDidLoad() {
        super.viewDidLoad()
        
        setupTabBar()
        setupViewControllers()
        setupLogin()
    }
    
    lazy var finderVC = RYFinderViewController()
    
    lazy var carnieVC = RYCarnieViewController()
    
    lazy var mineVC = MineViewController()
}

// MARK: reload

extension TabBarController {
    
    /* 缓存请求法
     会判断缓存，没缓存走请求法
     */
    func reloadData() {
        if let sno = UserModel.default.token?.stuNum,
           var scheduleModel = CacheManager.shared.getCodable(ScheduleModel.self, in: .schedule(sno: sno)) {

            let date = UserDefaultsManager.shared.latestRequestDate ?? Date()
            let days = Calendar.current.dateComponents([.day], from: Date(), to: date).day ?? 0
            scheduleModel.nowWeek += days / 7

            reloadWith(scheduleModel: scheduleModel)
        } else {
            request()
        }
        
        reloadSubControllers()
    }
    
    /* 直接请求法
     一般来说不主动掉用
     */
    func request() {
        if let sno = UserModel.default.token?.stuNum {
            ScheduleModel.request(sno: sno) { model in
                if let model {
                    UserDefaultsManager.shared.latestRequestDate = Date()
                    self.reloadWith(scheduleModel: model)
                } else {
                    self.reloadTabBarData(title: "网络连接失败", time: "请连接网络", place: "或开启流量")
                }
            }
        }
    }
    
    /* 根据数据源刷新
     给定一个ScheduleModel进行刷新
     */
    func reloadWith(scheduleModel: ScheduleModel) {
        if scheduleModel.customType == .system {
            UserModel.default.start = scheduleModel.start
        }
        
        let calModels = scheduleModel.calModels
        if let cur = ScheduleModel.calCourseWillBeTaking(with: calModels) {
            reloadTabBarData(title: cur.curriculum.course, time: cur.time, place: cur.curriculum.classRoom)
        } else {
            reloadTabBarData(title: "今天已经没课了", time: "好好休息吧", place: "明天新一天")
        }
    }
    
    /* 直接赋值法
     一般用于错误信息的时候可以直接赋值，不用掉用一堆API
     */
    func reloadTabBarData(title: String?, time: String?, place: String?) {
        tabBar.ryTabBar?.headerView.updateData(title: title, time: time, place: place)
    }
    
    func reloadSubControllers() {
        finderVC.reloadData()
    }
}

// MARK: setup

extension TabBarController {
    
    func setupTabBar() {
        let tabBar = TabBar()
        tabBar.bezierPathSetColor = .ry(light: "#E2EDFB", dark: "#7C7C7C")
        tabBar.backgroundColor = .ry(light: "#FFFFFF", dark: "2D2D2D")
        let pan = UIPanGestureRecognizer(target: self, action: #selector(response(pan:)))
        tabBar.headerView.addGestureRecognizer(pan)
        let tap = UITapGestureRecognizer(target: self, action: #selector(response(tap:)))
        tabBar.headerView.addGestureRecognizer(tap)
        setValue(tabBar, forKey: "tabBar")
    }
    
    func setupViewControllers() {
        let vcs = viewControllersForTabBar
        if vcs.count == 0 { return }
        let tabBarItems = tabBarItemsForTabBar
        for index in 0..<vcs.count {
            vcs[index].tabBarItem = tabBarItems[min(index, tabBarItems.count - 1)]
        }
        viewControllers = vcs
    }
    
    func setupLogin() {
        RYLoginViewController.check { shouldPresent, optionVC in
            if shouldPresent, let vc = optionVC {
                let nav = self.createVC(root: vc)
                nav.modalPresentationStyle = .fullScreen
                self.present(nav, animated: true)
            } else {
                self.reloadData()
                if !(UserDefaultsManager.shared.hasReadSportsCheckInInfo ?? false) {
                    self.showSANotice { success in
                        if success {
                            UserDefaultsManager.shared.hasReadSportsCheckInInfo = true
                        }
                        self.checkPresentSchedule()
                    }
                } else {
                    self.checkPresentSchedule()
                }
            }
        }
    }
    
    /// 是否需要弹出课表
    func checkPresentSchedule() {
        if UserDefaultsManager.shared.presentScheduleWhenOpenApp {
            self.presentSchedule()
        }
    }
    
    /// 体育打卡信息说明
    func showSANotice(completion: ((Bool) -> Void)?) {
        HttpManager.shared.magipoke_sport_notice().ry_JSON { [weak self] response in
            guard let self = self else { return }
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    let noticeItems = model["data"].arrayValue.map { NoticeItem(json: $0) }
                    let alertController = SAAlertController(noticeItems: noticeItems, completion: completion)
                    self.present(alertController, animated: true)
                }
            case .failure(let netError):
                print(netError)
                completion?(false)
            }
        }
    }
}

// MARK: creater

extension TabBarController {
    
    var viewControllersForTabBar: [UIViewController] {
        [
            createVC(root: finderVC),
            createVC(root: carnieVC),
            createVC(root: mineVC)
        ]
    }
    
    var tabBarItemsForTabBar: [UITabBarItem] {
        [
            createTabBar(title: "发现",
                         imageName: "TabBar_find_defualt",
                         selectedImageName: "TabBar_find_select"),
            
            createTabBar(title: "邮乐场",
                         imageName: "TabBar_carnie_defualt",
                         selectedImageName: "TabBar_carnie_select",
                         needMoreSpaceToShow: false),
            
            createTabBar(title: "我的",
                         imageName: "TabBar_mine_defualt",
                         selectedImageName: "TabBar_mine_select")
        ]
    }
    
    func createVC(root: UIViewController) -> UIViewController {
        let nav = CustomNavigationController(rootViewController: root)
        nav.isNavigationBarHidden = true
        return nav
    }
    
    func createTabBar(title: String?, imageName: String, selectedImageName: String, needMoreSpaceToShow: Bool = true) -> UITabBarItem {
        let image = UIImage(named: imageName)?.scaled(toHeight: 25)?.withRenderingMode(.alwaysOriginal)
        let selectedImage = UIImage(named: selectedImageName)?.scaled(toHeight: 25)?.withRenderingMode(.alwaysOriginal)
        let tabBarItem = UITabBarItem(title: title, image: image, selectedImage: selectedImage)
        tabBarItem.setTitleTextAttributes([
            .foregroundColor: UIColor.ry(light: "#AABCD8", dark: "#5A5A5A")
        ], for: .normal)
        tabBarItem.setTitleTextAttributes([
            .foregroundColor: UIColor.ry(light: "#2923D2", dark: "#465FFF")
        ], for: .selected)
        tabBarItem.needMoreSpaceToShow = needMoreSpaceToShow
        return tabBarItem
    }
}

// MARK: interactive

extension TabBarController {
    
    @objc
    func response(tap: UITapGestureRecognizer) {
        presentSchedule()
    }
    
    @objc
    func response(pan: UIPanGestureRecognizer) {
        if pan.state == .began {
            presentSchedule(pan: pan)
        }
    }
    
    func presentSchedule(pan: UIPanGestureRecognizer? = nil) {
        if presentedViewController != nil { return }
        
        let transitionDelegate = RYTransitioningDelegate()
        transitionDelegate.panInsetsIfNeeded = UIEdgeInsets(top: Constants.statusBarHeight, left: 0, bottom: tabBar.bounds.height, right: 0)
        transitionDelegate.supportedTapOutsideBackWhenPresent = false
        transitionDelegate.panGestureIfNeeded = pan
        transitionDelegate.present = { transition in
            transition.prepareAnimationAction = { context in
                guard let to = context.viewController(forKey: .to) else { return }
                guard let copyHeader = self.ry_tabBar?.headerView.copyByKeyedArchiver else { return }
                to.view.frame.origin.y = self.tabBar.frame.minY
                to.view.frame.size.height = copyHeader.bounds.height
                to.view.addSubview(copyHeader)
                if let presentationVC = to as? TabBarPresentationViewController {
                    presentationVC.scheduleVC.headerView.alpha = 0
                }
            }
            transition.finishAnimationAction = { context in
                guard let to = context.viewController(forKey: .to) else { return }
                let height = context.containerView.frame.maxY - Constants.statusBarHeight
                to.view.frame.origin.y = context.containerView.frame.maxY - height
                to.view.frame.size.height = height
                to.view.subviews.last?.alpha = 0
                if let presentationVC = to as? TabBarPresentationViewController {
                    presentationVC.scheduleVC.headerView.alpha = 1
                }
            }
            transition.completionAnimationAction = { context in
                if !context.transitionWasCancelled {
                    context.viewController(forKey: .to)?.view.subviews.last?.isHidden = true
                }
            }
        }
        
        let vc = TabBarPresentationViewController()
        vc.tabBarFrame = tabBar.frame
        vc.modalPresentationStyle = .custom
        vc.transitioningDelegate = transitionDelegate
//        vc.scheduleVC.requestCallBack = { vc in
//            if let mainModel = vc.fact.mappy.scheduleModelMap.first(where: { $0.key.sno == UserModel.default.token?.stuNum })?.key {
//                self.reloadWith(scheduleModel: mainModel)
//                self.reloadSubControllers()
//            }
//        }
        present(vc, animated: true)
    }
}

// MARK: UITabBarDelegate

extension TabBarController {
    
    open override func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        if let tabBar = tabBar as? TabBar {
            tabBar.isHeaderViewHidden = !item.needMoreSpaceToShow
        }
    }
}

// MARK: EX UITabBarController

extension UITabBarController {
    var ry_tabBar: TabBar? {
        tabBar as? TabBar
    }
}

// MARK: EX UIViewController

extension UIViewController {
    var ryTabBarController: TabBarController? {
        tabBarController as? TabBarController
    }
}
