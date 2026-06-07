//
//  WeDateVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

class WeDateVC: UIViewController {
    
    // MARK: - Life Cycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(topView)
        view.addSubview(segmentedView)
        view.addSubview(listContainerView)
        view.backgroundColor = .weDatePageBackground
        addShadow()
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        topView.frame = CGRect(x: 0, y: statusBarHeight + 6, width: SCREEN_WIDTH, height: 49)
        segmentedView.frame = CGRect(x: 0, y: topView.bottom, width: SCREEN_WIDTH, height: 34)
        listContainerView.frame = CGRect(x: 0, y: segmentedView.bottom, width: SCREEN_WIDTH, height: SCREEN_HEIGHT - segmentedView.bottom)
        
        let maskPath = UIBezierPath(roundedRect: segmentedView.bounds, byRoundingCorners: [.bottomLeft, .bottomRight], cornerRadii: CGSize(width: 16, height: 16))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        segmentedView.layer.mask = maskLayer
    }
    
    // MARK: - Method
    
    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    @objc private func clickBatchAddBtn() {
        navigationController?.pushViewController(BatchAddVC(), animated: true)
    }
    
    private func addShadow() {
        let gradientView = UIView(frame: CGRect(x: 0, y: statusBarHeight + 33, width: SCREEN_WIDTH, height: 85))
        let gradientLayer = CAGradientLayer()
        gradientLayer.frame = gradientView.bounds
        gradientLayer.colors = [
            UIColor(light: .hex("#EBF0F5"), dark: .hex("#000000").withAlphaComponent(0.35)).cgColor,
            UIColor(light: .hex("#EBF0F5").withAlphaComponent(0), dark: .hex("#000000").withAlphaComponent(0)).cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 0, y: 1)
        gradientView.layer.addSublayer(gradientLayer)
        view.insertSubview(gradientView, belowSubview: topView)
    }
    
    // MARK: - Lazy
    
    private lazy var topView: WeDateTopView = {
        let topView = WeDateTopView()
        topView.returnBtn.addTarget(self, action: #selector(clickReturnBtn), for: .touchUpInside)
        topView.batchAddBtn.addTarget(self, action: #selector(clickBatchAddBtn), for: .touchUpInside)
        topView.backgroundColor = .weDatePageBackground
        return topView
    }()
    
    private lazy var segmentedView: JXSegmentedView = {
        let indicator = JXSegmentedIndicatorImageView()
        indicator.image = UIImage(named: "选中效果")
        indicator.indicatorWidth = 66
        indicator.indicatorHeight = 3
        indicator.indicatorPosition = .bottom
        
        let segmentedView = JXSegmentedView()
        segmentedView.dataSource = segmentedDataSource
        segmentedView.indicators = [indicator]
        segmentedView.listContainer = listContainerView
        segmentedView.defaultSelectedIndex = 0
        segmentedView.backgroundColor = .weDatePageBackground
        return segmentedView
    }()
    
    private lazy var segmentedDataSource: JXSegmentedTitleDataSource = {
        let segmentedDataSource = JXSegmentedTitleDataSource()
        segmentedDataSource.titles = ["临时分组", "固定分组"]
        segmentedDataSource.titleNormalFont = .systemFont(ofSize: 18)
        segmentedDataSource.titleSelectedColor = .weDateTitleText
        segmentedDataSource.titleNormalColor = .weDateTitleText
        return segmentedDataSource
    }()
    
    private lazy var listContainerView: JXSegmentedListContainerView = {
        let listContainerView = JXSegmentedListContainerView(dataSource: self)
        listContainerView.scrollView.isScrollEnabled = false
        return listContainerView
    }()
}

// MARK: - JXSegmentedListContainerViewDataSource

extension WeDateVC: JXSegmentedListContainerViewDataSource {
    
    func numberOfLists(in listContainerView: JXSegmentedListContainerView) -> Int {
        return segmentedDataSource.titles.count
    }
    
    func listContainerView(_ listContainerView: JXSegmentedListContainerView, initListAt index: Int) -> JXSegmentedListContainerViewListDelegate {
        return index == 0 ? TemporaryGroupVC() : FixedGroupVC()
    }
}
