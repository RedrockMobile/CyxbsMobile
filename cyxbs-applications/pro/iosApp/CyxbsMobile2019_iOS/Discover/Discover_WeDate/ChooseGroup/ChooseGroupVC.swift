//
//  ChooseGroupVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/7.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class ChooseGroupVC: UIViewController {
    
    /// 储存cell按钮选中状态
    private var isButtonSelectedAry = [Bool]()
    /// 学号
    var studentID: String = ""
    /// 分组信息
    var groupAry: [GroupItem] = []
    /// 选择的分组id数组
    var selectedGroupIDAry: [Int] = [] {
        willSet {
            if newValue.count > 0 {
                finishBtn.backgroundColor = .weDatePanelBackground
                let gradientLayer = CAGradientLayer()
                gradientLayer.colors = [
                    UIColor.weDateGradientStart.cgColor,
                    UIColor.weDateGradientEnd.cgColor
                ]
                gradientLayer.startPoint = CGPoint(x: 0, y: 0)
                gradientLayer.endPoint = CGPoint(x: 1, y: 1)
                gradientLayer.frame = finishBtn.bounds
                finishBtn.layer.insertSublayer(gradientLayer, at: 0)
                finishBtn.isUserInteractionEnabled = true
            } else {
                if let sublayers = finishBtn.layer.sublayers {
                    for layer in sublayers {
                        if layer is CAGradientLayer {
                            layer.removeFromSuperlayer()
                        }
                    }
                }
                finishBtn.backgroundColor = .weDateDisabledButton
                finishBtn.isUserInteractionEnabled = false
            }
        }
    }
    
    // MARK: - Life Cycle
    
    init(studentID: String) {
        super.init(nibName: nil, bundle: nil)
        self.studentID = studentID
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        receiveGroup()
        
        containerView.addSubview(cancelBtn)
        containerView.addSubview(label)
        containerView.addSubview(collectionView)
        containerView.addSubview(indicatorBackView)
        indicatorBackView.addSubview(customIndicator)
        containerView.addSubview(finishBtn)
        view.addSubview(containerView)
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        UIView.animate(withDuration: 0.1) {
            self.view.backgroundColor = UIColor(hexString: "000000", alpha: 0.47)
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        view.backgroundColor = .clear
    }
    
    // MARK: - Method
    
    @objc private func clickCancelBtn() {
        self.dismiss(animated: true, completion: nil)
    }
    
    /// 得到所有分组
    private func receiveGroup() {
        FixedGroupModel.getGroup { groupModel in
            self.groupAry = groupModel.groupAry
            self.collectionView.reloadData()
            
            if self.groupAry.count > 6 {
                self.customIndicator.isHidden = false
                self.indicatorBackView.isHidden = false
            } else {
                self.customIndicator.isHidden = true
                self.indicatorBackView.isHidden = true
            }
            
            self.isButtonSelectedAry = [Bool](repeating: false, count: self.groupAry.count)
            
        } failure: { error in
            print(error)
        }
    }
    
    @objc private func clickFinishBtn() {
        for groupID in selectedGroupIDAry {
            GroupManageVC.addMemberWith(groupID: groupID, studentID: [studentID])
        }
        self.dismiss(animated: true, completion: nil)
    }
    
    // MARK: - Lazy
    
    /// 此VC所有UI的容器视图
    private lazy var containerView: UIView = {
        let containerView = UIView(frame: CGRect(x: 0, y: SCREEN_HEIGHT - 280, width: SCREEN_WIDTH, height: 280))
        containerView.backgroundColor = .weDatePanelBackground
        let maskPath = UIBezierPath(roundedRect: containerView.bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 12, height: 12))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        containerView.layer.mask = maskLayer
        return containerView
    }()
    /// 取消按钮
    private lazy var cancelBtn: UIButton = {
        let cancelBtn = UIButton(frame: CGRect(x: SCREEN_WIDTH - 16 - 25, y: 16, width: 25, height: 17))
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 12)
        cancelBtn.setTitleColor(.weDateLightMutedText, for: .normal)
        cancelBtn.addTarget(self, action: #selector(clickCancelBtn), for: .touchUpInside)
        return cancelBtn
    }()
    /// '添加到'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: 16, y: 46, width: 56, height: 21))
        label.text = "添加到"
        label.font = .systemFont(ofSize: 18)
        label.textColor = .weDatePrimaryText
        return label
    }()
    /// 展示分组名
    private lazy var collectionView: UICollectionView = {
        let layout = UICollectionViewFlowLayout()
        layout.minimumLineSpacing = 0
        layout.minimumInteritemSpacing = 0
        layout.scrollDirection = .horizontal
        let collectionView = UICollectionView(frame: CGRect(x: 0, y: label.bottom + 19, width: SCREEN_WIDTH, height: 90), collectionViewLayout: layout)
        collectionView.showsHorizontalScrollIndicator = false
        collectionView.backgroundColor = .weDatePanelBackground
        collectionView.register(ChooseGroupCollectionViewCell.self, forCellWithReuseIdentifier: ChooseGroupCollectionViewCellReuseIdentifier)
        collectionView.dataSource = self
        collectionView.delegate = self
        return collectionView
    }()
    /// 指示器所在视图
    private lazy var indicatorBackView: UIView = {
        let indicatorBackView = UIView(frame: CGRect(x: (SCREEN_WIDTH - 62) / 2, y: label.bottom + 120, width: 62, height: 3))
        indicatorBackView.layer.cornerRadius = 1.5
        indicatorBackView.clipsToBounds = true
        indicatorBackView.backgroundColor = .weDateSearchBackground
        return indicatorBackView
    }()
    /// 指示器
    private lazy var customIndicator: UIView = {
        let customIndicator = UIView(frame: CGRect(x: 0, y: 0, width: 40, height: 3))
        customIndicator.layer.cornerRadius = 1.5
        customIndicator.clipsToBounds = true
        customIndicator.backgroundColor = .weDateIndicator
        return customIndicator
    }()
    /// 完成按钮
    private lazy var finishBtn: UIButton = {
        let finishBtn = UIButton(frame: CGRect(x: (SCREEN_WIDTH - 120) / 2, y: indicatorBackView.bottom + 20, width: 120, height: 42))
        finishBtn.layer.cornerRadius = 22
        finishBtn.clipsToBounds = true
        finishBtn.setTitle("完成", for: .normal)
        finishBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        finishBtn.addTarget(self, action: #selector(clickFinishBtn), for: .touchUpInside)
        finishBtn.backgroundColor = .weDateDisabledButton
        return finishBtn
    }()
}

