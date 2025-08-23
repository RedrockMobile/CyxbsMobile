//
//  RecommendedView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class RecommendedView : UIView {
    
    var onTap: (() -> Void)?
    
    func commonInit() {
        addSubview(hintLabel)
        addSubview(dividine)
        addSubview(hashTag)
        addSubview(questionLabel)
        addSubview(categoryLabel)
        addSubview(paraIcon)
        addSubview(ansPrevLabel)
        addSubview(dateLabel)
        addSubview(cyxbsIcon)
        addSubview(likeButton)
        addSubview(likeCountLabel)
        setPosition()
    }
    
    // MARK: - 设置视图布局
    
    func setPosition() {
        
        hintLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(32)
            make.top.equalToSuperview().offset(12)
            make.width.equalTo(114)
            make.height.equalTo(24)
        }
        
        dividine.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(32)
            make.right.equalToSuperview().offset(-32)
            make.top.equalToSuperview().offset(42)
            make.height.equalTo(0.5)
        }
        
        hashTag.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(32)
            make.top.equalTo(dividine.snp.top).offset(20)
            make.height.equalTo(22)
            make.width.equalTo(22)
        }
        
        paraIcon.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(40)
            make.top.equalTo(dividine.snp.top).offset(65)
            make.height.equalTo(6)
            make.width.equalTo(6)
        }
        
        ansPrevLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(64)
            make.top.equalTo(dividine.snp.top).offset(56)
            make.right.equalToSuperview().offset(-64)
        }
        
        questionLabel.snp.makeConstraints{ make in
            make.top.equalTo(dividine.snp.top).offset(20)
            make.left.equalToSuperview().offset(64)
            make.height.equalTo(22)
            make.right.lessThanOrEqualTo(categoryLabel.snp.left).offset(-8)
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.top.equalTo(dividine.snp.top).offset(23)
            make.left.equalTo(questionLabel.snp.right).offset(20)
            make.height.equalTo(16)
            make.width.equalTo(48)
            make.left.greaterThanOrEqualTo(questionLabel.snp.right).offset(8)
        }
        
        dateLabel.snp.makeConstraints{ make in
            make.bottom.equalToSuperview().offset(-17)
            make.left.equalToSuperview().offset(32)
            make.width.equalTo(70)
            make.height.equalTo(17)
        }
        
        cyxbsIcon.snp.makeConstraints{ make in
            make.right.equalToSuperview()
            make.bottom.equalToSuperview()
            make.width.equalTo(100)
            make.height.equalTo(67)
        }
        
        likeButton.snp.makeConstraints{ make in
            make.bottom.equalToSuperview().offset(-15)
            make.right.equalToSuperview().offset(-46)
            make.height.equalTo(24)
            make.width.equalTo(24)
        }
        
        likeCountLabel.snp.makeConstraints{ make in
            make.right.equalToSuperview().offset(-15.25)
            make.bottom.equalToSuperview().offset(-28)
            make.width.equalTo(30)
            make.height.equalTo(18)
        }
        
        questionLabel.setContentCompressionResistancePriority(.defaultHigh, for: .horizontal)
        categoryLabel.setContentCompressionResistancePriority(.required, for: .horizontal)
        
        questionLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        categoryLabel.setContentHuggingPriority(.defaultHigh, for: .horizontal)
        
    }
    
    // MARK: - 懒加载
    
    lazy var hintLabel : UILabel = {
        let hintLabel = UILabel()
        hintLabel.text = "你的问题已有答案"
        hintLabel.font = UIFont(name: PingFangSC, size: 14)
        hintLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        hintLabel.textAlignment = .left
        hintLabel.numberOfLines = 1
        hintLabel.backgroundColor = .clear
        return hintLabel
    }()
    
    lazy var dividine : UIView = {
        let dividine = UIView()
        dividine.backgroundColor = UIColor(light: UIColor(hexString:"#F2F4FF"), dark: UIColor(hexString: "#818181", alpha: 0.4))
        return dividine
    }()
    
    lazy var hashTag : UIImageView = {
        let hashTag = UIImageView()
        hashTag.image = UIImage(named: "HashTag")
        return hashTag
    }()
    
    lazy var questionLabel : UILabel = {
        let questionLabel = UILabel()
        questionLabel.font = UIFont(name: PingFangSC, size: 16)
        questionLabel.textColor = UIColor.ry(light: "#15315B", dark: "#767677")
        questionLabel.backgroundColor = .clear
        questionLabel.numberOfLines = 1
        return questionLabel
    }()
    
    lazy var categoryLabel : UILabel = {
        let categoryLabel = UILabel()
        categoryLabel.font = UIFont(name: PingFangSC, size: 10)
        categoryLabel.textAlignment = .center
        categoryLabel.textColor = UIColor.ry(light: "#4A44E4", dark: "#D2D2D2")
        categoryLabel.backgroundColor = UIColor.ry(light: "#D4DAFF", dark: "#5A5A5A")
        categoryLabel.layer.cornerRadius = 8
        categoryLabel.layer.masksToBounds = true
        return categoryLabel
    }()
    
    lazy var paraIcon : UIImageView = {
        let paraIcon = UIImageView()
        paraIcon.image = UIImage(named: "Ellipse")
        return paraIcon
    }()
    
    lazy var ansPrevLabel : UILabel = {
        let ansPrevLabel = UILabel()
        ansPrevLabel.font = UIFont(name: PingFangSC, size: 16)
        ansPrevLabel.textAlignment = .left
        ansPrevLabel.backgroundColor = .clear
        ansPrevLabel.numberOfLines = 2
        ansPrevLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677", alpha: 1))
        return ansPrevLabel
    }()
    
    lazy var dateLabel : UILabel = {
        let dateLabel = UILabel()
        dateLabel.font = UIFont(name: PingFangSC, size: 12)
        dateLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        return dateLabel
    }()
    
    lazy var cyxbsIcon : UIImageView = {
        let cyxbsIcon = UIImageView()
        cyxbsIcon.image = UIImage(named: "CyxbsWaterMark")
        return cyxbsIcon
    }()
    
    lazy var likeButton : UIButton = {
        let likeButton = UIButton()
        likeButton.setImage(UIImage(named: "UnLike"), for: .normal)
        likeButton.setImage(UIImage(named: "Like"), for: .selected)
        return likeButton
    }()
    
    lazy var likeCountLabel : UILabel = {
        let likeCountLabel = UILabel()
        likeCountLabel.font = UIFont(name: PingFangSC, size: 12)
        likeCountLabel.textAlignment = .left
        likeCountLabel.textColor = UIColor.ry(light: "#A7B6D1", dark: "#FFFFFF")
        return likeCountLabel
    }()
    
    // MARK: - 处理视图点击事件（闭包回掉）
    
    private func addTapGesture() {
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        addGestureRecognizer(tapGesture)
        isUserInteractionEnabled = true
    }
    
    @objc private func handleTap(_ sender: UITapGestureRecognizer) {
        // 点击动画效果
        UIView.animate(withDuration: 0.1, animations: {
            self.transform = CGAffineTransform(scaleX: 0.95, y: 0.95)
            self.backgroundColor = .systemPurple
        }) { _ in
            UIView.animate(withDuration: 0.1) {
                self.transform = .identity
                self.backgroundColor = .systemBlue
            }
        }
        
        // 执行闭包回调
        onTap?()
    }
    
    
}
