//
//  QAMainVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/19.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView


class QAMainVC : UIViewController {
    
    private var qaViewControllers: [QAViewController] = []
    private var segmentedDataSource: JXSegmentedActivityCustomDataSource!
    private var segmentedView: JXSegmentedView!
    private var listContainerView: JXSegmentedListContainerView!
    
    private let sharedQAModel = QAModel()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#0E0E0E")
        view.addSubview(topView)
        topView.commonInit()
        addSegment()
        view.addSubview(publishButton)
        setPosition()
        initVCs()
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
    
    lazy var publishButton : UIButton = {
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
    
    @objc func popVC(){
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func publish(){
        let newQuestionPage = NewQuestionVC()
        self.navigationController?.pushViewController(newQuestionPage, animated: true)
    }
    
}

extension QAMainVC: JXSegmentedViewDelegate{
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5){
            if(self.qaViewControllers[index].qaModel.qa.count == 0){
                ActivityHUD.shared.showNoMoreData()
            }
        }
    }
}

extension QAMainVC: JXSegmentedListContainerViewDataSource{
    func listContainerView(_ listContainerView: JXSegmentedListContainerView, initListAt index: Int) -> JXSegmentedListContainerViewListDelegate {
        return qaViewControllers[index]
    }
    
    func numberOfLists(in listContainerView: JXSegmentedListContainerView) -> Int {
        return 5
    }
}
