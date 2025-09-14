//
//  QASearchMainVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

class QASearchMainVC: UIViewController {
    
    private var QASearchViewControllers: [QASearchViewController] = []
    private var segmentedDataSource: JXSegmentedActivityCustomDataSource!
    private var segmentedView: JXSegmentedView!
    private var listContainerView: JXSegmentedListContainerView!
    
    private let sharedQAModel = QAModel()
    
    // MARK: - 新增属性
    private var initialSearchKeyword: String?
    
    // MARK: - 新增初始化方法
    convenience init(initialSearchKeyword: String? = nil) {
        self.init()
        self.initialSearchKeyword = initialSearchKeyword
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#0E0E0E")
        view.addSubview(topView)
        topView.commonInit()
        addSegment()
        initVCs()
    }
    
    // MARK: - 加载视图
    lazy var topView: QAMainTopView = {
        let topView = QAMainTopView()
        topView.backgroundColor = .clear
        topView.frame = CGRectMake(0, 0, UIScreen.main.bounds.width, 100)
        topView.backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        return topView
    }()
    
    func addSegment() {
        segmentedView = JXSegmentedView()
        segmentedDataSource = JXSegmentedActivityCustomDataSource()
        segmentedDataSource.titles = ["全部","新生类","生活类","学习类","其他"]
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
        
        //自定义指示器
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
        //布局控件
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
        // 传递初始搜索词到各个分类的ViewController:cite[1]
        let allVC = QASearchViewController(qaType: .all, initialSearchKeyword: initialSearchKeyword)
        allVC.title = "全部"
        addChild(allVC)
        QASearchViewControllers.append(allVC)
        
        let freshManVC = QASearchViewController(qaType: .freshMen, initialSearchKeyword: initialSearchKeyword)
        freshManVC.title = "新生类"
        addChild(freshManVC)
        QASearchViewControllers.append(freshManVC)
        
        let lifeVC = QASearchViewController(qaType: .life, initialSearchKeyword: initialSearchKeyword)
        lifeVC.title = "生活类"
        addChild(lifeVC)
        QASearchViewControllers.append(lifeVC)
        
        let studyVC = QASearchViewController(qaType: .study, initialSearchKeyword: initialSearchKeyword)
        studyVC.title = "学习类"
        addChild(studyVC)
        QASearchViewControllers.append(studyVC)
        
        let otherVC = QASearchViewController(qaType: .other, initialSearchKeyword: initialSearchKeyword)
        otherVC.title = "其他"
        addChild(otherVC)
        QASearchViewControllers.append(otherVC)
    }
    
    @objc func popVC(){
        self.navigationController?.popViewController(animated: true)
    }
}

extension QASearchMainVC: JXSegmentedViewDelegate{
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5){
            if(self.QASearchViewControllers[index].qaModel.qa.count == 0){
                ActivityHUD.shared.showNoMoreData()
            }
        }
    }
}

extension QASearchMainVC: JXSegmentedListContainerViewDataSource{
    func listContainerView(_ listContainerView: JXSegmentedListContainerView, initListAt index: Int) -> JXSegmentedListContainerViewListDelegate {
        return QASearchViewControllers[index]
    }
    
    func numberOfLists(in listContainerView: JXSegmentedListContainerView) -> Int {
        return 5
    }
}
