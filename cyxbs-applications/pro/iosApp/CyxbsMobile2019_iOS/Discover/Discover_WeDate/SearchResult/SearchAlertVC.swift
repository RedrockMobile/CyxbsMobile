//
//  SearchAlertVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/15.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class SearchAlertVC: UIViewController {
    
    private let alertStr: String
    
    // MARK: - Life Cycle
    
    /// - Parameter alertStr: 弹窗上展示的字符串
    init(alertStr: String) {
        self.alertStr = alertStr
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        view.backgroundColor = UIColor(hexString: "000000", alpha: 0.47)
    }
        
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        view.backgroundColor = .clear
    }
    
    // MARK: - Method
    
    private func setupUI() {
        // 带有文本和按钮的自定义视图
        let customView = UIView(frame: CGRect(x: (SCREEN_WIDTH - 238) / 2, y: (SCREEN_HEIGHT - 133) / 2 - 50, width: 238, height: 133))
        customView.layer.cornerRadius = 11.0
        customView.clipsToBounds = true
        customView.backgroundColor = .weDatePanelBackground
        
        let label = UILabel(frame: CGRect(x: 0, y: 25, width: customView.width, height: 22))
        label.text = alertStr
        label.textAlignment = .center
        label.font = .systemFont(ofSize: 15)
        label.textColor = .weDatePrimaryText
        customView.addSubview(label)
        
        let button = UIButton(frame: CGRect(x: 73, y: label.bottom + 25, width: 92, height: 36))
        button.layer.cornerRadius = 18
        button.clipsToBounds = true
        button.setTitle("确定", for: .normal)
        button.titleLabel?.font = .boldSystemFont(ofSize: 15)
        button.addTarget(self, action: #selector(clickBtn), for: .touchUpInside)
        // 为按钮添加线性渐变
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = button.bounds
        button.layer.insertSublayer(gradientLayer, at: 0)
        customView.addSubview(button)
        
        view.addSubview(customView)
    }
    
    @objc private func clickBtn() {
        dismiss(animated: false, completion: nil)
    }
}
