//
//  NoticeView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Max Xu on 2024/11/21.
//  Copyright © 2024 Redrock. All rights reserved.
//

import UIKit
import SnapKit

class NoticeView: UIView {
    
    let titleLabel: UILabel = {
        let titleLabel = UILabel()
        // 配置 titleLabel
        titleLabel.font = UIFont.boldSystemFont(ofSize: 16)
        titleLabel.textColor = .ry(light: "#15315B", dark: "#F0F0F2")
        titleLabel.numberOfLines = 0 // 支持多行
        titleLabel.textAlignment = .justified
        return titleLabel
    }()
    let contentLabel: UILabel = {
        let contentLabel = UILabel()
        // 配置 contentLabel
        contentLabel.font = UIFont.systemFont(ofSize: 14)
        contentLabel.textColor = .ry(light: "#15315B", dark: "#F0F0F2")
        contentLabel.numberOfLines = 0 // 支持多行
        contentLabel.textAlignment = .justified
        return contentLabel
    }()

    init(title: String, content: String) {
        super.init(frame: .zero)
        setupViews()
        configure(title: title, content: content)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupViews() {
        // 添加到视图
        addSubview(titleLabel)
        addSubview(contentLabel)
        
        // 使用 SnapKit 设置约束
        titleLabel.snp.makeConstraints { make in
            make.top.equalToSuperview().offset(8)  // 距离顶部 8 点
            make.height.equalTo(20)
            make.left.equalToSuperview()
        }
        
        contentLabel.snp.makeConstraints { make in
            make.top.equalTo(titleLabel.snp.bottom).offset(8) // 距离 titleLabel 底部 8 点
            make.left.right.equalToSuperview()
            make.bottom.equalToSuperview().offset(-8) // 距离底部 8 点
        }
    }
    
    private func configure(title: String, content: String) {
        titleLabel.text = title
        contentLabel.text = content
    }
}
