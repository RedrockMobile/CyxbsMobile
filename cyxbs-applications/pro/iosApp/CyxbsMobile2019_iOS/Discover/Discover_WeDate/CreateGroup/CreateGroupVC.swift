//
//  CreateGroupVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/24.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

protocol CreateGroupVCDelegate: AnyObject {
    func updateGroupData()
}

class CreateGroupVC: UIViewController {
    
    weak var delegate: CreateGroupVCDelegate?
    
    private var studentIDAry: [String] = []
    
    // MARK: - Life Cycle
    
    init(studentIDAry: [String]) {
        super.init(nibName: nil, bundle: nil)
        self.studentIDAry = studentIDAry
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        textFieldBackView.addSubview(textField)
        containerView.addSubview(textFieldBackView)
        containerView.addSubview(cancelBtn)
        containerView.addSubview(createLab)
        containerView.addSubview(nameLab)
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
    
    @objc private func clickFinishBtn() {
        if let text = textField.text,
           text.isEmpty {
            containerView.addSubview(emptyLab)
            showShakeAnimation(label: emptyLab)
            repeatLab.removeFromSuperview()
        } else if textField.text!.count <= 10 {
            CreateGroupModel.requestWith(name: textField.text!, studentID: studentIDAry) { createGroupModel in
                if createGroupModel.isRepeat {
                    self.containerView.addSubview(self.repeatLab)
                    self.showShakeAnimation(label: self.repeatLab)
                    self.emptyLab.removeFromSuperview()
                } else {
                    self.delegate?.updateGroupData()
                    self.dismiss(animated: true, completion: nil)
                }
            } failure: { error in
                print(error)
            }
        }
    }
    
    private func showShakeAnimation(label: UILabel) {
        let animation = CABasicAnimation(keyPath: "position")
        animation.duration = 0.1
        animation.repeatCount = 10
        animation.autoreverses = true
        animation.fromValue = NSValue(cgPoint: CGPoint(x: label.center.x - 1, y: label.center.y))
        animation.toValue = NSValue(cgPoint: CGPoint(x: label.center.x + 1, y: label.center.y))
        label.layer.add(animation, forKey: "position")
    }
    
    // MARK: - Lazy
    
    /// 此VC所有UI的容器视图
    private lazy var containerView: UIView = {
        let containerView = UIView(frame: CGRect(x: 0, y: SCREEN_HEIGHT * 0.71, width: SCREEN_WIDTH, height: SCREEN_HEIGHT * 0.29))
        containerView.backgroundColor = .weDatePanelBackground
        let maskPath = UIBezierPath(roundedRect: containerView.bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 12, height: 12))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        containerView.layer.mask = maskLayer
        return containerView
    }()
    /// 取消按钮
    private lazy var cancelBtn: UIButton = {
        let cancelBtn = UIButton(type: .system)
        cancelBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 25, y: 16, width: 25, height: 17)
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 12)
        cancelBtn.setTitleColor(.weDateLightMutedText, for: .normal)
        cancelBtn.addTarget(self, action: #selector(clickCancelBtn), for: .touchUpInside)
        return cancelBtn
    }()
    /// '创建新的分组'文本
    private lazy var createLab: UILabel = {
        let createLab = UILabel(frame: CGRect(x: (SCREEN_WIDTH - 114) / 2, y: cancelBtn.bottom + 5, width: 117, height: 21))
        createLab.text = "创建新的分组"
        createLab.textAlignment = .center
        createLab.font = .boldSystemFont(ofSize: 19)
        createLab.textColor = .weDatePrimaryText
        return createLab
    }()
    /// '分组名称'文本
    private lazy var nameLab: UILabel = {
        let nameLab = UILabel(frame: CGRect(x: textFieldBackView.left - 74, y: createLab.bottom + 34.5, width: 74, height: 21))
        nameLab.text = "分组名称："
        nameLab.font = .systemFont(ofSize: 14)
        nameLab.textColor = .weDateMutedText
        return nameLab
    }()
    /// 输入框所在视图
    private lazy var textFieldBackView: UIView = {
        let textFieldBackView = UIView(frame: CGRect(x: createLab.left - 5, y: createLab.bottom + 24, width: 183, height: 42))
        textFieldBackView.layer.cornerRadius = 22
        textFieldBackView.clipsToBounds = true
        textFieldBackView.backgroundColor = .weDateInputBackground
        return textFieldBackView
    }()
    /// 输入框
    private lazy var textField: UITextField = {
        let textField = UITextField(frame: CGRect(x: 21, y: 10.5, width: 128, height: 21))
        textField.textColor = .weDateDeepText
        let attributes: [NSAttributedString.Key: Any] = [
            .foregroundColor: UIColor.weDateLightMutedText,
            .font: UIFont.systemFont(ofSize: 14)
        ]
        let attributedPlaceholder = NSAttributedString(string: "10个字以内", attributes: attributes)
        textField.attributedPlaceholder = attributedPlaceholder
        return textField
    }()
    /// 完成按钮
    private lazy var finishBtn: UIButton = {
        let finishBtn = UIButton(frame: CGRect(x: (SCREEN_WIDTH - 120) / 2, y: textFieldBackView.bottom + 39, width: 120, height: 42))
        finishBtn.layer.cornerRadius = 22
        finishBtn.clipsToBounds = true
        finishBtn.setTitle("完成", for: .normal)
        finishBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        finishBtn.addTarget(self, action: #selector(clickFinishBtn), for: .touchUpInside)
        // 为按钮添加线性渐变
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = finishBtn.bounds
        finishBtn.layer.insertSublayer(gradientLayer, at: 0)
        return finishBtn
    }()
    /// 分组名为空提示文本
    private lazy var emptyLab: UILabel = {
        let emptyLab = UILabel(frame: CGRect(x: textFieldBackView.left + 21, y: textFieldBackView.bottom + 10, width: 62, height: 15))
        emptyLab.text = "名称不能为空"
        emptyLab.font = .systemFont(ofSize: 10)
        emptyLab.textColor = .weDateAccent
        return emptyLab
    }()
    /// 分组名重复提示文本
    private lazy var repeatLab: UILabel = {
        let repeatLab = UILabel(frame: CGRect(x: textFieldBackView.left + 21, y: textFieldBackView.bottom + 10, width: 103, height: 15))
        repeatLab.text = "名称重复，请重新输入"
        repeatLab.font = .systemFont(ofSize: 10)
        repeatLab.textColor = .weDateAccent
        return repeatLab
    }()
}
