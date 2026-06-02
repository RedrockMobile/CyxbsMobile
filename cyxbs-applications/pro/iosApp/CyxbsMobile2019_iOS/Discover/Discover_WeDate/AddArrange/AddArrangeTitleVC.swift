//
//  AddArrangeTitleVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class AddArrangeTitleVC: UIViewController {

    /// 标题数组
    private var titleAry: [String] = []
    /// 选中的标题
    private var selectedTitle: String = "" {
        willSet {
            if !newValue.isEmpty {
                nextBtn.backgroundColor = .weDatePanelBackground
                let gradientLayer = CAGradientLayer()
                gradientLayer.colors = [
                    UIColor.weDateGradientStart.cgColor,
                    UIColor.weDateGradientEnd.cgColor
                ]
                gradientLayer.startPoint = CGPoint(x: 0, y: 0)
                gradientLayer.endPoint = CGPoint(x: 1, y: 1)
                gradientLayer.frame = nextBtn.bounds
                nextBtn.layer.insertSublayer(gradientLayer, at: 0)
            }
        }
    }
    /// 全部学生学号数组
    private var allStudentAry: [String] = []
    /// 忙碌学生学号数组
    private var busyStudentAry: [String] = []
    /// 日期字典
    private var dateDic: [String: Int] = [:]
    
    // MARK: - Life Cycle
    
    init(_ allStudentAry: [String], _ busyStudentAry: [String], _ dateDic: [String: Int]) {
        super.init(nibName: nil, bundle: nil)
        self.allStudentAry = allStudentAry
        self.busyStudentAry = busyStudentAry
        self.dateDic = dateDic
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        httpRequest()
        view.addSubview(returnBtn)
        view.addSubview(firstLab)
        view.addSubview(secondLab)
        view.addSubview(collectionView)
        view.addSubview(nextBtn)
        view.backgroundColor = .weDatePageBackground
        self.navigationController?.navigationBar.isHidden = true
    }
    
    // MARK: - Method
    
    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    
    private func httpRequest() {
        HttpTool.share().request(Reminder_GET_titleHotWord,
                                 type: .get,
                                 serializer: .JSON,
                                 bodyParameters: nil,
                                 progress: nil) { task, object in
            if let object = object as? [String: Any],
               let data = object["data"] as? [String] {
                self.titleAry = data
                self.collectionView.reloadData()
            }
        } failure: { task, error in
            print(error)
        }
    }
    
    @objc private func clickNextBtn() {
        if selectedTitle.isEmpty {
            view.addSubview(promptLab)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                self.promptLab.removeFromSuperview()
            }
        } else {
            self.navigationController?.pushViewController(AddArrangePlaceVC(allStudentAry, busyStudentAry, dateDic, selectedTitle), animated: true)
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
    /// '为你的行程添加'文本
    private lazy var firstLab: UILabel = {
        let firstLab = UILabel(frame: CGRect(x: returnBtn.left, y: returnBtn.bottom + 70, width: 171, height: 34))
        firstLab.text = "为你的行程添加"
        firstLab.font = .boldSystemFont(ofSize: 24)
        firstLab.textColor = .weDateDeepText
        return firstLab
    }()
    /// '一个标题'文本
    private lazy var secondLab: UILabel = {
        let secondLab = UILabel(frame: CGRect(x: returnBtn.left, y: firstLab.bottom + 2, width: 100, height: firstLab.height))
        secondLab.text = "一个标题"
        secondLab.font = .boldSystemFont(ofSize: 24)
        secondLab.textColor = .weDateDeepText
        return secondLab
    }()
    /// 展示标题热词
    private lazy var collectionView: UICollectionView = {
        let layout = AddArrangeFlowLayout()
        layout.minimumLineSpacing = 5.75
        layout.minimumInteritemSpacing = 5.75
        layout.scrollDirection = .vertical
        let collectionView = UICollectionView(frame: CGRect(x: firstLab.left, y: secondLab.bottom + 25, width: SCREEN_WIDTH - firstLab.left * 2, height: 70), collectionViewLayout: layout)
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
        nextBtn.backgroundColor = .weDateDisabledButton
        return nextBtn
    }()
    /// 标题为空提示语
    private lazy var promptLab: UILabel = {
        let promptLab = UILabel(frame: CGRect(x: (SCREEN_WIDTH - 199) / 2, y: returnBtn.bottom + 17, width: 199, height: 36))
        promptLab.text = "掌友，标题不能为空哦！"
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

extension AddArrangeTitleVC: UICollectionViewDataSource {
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return titleAry.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: AddArrangeCollectionViewCellReuseIdentifier, for: indexPath) as! AddArrangeCollectionViewCell
        cell.label.text = titleAry[indexPath.item]
        return cell
    }
}

// MARK: - UICollectionViewDelegate

extension AddArrangeTitleVC: UICollectionViewDelegate {
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        selectedTitle = titleAry[indexPath.item]
    }
}

// MARK: - UICollectionViewDelegateFlowLayout

extension AddArrangeTitleVC: UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        return CGSize(width: (titleAry[indexPath.item].count - 1) * 14 + 50, height: 32)
    }
}
