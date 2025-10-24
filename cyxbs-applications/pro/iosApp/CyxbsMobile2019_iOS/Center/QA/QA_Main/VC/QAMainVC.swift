//
//  QAMainVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/19.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

class QAMainVC: UIViewController {
    
    private var qaViewControllers: [QAViewController] = []
    private var segmentedDataSource: JXSegmentedActivityCustomDataSource!
    private var segmentedView: JXSegmentedView!
    private var listContainerView: JXSegmentedListContainerView!
    private let sharedQAModel = QAModel()
    private let newQACounter = NewQAItemCounter()
    private var unreadCounts: [Int] = [0, 0, 0, 0, 0] // 对应5个分类的未读数量
    private var badgeViews: [UILabel] = [] // 存储角标视图
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#0E0E0E")
        view.addSubview(topView)
        topView.commonInit()
        addSegment()
        view.addSubview(publishButton)
        setPosition()
        initVCs()
        
        // 加载未读数量
        loadUnreadCounts()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // 每次进入页面时检查是否有新内容
        loadUnreadCounts()
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // 更新角标位置
        updateBadgePositions()
    }
    
    // MARK: - 加载未读数量
    private func loadUnreadCounts() {
        newQACounter.requestData()
        
        // 模拟异步获取数据后的处理
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            guard let self = self else { return }
            
            // 获取各个分类的计数
            let freshMenCount = self.newQACounter.getCountForCategory("新生")
            let lifeCount = self.newQACounter.getCountForCategory("生活")
            let studyCount = self.newQACounter.getCountForCategory("学习")
            let otherCount = self.newQACounter.getCountForCategory("其他")
            let totalCount = freshMenCount + lifeCount + studyCount + otherCount
            
            // 更新未读数量数组
            self.unreadCounts = [totalCount, freshMenCount, lifeCount, studyCount, otherCount]
            
            // 更新角标显示
            self.updateBadges()
            
            // 更新最新请求时间
            if #available(iOS 15, *) {
                self.newQACounter.initDate()
            } else {
                let currentDate = Date()
                UserDefaultsManager.shared.latestRequestQA = currentDate
            }
        }
    }
    
    // MARK: - 创建角标视图
    private func createBadgeViews() {
        // 清除现有角标
        badgeViews.forEach { $0.removeFromSuperview() }
        badgeViews.removeAll()
        
        // 只为后4个分段项创建角标（跳过"全部"）
        for _ in 0..<4 {
            let badge = UILabel()
            badge.backgroundColor = UIColor(hexString: "#4A44E4")
            badge.textColor = .white
            badge.font = UIFont(name: PingFangSC, size: 10)
            badge.textAlignment = .center
            badge.layer.cornerRadius = 7
            badge.layer.masksToBounds = true
            badge.isHidden = true
            badge.frame = CGRect(x: 0, y: 0, width: 21, height: 14)
            segmentedView.addSubview(badge)
            badgeViews.append(badge)
        }
    }
    
    // MARK: - 更新角标显示
    private func updateBadges() {
        // 确保角标视图已创建
        if badgeViews.isEmpty {
            createBadgeViews()
        }
        
        // 更新每个角标的显示（跳过"全部"分类，索引0）
        for (index, count) in unreadCounts.enumerated() {
            // 跳过"全部"分类（索引0）
            if index == 0 {
                continue
            }
            
            // 调整索引，因为角标视图只有4个（对应后4个分类）
            let badgeIndex = index - 1
            
            if count > 0 && badgeIndex < badgeViews.count {
                badgeViews[badgeIndex].isHidden = false
                badgeViews[badgeIndex].text = "\(count)"
                // 根据数字长度调整角标宽度
                let width = max(21, 10 + 8 * (count > 9 ? 1 : 0))
                badgeViews[badgeIndex].frame.size.width = CGFloat(width)
                badgeViews[badgeIndex].layer.cornerRadius = 7
            } else if badgeIndex < badgeViews.count {
                badgeViews[badgeIndex].isHidden = true
            }
        }
        
        // 更新角标位置
        updateBadgePositions()
    }
    
    // MARK: - 更新角标位置
    private func updateBadgePositions() {
        guard !badgeViews.isEmpty else { return }
        
        // 获取分段视图的项数
        let itemCount = segmentedDataSource.titles.count
        guard itemCount > 0 else { return }
        
        // 计算每个项的宽度
        let itemWidth = segmentedView.bounds.width / CGFloat(itemCount)
        
        // 更新每个角标的位置（跳过"全部"分类，索引0）
        for (index, badge) in badgeViews.enumerated() {
            // 调整索引，从第二个分类开始（索引1）
            let categoryIndex = index + 1
            
            if categoryIndex < itemCount {
                // 计算项的中心X坐标
                let centerX = itemWidth * CGFloat(categoryIndex) + itemWidth / 2
                
                // 计算角标的位置（在项标题的右上角）
                let badgeX = centerX + 30 // 向右偏移30点
                let badgeY: CGFloat = 10   // 顶部偏移10点
                
                badge.center = CGPoint(x: badgeX, y: badgeY)
            }
        }
    }
    
    // MARK: - 清除指定分类的未读计数
    private func clearUnreadCount(for index: Int) {
        guard index >= 0 && index < unreadCounts.count else { return }
        
        // 如果点击的是"全部"分类，清除所有未读
        if index == 0 {
            for i in 0..<unreadCounts.count {
                unreadCounts[i] = 0
                
                // 对于后4个分类，隐藏角标
                if i > 0 && (i-1) < badgeViews.count {
                    badgeViews[i-1].isHidden = true
                }
            }
        } else {
            // 清除指定分类的未读
            unreadCounts[index] = 0
            
            // 隐藏对应角标
            if (index-1) < badgeViews.count {
                badgeViews[index-1].isHidden = true
            }
            
            // 更新"全部"分类的未读数量（虽然不显示角标，但仍需记录）
            let totalCount = unreadCounts[1] + unreadCounts[2] + unreadCounts[3] + unreadCounts[4]
            unreadCounts[0] = totalCount
        }
    }
    
    // MARK: - 加载视图
    
    func setPosition() {
        publishButton.snp.makeConstraints{ make in
            make.bottom.equalTo(view.snp.bottom).offset(-60)
            make.centerX.equalTo(view.snp.centerX)
            make.width.equalTo(162.9)
            make.height.equalTo(41)
        }
    }
    
    lazy var topView: QAMainTopView = {
        let topView = QAMainTopView()
        topView.backgroundColor = .clear
        topView.frame = CGRectMake(0, 0, UIScreen.main.bounds.width, 100)
        topView.backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        return topView
    }()
    
    lazy var publishButton: UIButton = {
        let publishButton = UIButton()
        publishButton.backgroundColor = UIColor(hexString: "#4841E2")
        publishButton.setTitle("发布问题", for: .normal)
        publishButton.setTitleColor(.white, for: .normal)
        publishButton.setImage(UIImage(named: "Edit"), for: .normal)
        publishButton.imageEdgeInsets = UIEdgeInsets(top: 0, left: -10, bottom: 0, right: 0)
        publishButton.titleEdgeInsets = UIEdgeInsets(top: 0, left: 10, bottom: 0, right: -10)
        publishButton.layer.cornerRadius = 20.5
        publishButton.layer.masksToBounds = true
        publishButton.addTarget(self, action: #selector(publish), for: .touchUpInside)
        return publishButton
    }()
    
    func addSegment() {
        segmentedView = JXSegmentedView()
        segmentedDataSource = JXSegmentedActivityCustomDataSource()
        segmentedDataSource.titles = ["全部", "新生类", "生活类", "学习类", "其他"]
        segmentedDataSource.titleNormalFont = UIFont(name: PingFangSC, size: 16)!
        segmentedDataSource.titleNormalColor = UIColor.ry(light: "#15315B", dark: "#FFFFFF")
        segmentedDataSource.titleSelectedColor = UIColor.ry(light: "#15315B", dark: "#FFFFFF")
        segmentedDataSource.isTitleColorGradientEnabled = false
        segmentedDataSource.isBackGroundColorGradientEnabled = false
        segmentedDataSource.isItemSpacingAverageEnabled = true
        segmentedDataSource.itemWidthIncrement = 34
        segmentedDataSource.itemSpacing = 0
        segmentedDataSource.backGroundNormalColor = .clear
        segmentedDataSource.backGroundSelectedColor = .clear
        
        // 自定义指示器
        let indicator = JXSegmentedIndicatorImageView()
        indicator.indicatorWidth = 48
        indicator.indicatorHeight = 4
        
        let indicatorImage = UIImage(named: "Selected")
        indicator.image = indicatorImage
        
        segmentedView.indicators = [indicator]
        
        segmentedView.delegate = self
        segmentedView.dataSource = segmentedDataSource
        view.addSubview(segmentedView)
        listContainerView = JXSegmentedListContainerView(dataSource: self)
        view.addSubview(listContainerView)
        segmentedView.listContainer = listContainerView
        
        // 布局控件
        segmentedView.snp.makeConstraints{ make in
            make.width.equalToSuperview()
            make.height.equalTo(40)
            make.top.equalTo(100)
        }
        listContainerView.snp.makeConstraints{ make in
            make.width.equalToSuperview()
            make.top.equalTo(segmentedView.snp.bottom)
            make.bottom.equalToSuperview()
        }
    }
    
    func initVCs() {
        let allVC = QAViewController(qaType: .all)
        allVC.title = "全部"
        addChild(allVC)
        qaViewControllers.append(allVC)
        
        let freshManVC = QAViewController(qaType: .freshMen)
        freshManVC.title = "新生类"
        addChild(freshManVC)
        qaViewControllers.append(freshManVC)
        
        let lifeVC = QAViewController(qaType: .life)
        lifeVC.title = "生活类"
        addChild(lifeVC)
        qaViewControllers.append(lifeVC)
        
        let studyVC = QAViewController(qaType: .study)
        studyVC.title = "学习类"
        addChild(studyVC)
        qaViewControllers.append(studyVC)
        
        let otherVC = QAViewController(qaType: .other)
        otherVC.title = "其他"
        addChild(otherVC)
        qaViewControllers.append(otherVC)
    }
    
    @objc func popVC() {
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func publish() {
        let newQuestionPage = NewQuestionVC()
        self.navigationController?.pushViewController(newQuestionPage, animated: true)
    }
}

extension QAMainVC: JXSegmentedViewDelegate {
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        // 清除当前选中分类的未读计数
        clearUnreadCount(for: index)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if self.qaViewControllers[index].qaModel.qa.count == 0 {
                ActivityHUD.shared.showNoMoreData()
            }
        }
    }
}

extension QAMainVC: JXSegmentedListContainerViewDataSource {
    func listContainerView(_ listContainerView: JXSegmentedListContainerView, initListAt index: Int) -> JXSegmentedListContainerViewListDelegate {
        return qaViewControllers[index]
    }
    
    func numberOfLists(in listContainerView: JXSegmentedListContainerView) -> Int {
        return 5
    }
}
