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
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(105)
            make.height.equalTo(22)
            make.width.equalTo(22)
        }
        
        questionLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(48)
            make.top.equalToSuperview().offset(105)
            make.height.equalTo(22)
            make.width.equalTo(calculateLabelWidth(labelText: questionLabel.text!))
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.left.equalTo(questionLabel).offset(questionLabel.width+12)
            make.top.equalToSuperview().offset(108)
            make.height.equalTo(16)
            make.width.equalTo(48)
        }
        
        dateLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(48)
            make.top.equalToSuperview().offset(135)
            make.height.equalTo(17)
            make.width.equalTo(66)
        }
        
        answerDetailView.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(172)
            make.width.equalTo(343)
            make.height.equalTo(answerDetailView.textHeightFromTextString(text: answerDetailView.contentLabel.text!, textWidth: 296, fontSize: 14, isBold: false)+16+71)
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
        return categoryLabel
    }()
    
    lazy var questionLabel : UILabel = {
        let questionLabel = UILabel()
        questionLabel.font = UIFont(name: PingFangSC, size: 16)
        questionLabel.textColor = UIColor.ry(light: "#15315B", dark: "#767677")
        questionLabel.numberOfLines = 1
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
    
    func calculateLabelWidth(labelText:String) -> CGFloat {
        // 1. 创建临时标签
        let tempLabel = UILabel()
        tempLabel.numberOfLines = 1
        tempLabel.font = UIFont.systemFont(ofSize: 16)
        tempLabel.text = labelText
        
        // 3. 计算最大允许高度（考虑行间距）
        let maxHeight : CGFloat = 22
        
        // 4. 计算自适应宽度
        let calculatedSize = tempLabel.sizeThatFits(
            CGSize(width: CGFloat.greatestFiniteMagnitude,
                   height: maxHeight)
        )
        
        return calculatedSize.width
    }
    
}
