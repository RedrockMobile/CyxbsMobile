//
//  AddArrangeMemberVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class AddArrangeMemberVC: UIViewController {
    
    /// 选中的人员
    private var selectedMember: String = ""
    /// 全部学生学号数组
    private var allStudentAry: [String] = []
    /// 忙碌学生学号数组
    private var busyStudentAry: [String] = []
    /// 日期字典
    private var dateDic: [String: Int] = [:]
    /// 标题字符串
    private var titleStr: String = ""
    /// 地点字符串
    private var locationStr: String = ""

    // MARK: - Life Cycle
    
    init(_ allStudentAry: [String], _ busyStudentAry: [String], _ dateDic: [String: Int], _ titleStr: String, _ locationStr: String) {
        super.init(nibName: nil, bundle: nil)
        self.allStudentAry = allStudentAry
        self.busyStudentAry = busyStudentAry
        self.dateDic = dateDic
        self.titleStr = titleStr
        self.locationStr = locationStr
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(returnBtn)
        view.addSubview(label)
        view.addSubview(collectionView)
        view.addSubview(sendArrangeBtn)
        view.backgroundColor = .weDatePageBackground
    }

    // MARK: - Method

    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    @objc private func clickSendArrangeBtn() {
        if !selectedMember.isEmpty {
            var array: [String] = []
            if selectedMember == "空闲人员" {
                for studentID in allStudentAry {
                    if !busyStudentAry.contains(studentID) {
                        array.append(studentID)
                    }
                }
            } else {
                array = allStudentAry
            }
            AddArrangeModel.sendNotification(stuNumAry: array, title: titleStr, location: locationStr, dateDic: dateDic)
            promptLab.text = "发送成功～"
            view.addSubview(promptLab)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                self.promptLab.removeFromSuperview()
                self.navigationController?.popToRootViewController(animated: true)
            }
        } else {
            promptLab.text = "掌友，人员不能为空哦！"
            view.addSubview(promptLab)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                self.promptLab.removeFromSuperview()
            }
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
    /// '选择通知人员'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: returnBtn.left, y: returnBtn.bottom + 104, width: 146, height: 34))
        label.text = "选择通知人员"
        label.font = .boldSystemFont(ofSize: 24)
        label.textColor = .weDateDeepText
        return label
    }()
    /// 展示人员类型
    private lazy var collectionView: UICollectionView = {
        let layout = UICollectionViewFlowLayout()
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
    /// 发出通知按钮
    private lazy var sendArrangeBtn: UIButton = {
        let sendArrangeBtn = UIButton(frame: CGRect(x: (SCREEN_WIDTH - 130) / 2, y: SCREEN_HEIGHT - 57 - 42, width: 130, height: 42))
        sendArrangeBtn.layer.cornerRadius = 22
        sendArrangeBtn.clipsToBounds = true
        sendArrangeBtn.setTitle("发出通知", for: .normal)
        sendArrangeBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        sendArrangeBtn.addTarget(self, action: #selector(clickSendArrangeBtn), for: .touchUpInside)
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = sendArrangeBtn.bounds
        sendArrangeBtn.layer.insertSublayer(gradientLayer, at: 0)
        return sendArrangeBtn
    }()
    /// 人员为空提示语
    private lazy var promptLab: UILabel = {
        let promptLab = UILabel(frame: CGRect(x: (SCREEN_WIDTH - 199) / 2, y: returnBtn.bottom + 17, width: 199, height: 36))
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

extension AddArrangeMemberVC: UICollectionViewDataSource {
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return 2
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: AddArrangeCollectionViewCellReuseIdentifier, for: indexPath) as! AddArrangeCollectionViewCell
        if indexPath.item == 0 {
            cell.label.text = "空闲人员"
        } else {
            cell.label.text = "全体人员"
        }
        return cell
    }
}

// MARK: - UICollectionViewDelegate

extension AddArrangeMemberVC: UICollectionViewDelegate {
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        if indexPath.item == 0 {
            selectedMember = "空闲人员"
        } else {
            selectedMember = "全体人员"
        }
    }
}

// MARK: - UICollectionViewDelegateFlowLayout

extension AddArrangeMemberVC: UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        return CGSize(width: 92, height: 32)
    }
}
