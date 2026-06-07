//
//  RepeatNameVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/9.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

protocol RepeatNameVCDelegate: AnyObject {
    func addRepeatStudent(_ array: [StudentResultItem])
}

class RepeatNameVC: UIViewController {
    
    weak var delegate: RepeatNameVCDelegate?
    /// 储存cell按钮选中状态
    private var isButtonSelectedAry = [Bool]()
    /// 重复学生数组
    var studentAry: [StudentResultItem] = []
    /// 选择的学生数组
    var selectedStudentAry: [StudentResultItem] = [] {
        willSet {
            if newValue.count > 0 {
                confirmBtn.backgroundColor = .weDatePanelBackground
                let gradientLayer = CAGradientLayer()
                gradientLayer.colors = [
                    UIColor.weDateGradientStart.cgColor,
                    UIColor.weDateGradientEnd.cgColor
                ]
                gradientLayer.startPoint = CGPoint(x: 0, y: 0)
                gradientLayer.endPoint = CGPoint(x: 1, y: 1)
                gradientLayer.frame = confirmBtn.bounds
                confirmBtn.layer.insertSublayer(gradientLayer, at: 0)
                confirmBtn.isUserInteractionEnabled = true
            } else {
                if let sublayers = confirmBtn.layer.sublayers {
                    for layer in sublayers {
                        if layer is CAGradientLayer {
                            layer.removeFromSuperlayer()
                        }
                    }
                }
                confirmBtn.backgroundColor = .weDateDisabledButton
                confirmBtn.isUserInteractionEnabled = false
            }
        }
    }
    
    // MARK: - Life Cycle
    
    init(studentAry: [StudentResultItem]) {
        super.init(nibName: nil, bundle: nil)
        self.studentAry = studentAry
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()

        view.addSubview(containerView)
        containerView.addSubview(cancelBtn)
        containerView.addSubview(label)
        containerView.addSubview(tableView)
        containerView.addSubview(confirmBtn)
        
        isButtonSelectedAry = [Bool](repeating: false, count: studentAry.count)
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
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        
        containerView.frame = CGRect(x: 0, y: SCREEN_HEIGHT * 0.47, width: SCREEN_WIDTH, height: SCREEN_HEIGHT * 0.53)
        cancelBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 25, y: 16, width: 25, height: 17)
        label.frame = CGRect(x: 16, y: 51, width: 166, height: 25)
        tableView.snp.makeConstraints { make in
            make.left.right.equalToSuperview()
            make.top.equalTo(label.snp.bottom).offset(24)
            make.bottom.equalToSuperview().inset(98)
        }
        confirmBtn.frame = CGRect(x: (SCREEN_WIDTH - 120) / 2, y: tableView.bottom + 28, width: 120, height: 42)
        
        let maskPath = UIBezierPath(roundedRect: containerView.bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 12, height: 12))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        containerView.layer.mask = maskLayer
    }
    
    // MARK: - Method
    
    @objc private func clickCancelBtn() {
        self.dismiss(animated: true, completion: nil)
    }
    
    @objc private func clickConfirmBtn() {
        self.dismiss(animated: true, completion: nil)
        delegate?.addRepeatStudent(selectedStudentAry)
    }
    
    // MARK: - Lazy
    
    /// 此VC所有UI的容器视图
    private lazy var containerView: UIView = {
        let containerView = UIView()
        containerView.backgroundColor = .weDatePanelBackground
        return containerView
    }()
    /// 取消按钮
    private lazy var cancelBtn: UIButton = {
        let cancelBtn = UIButton()
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 12)
        cancelBtn.setTitleColor(.weDateLightMutedText, for: .normal)
        cancelBtn.addTarget(self, action: #selector(clickCancelBtn), for: .touchUpInside)
        return cancelBtn
    }()
    /// '有重名现象，请勾选'文本
    private lazy var label: UILabel = {
        let label = UILabel()
        label.text = "有重名现象，请勾选"
        label.font = .systemFont(ofSize: 18, weight: .black)
        label.textColor = .weDatePrimaryText
        return label
    }()
    /// 重名学生展示
    private lazy var tableView: UITableView = {
        let tableView = UITableView(frame: .zero, style: .plain)
        tableView.estimatedRowHeight = 0
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.separatorStyle = .none
        tableView.showsVerticalScrollIndicator = true
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(RepeatNameTableViewCell.self, forCellReuseIdentifier: RepeatNameTableViewCellReuseIdentifier)
        tableView.backgroundColor = .weDatePanelBackground
        return tableView
    }()
    /// 确认按钮
    private lazy var confirmBtn: UIButton = {
        let confirmBtn = UIButton()
        confirmBtn.layer.cornerRadius = 22
        confirmBtn.clipsToBounds = true
        confirmBtn.setTitle("确定", for: .normal)
        confirmBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        confirmBtn.addTarget(self, action: #selector(clickConfirmBtn), for: .touchUpInside)
        confirmBtn.backgroundColor = .weDateDisabledButton
        return confirmBtn
    }()
}

// MARK: - UITableViewDataSource

extension RepeatNameVC: UITableViewDataSource {
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return studentAry.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: RepeatNameTableViewCellReuseIdentifier, for: indexPath) as! RepeatNameTableViewCell
        cell.nameLab.text = studentAry[indexPath.row].name
        cell.stuNumLab.text = studentAry[indexPath.row].studentID
        cell.departLab.text = studentAry[indexPath.row].depart
        cell.button.tag = indexPath.row
        cell.delegate = self
        cell.isButtonSelected = isButtonSelectedAry[indexPath.row]
        cell.selectionStyle = .none
        return cell
    }
}

// MARK: - UITableViewDelegate

extension RepeatNameVC: UITableViewDelegate {
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 54
    }
}

// MARK: - RepeatNameTableViewCellDelegate

extension RepeatNameVC: RepeatNameTableViewCellDelegate {
    
    func addStudentDataAt(_ index: Int) {
        let studentID = studentAry[index].studentID
        if !selectedStudentAry.contains(where: { $0.studentID == studentID }) {
            selectedStudentAry.append(studentAry[index])
        } else if let i = selectedStudentAry.firstIndex(where: { $0.studentID == studentID }) {
            selectedStudentAry.remove(at: i)
        }
        // 改变按钮选择状态
        isButtonSelectedAry[index] = !isButtonSelectedAry[index]
        tableView.reloadData()
    }
}
