//
//  RYCarnieViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/15.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import ProgressHUD

class RYCarnieViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.backgroundColor = .ry(light: "#F2F3F8", dark: "#000000")
        
        view.addSubview(contentScrollView)
        
        setupUI()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        request()
    }
    
    lazy var contentScrollView: UIScrollView = {
        let scrollView = UIScrollView(frame: view.bounds)
        scrollView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrollView.backgroundColor = .clear
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.contentInsetAdjustmentBehavior = .never
        return scrollView
    }()
    
    lazy var headerView: RYCarnieHeaderView = {
        let space: CGFloat = 16
        let headerView = RYCarnieHeaderView(frame: CGRect(x: space, y: Constants.statusBarHeight + 28, width: view.bounds.width - 2 * space, height: 77))
        return headerView
    }()
    
    lazy var foodEntryView: RYCarnieEntryView = {
        let size = CGSize(width: 168, height: 220)
        let width = size.width / UI_width * view.bounds.width
        let height = size.height / size.width * width
        let entryView = RYCarnieEntryView(frame: CGRect(x: 17, y: headerView.frame.maxY + 17, width: width, height: height))
        entryView.setupData(imgName: "carnie_food", title: "美食资讯处")
        entryView.imgView.frame.size = CGSize(width: entryView.bounds.width, height: 210)
        entryView.titleLab.frame.origin = CGPoint(x: 4, y: entryView.bounds.height - entryView.titleLab.bounds.height)
        entryView.addTarget(self, action: #selector(tapFoodEntry), for: .touchUpInside)
        return entryView
    }()
    
    lazy var statementEntryView: RYCarnieEntryView = {
        let size = CGSize(width: 154, height: 289)
        let width = size.width / UI_width * view.bounds.width
        let height = size.height / size.width * width
        let left = 197.0 / UI_width * view.bounds.width
        let entryView = RYCarnieEntryView(frame: CGRect(x: left, y: headerView.frame.maxY + 72, width: width, height: height))
        entryView.setupData(imgName: "carnie_statement", title: "答疑广场")
        entryView.imgView.frame.size = entryView.bounds.size
        entryView.titleLab.center.x = entryView.bounds.width / 2
        entryView.titleLab.frame.origin.y = entryView.bounds.height - entryView.titleLab.bounds.height - 14
        entryView.addTarget(self, action: #selector(tapStatementEntry), for: .touchUpInside)
        return entryView
    }()
    
    lazy var eventEntryView: RYCarnieEntryView = {
        let size = CGSize(width: 172, height: 188)
        let width = size.width / UI_width * view.bounds.width
        let height = size.height / size.width * width
        let entryView = RYCarnieEntryView(frame: CGRect(x: 37, y: headerView.frame.maxY + 343, width: width, height: height))
        entryView.setupData(imgName: "carnie_event", title: "活动布告栏")
        entryView.imgView.frame.size = CGSize(width: entryView.bounds.width, height: 171)
        entryView.titleLab.frame.origin = CGPoint(x: 8, y: entryView.bounds.height - entryView.titleLab.bounds.height)
        entryView.addTarget(self, action: #selector(tapEventEntry), for: .touchUpInside)
        return entryView
    }()
}

// MARK: data

extension RYCarnieViewController {
    
    var UI_width: CGFloat { 375 }
    
    func setupUI() {
        contentScrollView.addSubview(headerView)
        contentScrollView.addSubview(foodEntryView)
        contentScrollView.addSubview(statementEntryView)
        contentScrollView.addSubview(eventEntryView)
        
        contentScrollView.contentSize.height = eventEntryView.frame.maxY + 30 + Constants.statusBarHeight
    }
    
    func request() {
        
        HttpManager.shared.magipoke_playground_center_days().ry_JSON { response in
            if case .success(let model) = response, model["status"].intValue == 10000 {
                UserDefaultsManager.shared.daysOfEntryCarnie = model["data"]["days"].intValue
            } else {
                if Calendar.current.isDateInToday(UserDefaultsManager.shared.latestRequestDate ?? Date()) {
                    UserDefaultsManager.shared.daysOfEntryCarnie += 1
                }
            }
            
            if let person = UserModel.default.person {
                self.setupData(person: person)
            } else {
                HttpManager.shared.magipoke_Person_Search().ry_JSON { response in
                    if case .success(let model) = response, model["status"].stringValue == "10000" {
                        let person = PersonModel(json: model["data"])
                        UserModel.default.person = person
                        self.setupData(person: person)
                    }
                }
            }
        }
    }
    
    func setupData(person: PersonModel) {
        headerView.update(imgURL: person.photo_src, title: "Hi~，\(person.username)", days: UserDefaultsManager.shared.daysOfEntryCarnie)
    }
}

// MARK: interactive

extension RYCarnieViewController {
    
    @objc
    func tapFoodEntry() {
        // 美食
        let vc = FoodVC()
        vc.hidesBottomBarWhenPushed = true
        navigationController?.pushViewController(vc, animated: true)
    }
    
    @objc
    func tapStatementEntry() {
        // 表态
//        ProgressHUD.showError("正在加紧建设中...")
        let vc = QAMainVC()
        vc.hidesBottomBarWhenPushed = true
        navigationController?.pushViewController(vc, animated: true)
    }
    
    @objc
    func tapEventEntry() {
        // 活动
        let vc = ActivityMainViewController()
        vc.hidesBottomBarWhenPushed = true
        navigationController?.pushViewController(vc, animated: true)
    }
}