// MARK: - UICollectionViewDataSource

extension ChooseGroupVC: UICollectionViewDataSource {
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return groupAry.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: ChooseGroupCollectionViewCellReuseIdentifier, for: indexPath) as! ChooseGroupCollectionViewCell
        cell.groupName = groupAry[indexPath.item].name
        cell.delegate = self
        cell.hookBtn.tag = indexPath.item
        cell.groupNameBtn.tag = indexPath.item
        cell.isButtonSelected = isButtonSelectedAry[indexPath.item]
        return cell
    }
}

// MARK: - UIScrollViewDelegate

extension ChooseGroupVC: UIScrollViewDelegate {
 
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        let contentWidth = collectionView.contentSize.width - collectionView.width
        let scrollRatio = collectionView.contentOffset.x / contentWidth
        let indicatorX = scrollRatio * (indicatorBackView.width - customIndicator.width)
        customIndicator.frame.origin.x = indicatorX
    }
}

// MARK: - UICollectionViewDelegateFlowLayout

extension ChooseGroupVC: UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        return CGSize(width: 188, height: collectionView.height / 3)
    }
}

// MARK: - ChooseGroupCollectionViewCellDelegate

extension ChooseGroupVC: ChooseGroupCollectionViewCellDelegate {
    
    func addGroupIDAt(_ index: Int) {
        let groupID = groupAry[index].groupID
        if !selectedGroupIDAry.contains(groupID) {
            selectedGroupIDAry.append(groupID)
        } else if let i = selectedGroupIDAry.firstIndex(of: groupID) {
            selectedGroupIDAry.remove(at: i)
        }
        // 改变按钮选择状态
        isButtonSelectedAry[index] = !isButtonSelectedAry[index]
        collectionView.reloadData()
    }
}
