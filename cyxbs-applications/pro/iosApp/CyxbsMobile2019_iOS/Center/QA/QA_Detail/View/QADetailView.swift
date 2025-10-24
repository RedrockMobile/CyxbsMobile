//
//  QADetailView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/14.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class QADetailView : UIView{
    
    func commonInit(){
        addSubview(hashTag)
        addSubview(questionLabel)
        addSubview(categoryLabel)
        addSubview(dateLabel)
        addSubview(answerDetailView)
        setPosition()
    }
    
    func setPosition(){
        
        hashTag.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(32)
            make.top.equalToSuperview()
            make.height.equalTo(22)
            make.width.equalTo(22)
        }
        
        questionLabel.snp.makeConstraints{ make in
            make.left.equalTo(hashTag.snp.right).offset(10)
            make.top.equalToSuperview()
            make.right.lessThanOrEqualTo(categoryLabel.snp.left).offset(-10)
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.right.equalToSuperview().offset(-32)
            make.top.equalToSuperview().offset(3)
            make.height.equalTo(16)
            make.width.equalTo(48)
        }
        
        dateLabel.snp.makeConstraints{ make in
            make.left.equalTo(hashTag.snp.right).offset(10)
            make.top.equalTo(questionLabel.snp.bottom).offset(8)
            make.right.equalToSuperview().offset(-16)
        }
        
        answerDetailView.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.top.equalTo(dateLabel.snp.bottom).offset(20)
            make.right.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-20)
        }
    }
    
    lazy var hashTag : UIImageView = {
        let hashTag = UIImageView()
        hashTag.image = UIImage(named: "HashTag")
        return hashTag
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
    
    lazy var questionLabel : UILabel = {
        let questionLabel = UILabel()
        questionLabel.font = UIFont(name: PingFangSC, size: 16)
        questionLabel.textColor = UIColor.ry(light: "#15315B", dark: "#767677")
        questionLabel.backgroundColor = .clear
        questionLabel.numberOfLines = 0 // 允许多行显示
        return questionLabel
    }()
    
    lazy var dateLabel : UILabel = {
        let dateLabel = UILabel()
        dateLabel.font = UIFont(name: PingFangSC, size: 12)
        dateLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        return dateLabel
    }()
    
    lazy var answerDetailView : AnswerDetailView = {
        let answerDetailView = AnswerDetailView()
        answerDetailView.commonInit()
        answerDetailView.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#2D2D2D")
        answerDetailView.layer.cornerRadius = 8
        answerDetailView.layer.masksToBounds = true
        return answerDetailView
    }()
}
