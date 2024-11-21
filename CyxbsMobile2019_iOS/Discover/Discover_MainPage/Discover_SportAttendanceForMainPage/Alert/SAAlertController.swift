//
//  SAAlertController.swift
//  CyxbsMobile2019_iOS
//
//  Created by Max Xu on 2024/11/21.
//  Copyright © 2024 Redrock. All rights reserved.
//

import UIKit
import SnapKit

class SAAlertController: UIViewController {
    
    var noticeItems: [NoticeItem] = []
    var noticeViews: [NoticeView] = []

    // MARK: - Properties
    private lazy var contentView: UIView = {
        let view = UIView()
        view.backgroundColor = .ry(light: "#FFFFFF", dark: "#2C2C2C")
        view.layer.cornerRadius = 12
        view.clipsToBounds = true
        return view
    }()
    private lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.textColor = .ry(light: "#15315B", dark: "#F0F0F2")
        label.textAlignment = .center
        label.font = .boldSystemFont(ofSize: 18)
        label.text = "体育打卡信息说明"
        return label
    }()
    private let confirmButton: UIButton = {
        let button = UIButton()
        button.layer.cornerRadius = 18
        button.clipsToBounds = true
        button.backgroundColor = .ry(light: "#C3D4EE", dark: "#5852FF")
        button.titleLabel?.font = .boldSystemFont(ofSize: 15)
        return button
    }()
    
    private var completion: (() -> Void)?

    // MARK: - Life Cycle
    init(noticeItems: [NoticeItem]?, completion: (() -> Void)?) {
        super.init(nibName: nil, bundle: nil)
        self.completion = completion
        self.noticeItems = noticeItems ?? []
        self.modalTransitionStyle = .crossDissolve
        self.modalPresentationStyle = .overFullScreen
        
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        view.addSubview(contentView)
        contentView.addSubview(titleLabel)
        contentView.addSubview(confirmButton)
        
        contentView.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.centerY.equalToSuperview()
            make.left.equalToSuperview().offset(30)
            make.right.equalToSuperview().offset(-30)
            make.height.greaterThanOrEqualTo(400)
        }

        titleLabel.snp.makeConstraints { make in
            make.top.equalTo(contentView).offset(18)
            make.centerX.equalToSuperview()
        }
        
        // 动态添加 TitleContentView
        var lastView: UIView? = nil
        for num in 0 ..< self.noticeItems.count {
            let item = self.noticeItems[num]
            let noticeView = NoticeView(title: "\(num+1).\(item.title):", content: item.content)
            noticeViews.append(noticeView)
            contentView.addSubview(noticeView)
            noticeView.snp.makeConstraints { make in
                make.left.equalToSuperview().offset(10)
                make.right.equalToSuperview().offset(-10)
                if let last = lastView {
                    make.top.equalTo(last.snp.bottom) // 距离上一个视图 5 点
                } else {
                    make.top.equalTo(titleLabel.snp.bottom).offset(15) // 距离顶部标题区域 30 点
                }
            }
            lastView = noticeView
        }
        
        if let lastView = lastView {
            confirmButton.snp.makeConstraints { make in
                make.top.equalTo(lastView.snp.bottom).offset(50)
                make.centerX.equalTo(contentView)
                make.width.equalTo(150)
                make.height.equalTo(36)
            }
            contentView.snp.makeConstraints{ make in
                make.bottom.equalTo(confirmButton.snp.bottom).offset(20)
            }
        }
        
        confirmButton.setTitle("确认", for: .normal)
        
        let maskTapGes = UITapGestureRecognizer(target: self, action: #selector(didTapView))
        view.addGestureRecognizer(maskTapGes)
        let backgroundTapGes = UITapGestureRecognizer()
        contentView.addGestureRecognizer(backgroundTapGes)
        confirmButton.addTarget(self, action: #selector(handleConfirm), for: .touchUpInside)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        addBtnGradient(button: confirmButton)
    }
    
    // MARK: - Methods
    // 给按钮添加渐变
    private func addBtnGradient(button: UIButton) {
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.init(hexString: "#4741E0").cgColor,
            UIColor.init(hexString: "#5D5EF7").cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = button.bounds
        button.layer.insertSublayer(gradientLayer, at: 0)
    }

    // MARK: - Button Actions
    @objc private func handleConfirm() {
        completion?()
        dismiss(animated: true, completion: nil)
    }
    
    @objc private func didTapView(_ tapGes: UITapGestureRecognizer) {
        completion?()
        dismiss(animated: true, completion: nil)
    }
}


