//
//  ActivityViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by 许晋嘉 on 2023/8/13.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView
import ProgressHUD

class ActivityMainViewController: UIViewController {
    
    private var collectionViewControllers: [ActivityCollectionVC] = []
    private var segmentedDataSource: JXSegmentedActivityCustomDataSource!
    private var segmentedView: JXSegmentedView!
    private var listContainerView: JXSegmentedListContainerView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        if #available(iOS 11.0, *) {
            view.backgroundColor = UIColor.dm_color(withLightColor: UIColor(hexString: "#FFFFFF")!, darkColor: UIColor(hexString: "#FFFFFF")!, alpha: 1)
        }
        else {
            view.backgroundColor = UIColor(hexString: "#FFFFFF"	)
        }
        addTopView()
        isAdmin()
        initVCs()
        addSegmentView()
    }
    
    // MARK: - 懒加载
    //顶部视图
    lazy var topView: ActivityTopView = {
        let topView = ActivityTopView(frame: CGRectMake(0, 0, view.bounds.width, 112 + Constants.statusBarHeight))
        topView.backButton.addTarget(self, action: #selector(popController), for: .touchUpInside)
        topView.searchButton.addTarget(self, action: #selector(pushSearchVC), for: .touchUpInside)
        topView.addActivityButton.addTarget(self, action: #selector(pushAddVC), for: .touchUpInside)
        topView.activityHitButton.addTarget(self, action: #selector(pushHitVC), for: .touchUpInside)
        topView.adminButton.addTarget(self, action: #selector(pushAdminVC), for: .touchUpInside)
        return topView
    }()
    //返回上一个VC
    @objc func popController() {
        self.navigationController?.popViewController(animated: true)
    }
    //跳转添加活动页面
    @objc func pushAddVC() {
        let addVC = ActivityUploadVC()
        self.navigationController?.pushViewController(addVC, animated: true)
    }
    //跳转排行榜页面
    @objc func pushHitVC() {
        let hitVC = ActivityRankingListVC()
        self.navigationController?.pushViewController(hitVC, animated: true)
    }
    //跳转搜索活动页面
    @objc func pushSearchVC() {
        let searchVC = ActivitySearchVC()
        self.navigationController?.pushViewController(searchVC, animated: true)
    }
    //跳转管理员页面
    @objc func pushAdminVC() {
        let adminVC = ActivityAdminManageMainVC()
        self.navigationController?.pushViewController(adminVC, animated: true)
    }
    
    // 添加顶部视图
    func addTopView() {
        view.addSubview(topView)
        // 创建圆角路径，将左下角和右下角设置为圆角
        let cornerRadius: CGFloat = 20
        let shadowPath = UIBezierPath(roundedRect: topView.bounds, byRoundingCorners: [.bottomLeft, .bottomRight], cornerRadii: CGSize(width: cornerRadius, height: cornerRadius))
        // 创建阴影图层
        let shadowLayer = CAShapeLayer()
        shadowLayer.path = shadowPath.cgPath
        shadowLayer.fillColor = UIColor.white.cgColor
        // 设置阴影属性
        shadowLayer.shadowColor = UIColor.gray.cgColor
        shadowLayer.shadowOpacity = 0.1
        shadowLayer.shadowOffset = CGSize(width: 0, height: 2)
        shadowLayer.shadowRadius = 4
        // 将阴影图层插入到顶部视图的图层中，使阴影位于底部
        topView.layer.insertSublayer(shadowLayer, at: 0)
    }
    
    func isAdmin() {
        HttpManager.shared.magipoke_ufield_isadmin().ry_JSON { response in
            switch response {
            case .success(let jsonData):
                let adminResponseData = AdminResponseData(from: jsonData)
                if (adminResponseData.data?.admin ?? false) {
                    self.topView.adAdminButton()
                }
                break
            case .failure(let error):
                print(error)
                break
            }
        }
    }
    
    func addSegmentView() {
        //初始化segmentedView
        segmentedView = JXSegmentedView()
        segmentedDataSource = JXSegmentedActivityCustomDataSource()
        segmentedDataSource.titles = ["全部","文娱活动","体育活动","教育活动"]
        segmentedDataSource.titleNormalFont = UIFont(name: PingFangSCMedium, size: 14)!
        segmentedDataSource.titleNormalColor = UIColor(hexString: "#2A4E84", alpha: 0.5)
        segmentedDataSource.titleSelectedColor = UIColor(hexString: "#4F4AE9", alpha: 1)
        segmentedDataSource.isTitleColorGradientEnabled = true
        segmentedDataSource.isBackGroundColorGradientEnabled = true
        segmentedDataSource.isItemSpacingAverageEnabled = true
        segmentedDataSource.isSelectedAnimable = true
        segmentedDataSource.itemWidthIncrement = 28
        segmentedDataSource.itemSpacing = 12
        segmentedDataSource.cornerRadius = 15
        segmentedDataSource.backGroundNormalColor = UIColor(hexString: "#5F7AA2", alpha: 0.05)
        segmentedDataSource.backGroundSelectedColor = UIColor(hexString: "#514DED", alpha: 0.1)
        segmentedView.delegate = self
        segmentedView.dataSource = segmentedDataSource
        //不需要配置指示器，这里仅改变backgroundcolor
        view.addSubview(segmentedView)
        listContainerView = JXSegmentedListContainerView(dataSource: self)
        view.addSubview(listContainerView)
        segmentedView.listContainer = listContainerView
        //布局子控件
        segmentedView.snp.makeConstraints { (make) in
            make.width.equalToSuperview()
            make.height.equalTo(30)
            make.top.equalTo(135 + Constants.statusBarHeight)
        }
        listContainerView.snp.makeConstraints { (make) in
            //可以滑动的容器,在tab的下面,宽度屏幕宽,底部在安全区的最下边
            make.top.equalTo(segmentedView.snp.bottom).offset(14)
            make.width.equalToSuperview()
            make.bottom.equalToSuperview()
        }
    }
    func initVCs() {
        let allVC = ActivityCollectionVC(activityType: .all)
        allVC.title = "全部"
        addChild(allVC)
        collectionViewControllers.append(allVC)
        
        let cultureVC = ActivityCollectionVC(activityType: .culture)
        cultureVC.title = "文娱活动"
        addChild(cultureVC)
        collectionViewControllers.append(cultureVC)
        
        let sportsVC = ActivityCollectionVC(activityType: .sports)
        sportsVC.title = "体育活动"
        addChild(sportsVC)
        collectionViewControllers.append(sportsVC)
        
        let educationVC = ActivityCollectionVC(activityType: .education)
        educationVC.title = "教育活动"
        addChild(educationVC)
        collectionViewControllers.append(educationVC)
    }
}

extension ActivityMainViewController: JXSegmentedViewDelegate {
    func segmentedView(_ segmentedView: JXSegmentedView, didSelectedItemAt index: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if (self.collectionViewControllers[index].activitiesModel.activities.count == 0){
                ActivityHUD.shared.showNoMoreData()
            }
        }
    }
}

extension ActivityMainViewController: JXSegmentedListContainerViewDataSource {
    func numberOfLists(in listContainerView: JXSegmentedListContainerView) -> Int {
        return 4
    }
    
    func listContainerView(_ listContainerView: JXSegmentedListContainerView, initListAt index: Int) -> JXSegmentedListContainerViewListDelegate {
        return collectionViewControllers[index]
    }
}
