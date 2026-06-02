//
//  CreateGroupAlertVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

protocol CreateGroupAlertVCDelegate: AnyObject {
    func createGroupAlertVCDidDismiss()
}

class CreateGroupAlertVC: UIViewController {
    
    weak var delegate: CreateGroupAlertVCDelegate?

    // MARK: - Life Cycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        customView.addSubview(alertLab)
        customView.addSubview(cancelBtn)
        customView.addSubview(confirmBtn)
        customView.addSubview(boxBtn)
        customView.addSubview(label)
        view.addSubview(customView)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        view.backgroundColor = UIColor(hexString: "000000", alpha: 0.47)
    }
    
    // MARK: - Method
    
    @objc private func clickCancelBtn() {
        if boxBtn.isSelected {
            UserDefaults.standard.set(true, forKey: "noMoreReminders")
            UserDefaults.standard.set("cancel", forKey: "userChoice")
        }
        
        dismiss(animated: false, completion: nil)
    }
    
    @objc private func clickConfirmBtn() {
        if boxBtn.isSelected {
            UserDefaults.standard.set(true, forKey: "noMoreReminders")
            UserDefaults.standard.set("confirm", forKey: "userChoice")
        }
        
        dismiss(animated: false, completion: nil)
        
        delegate?.createGroupAlertVCDidDismiss()
    }
    
    @objc private func clickBoxBtn() {
        boxBtn.isSelected = !boxBtn.isSelected
    }
    
    // MARK: - Lazy
    
    /// 容器视图
    private lazy var customView: UIView = {
        let customView = UIView(frame: CGRect(x: (SCREEN_WIDTH - 303) / 2, y: (SCREEN_HEIGHT - 197) / 2 - 50, width: 303, height: 197))
        customView.layer.cornerRadius = 12
        customView.clipsToBounds = true
        customView.backgroundColor = .weDatePanelBackground
        return customView
    }()
    /// 提示文本
    private lazy var alertLab: UILabel = {
        let alertLab = UILabel(frame: CGRect(x: 0, y: 40, width: customView.width, height: 43))
        alertLab.text = "为了方便下次查询是否将\n此次查询的小伙伴添加到分组呢"
        alertLab.textAlignment = .center
        alertLab.numberOfLines = 0
        alertLab.font = .systemFont(ofSize: 15)
        alertLab.textColor = .weDatePrimaryText
        return alertLab
    }()
    /// 取消按钮
    private lazy var cancelBtn: UIButton = {
        let cancelBtn = UIButton(frame: CGRect(x: 49.5, y: alertLab.bottom + 26, width: 92, height: 36))
        cancelBtn.layer.cornerRadius = 20
        cancelBtn.clipsToBounds = true
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.titleLabel?.font = .boldSystemFont(ofSize: 15)
        cancelBtn.backgroundColor = .weDateDisabledButton
        cancelBtn.addTarget(self, action: #selector(clickCancelBtn), for: .touchUpInside)
        return cancelBtn
    }()
    /// 确定按钮
    lazy var confirmBtn: UIButton = {
        let confirmBtn = UIButton(frame: CGRect(x: cancelBtn.right + 20, y: cancelBtn.top, width: cancelBtn.width, height: cancelBtn.height))
        confirmBtn.layer.cornerRadius = 20
        confirmBtn.clipsToBounds = true
        confirmBtn.setTitle("确定", for: .normal)
        confirmBtn.titleLabel?.font = .boldSystemFont(ofSize: 15)
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = confirmBtn.bounds
        confirmBtn.layer.insertSublayer(gradientLayer, at: 0)
        confirmBtn.addTarget(self, action: #selector(clickConfirmBtn), for: .touchUpInside)
        return confirmBtn
    }()
    /// 勾选框按钮
    private lazy var boxBtn: UIButton = {
        let boxBtn = UIButton(frame: CGRect(x: 115, y: cancelBtn.bottom + 16, width: 10, height: 10))
        boxBtn.setBackgroundImage(UIImage(named: "勾选框"), for: .normal)
        boxBtn.setBackgroundImage(UIImage(named: "勾选框_已选中"), for: .selected)
        boxBtn.addTarget(self, action: #selector(clickBoxBtn), for: .touchUpInside)
        return boxBtn
    }()
    /// '下次不再提醒'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: boxBtn.right + 3, y: cancelBtn.bottom + 14, width: 62, height: 14))
        label.text = "下次不再提醒"
        label.font = .systemFont(ofSize: 10)
        label.textColor = .weDateMutedText
        return label
    }()
}
