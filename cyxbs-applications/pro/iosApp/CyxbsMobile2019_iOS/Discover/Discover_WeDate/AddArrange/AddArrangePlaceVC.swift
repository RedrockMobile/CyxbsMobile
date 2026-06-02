//
//  AddArrangePlaceVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import SwiftyJSON

class AddArrangePlaceVC: UIViewController {

    /// 地点数组
    private var placeAry: [String] = []
    /// 选中的地点
    private var selectedPlace: String = ""
    /// 标记点击cell的索引
    private var selectedIndexPath: Int = -1
    /// 全部学生学号数组
    private var allStudentAry: [String] = []
    /// 忙碌学生学号数组
    private var busyStudentAry: [String] = []
    /// 日期字典
    private var dateDic: [String: Int] = [:]
    /// 标题字符串
    private var titleStr: String = ""
    
    
    // MARK: - Life Cycle
    
    init(_ allStudentAry: [String], _ busyStudentAry: [String], _ dateDic: [String: Int], _ titleStr: String) {
        super.init(nibName: nil, bundle: nil)
        self.allStudentAry = allStudentAry
        self.busyStudentAry = busyStudentAry
        self.dateDic = dateDic
        self.titleStr = titleStr
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.addSubview(returnBtn)
        view.addSubview(label)
        view.addSubview(collectionView)
        view.addSubview(nextBtn)
        view.backgroundColor = .weDatePageBackground
        
        HttpRequest()
    }
    
    // MARK: - Method
    
    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    private func HttpRequest() {
        HttpTool.share().request(Reminder_GET_placeHotWord,
                                 type: .get,
                                 serializer: .JSON,
                                 bodyParameters: nil,
                                 progress: nil) { task, object in
            let json = JSON(object!)
            if let hotLocation = json["data"]["hotLocation"].arrayObject as? [String] {
                self.placeAry = hotLocation
                self.collectionView.reloadData()
            }
        } failure: { task, error in
            print(error)
        }
    }
    
    @objc private func clickNextBtn() {
        if selectedPlace.isEmpty {
            view.addSubview(promptLab)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                self.promptLab.removeFromSuperview()
            }
        } else {
            self.navigationController?.pushViewController(AddArrangeMemberVC(allStudentAry, busyStudentAry, dateDic, titleStr, selectedPlace), animated: true)
        }
    }
    
    // MARK: - Lazy
    
    /// 返回按钮
    private lazy var returnBtn: UIButton = {
        let returnBtn = UIButton(frame: CGRect(x: 12, y: statusBarHeight + 8, width: 17, height: 25))
        returnBtn.setImage(UIImage(named: "空教室返回"), for: .normal)
        returnBtn.imageEdgeInsets = UIEdgeInsets(top: 4, left: 4, bottom: 4, right: 4)
        returnBtn.addTarget(self, action: #selector(clickReturnBtn), for: .touchUpInside)
        return returnBtn
    }()
    /// '选择地点'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: returnBtn.left, y: returnBtn.bottom + 104, width: 98, height: 34))
        label.text = "选择地点"
        label.font = .boldSystemFont(ofSize: 24)
        label.textColor = .weDateDeepText
        return label
    }()
    /// 展示地点热词
    private lazy var collectionView: UICollectionView = {
        let layout = AddArrangeFlowLayout()
        layout.minimumLineSpacing = 5.75
        layout.minimumInteritemSpacing = 5.75
        layout.scrollDirection = .vertical
        let collectionView = UICollectionView(frame: CGRect(x: label.left, y: label.bottom + 25, width: SCREEN_WIDTH - label.left * 2, height: 108), collectionViewLayout: layout)
        collectionView.register(AddArrangeCollectionViewCell.self, forCellWithReuseIdentifier: AddArrangeCollectionViewCellReuseIdentifier)
        collectionView.backgroundColor = .weDatePageBackground
        collectionView.dataSource = self
        collectionView.delegate = self
        return collectionView
    }()
    /// 下一步按钮
    private lazy var nextBtn: UIButton = {
        let nextBtn = UIButton(frame: CGRect(x: (SCREEN_WIDTH - 112) / 2, y: SCREEN_HEIGHT - 57 - 42, width: 112, height: 42))
        nextBtn.layer.cornerRadius = 22
        nextBtn.clipsToBounds = true
        nextBtn.setTitle("下一步", for: .normal)
        nextBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        nextBtn.addTarget(self, action: #selector(clickNextBtn), for: .touchUpInside)
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = nextBtn.bounds
        nextBtn.layer.insertSublayer(gradientLayer, at: 0)
        return nextBtn
    }()
    /// 地点为空提示语
    private lazy var promptLab: UILabel = {
        let promptLab = UILabel(frame: CGRect(x: (SCREEN_WIDTH - 199) / 2, y: returnBtn.bottom + 17, width: 199, height: 36))
        promptLab.text = "掌友，地点不能为空哦！"
        promptLab.font = .systemFont(ofSize: 13)
        promptLab.textAlignment = .center
        promptLab.layer.cornerRadius = 18
        promptLab.clipsToBounds = true
        promptLab.textColor = .white
        promptLab.backgroundColor = .weDateToastBackground
        return promptLab
    }()
}

// MARK: - UICollectionViewDataSource

extension AddArrangePlaceVC: UICollectionViewDataSource {
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return placeAry.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: AddArrangeCollectionViewCellReuseIdentifier, for: indexPath) as! AddArrangeCollectionViewCell
        cell.label.text = placeAry[indexPath.item]
        return cell
    }
}

// MARK: - UICollectionViewDelegate

extension AddArrangePlaceVC: UICollectionViewDelegate {
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        selectedPlace = placeAry[indexPath.item]
        // 实现再次点击cell后取消选中&只能同时选中一个cell
        let cell = collectionView.cellForItem(at: indexPath) as! AddArrangeCollectionViewCell
        if indexPath.item != selectedIndexPath {
            cell.isSelectedCell = false
        }
        if cell.isSelectedCell {
            collectionView.deselectItem(at: indexPath, animated: false)
            selectedPlace = ""
        }
        cell.isSelectedCell = !cell.isSelectedCell
        selectedIndexPath = indexPath.item
    }
}

// MARK: - UICollectionViewDelegateFlowLayout

extension AddArrangePlaceVC: UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        return CGSize(width: (placeAry[indexPath.item].count - 1) * 14 + 50, height: 32)
    }
}
