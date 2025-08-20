//
//  QATableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/19.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class QATableViewCell : UITableViewCell {
    
    private let space: CGFloat = 16
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#0D0D0D")
        selectionStyle = .none
        contentView.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#2D2D2D")
        contentView.addSubview(hashTag)
        contentView.addSubview(questionLabel)
        contentView.addSubview(categoryLabel)
        contentView.addSubview(paraIcon)
        contentView.addSubview(ansPrevLabel)
        contentView.addSubview(dateLabel)
        contentView.addSubview(likeButton)
        contentView.addSubview(likeCountLabel)
        contentView.addSubview(cyxbsIcon)
        setPosition()
    }
    
    func setPosition(){
        //let questionLabelLength = calculateLabelWidth(labelText: questionLabel.text!)
        
        questionLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(12)
            make.left.equalToSuperview().offset(48)
            make.height.equalTo(22)
            make.width.equalTo(200)
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(15)
            make.left.equalTo(questionLabel).offset(200 + 12)
            make.height.equalTo(16)
            make.width.equalTo(48)
        }
        
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    lazy var hashTag : UIImageView = {
        let hashTag = UIImageView()
        hashTag.image = UIImage(named: "HashTag")
        hashTag.frame = CGRectMake(16, 12, 22, 22)
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
        return categoryLabel
    }()
    
    lazy var paraIcon : UIImageView = {
        let paraIcon = UIImageView()
        paraIcon.image = UIImage(named: "Ellipse")
        paraIcon.frame = CGRectMake(24, 57, 6, 6)
        return paraIcon
    }()
    
    lazy var ansPrevLabel : UILabel = {
        let ansPrevLabel = UILabel()
        ansPrevLabel.font = UIFont(name: PingFangSC, size: 16)
        ansPrevLabel.textAlignment = .left
        ansPrevLabel.backgroundColor = .clear
        ansPrevLabel.numberOfLines = 2
        ansPrevLabel.frame = CGRectMake(48, 48, 280, 47)
        ansPrevLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677", alpha: 1))
        return categoryLabel
    }()
    
    lazy var dateLabel : UILabel = {
        let dateLabel = UILabel()
        dateLabel.font = UIFont(name: PingFangSC, size: 12)
        dateLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        dateLabel.frame = CGRectMake(16, 99, 70, 17)
        return dateLabel
    }()
    
    lazy var likeButton : UIButton = {
        let likeButton = UIButton()
        likeButton.setImage(UIImage(named: "Unlike"), for: .normal)
        likeButton.setImage(UIImage(named: "Like"), for: .selected)
        likeButton.frame = CGRectMake(273, 93, 24, 24)
        return likeButton
    }()
    
    lazy var likeCountLabel : UILabel = {
        let likeCountLabel = UILabel()
        likeCountLabel.font = UIFont(name: PingFangSC, size: 12)
        likeCountLabel.textAlignment = .left
        likeCountLabel.textColor = UIColor.ry(light: "#A7B6D1", dark: "#FFFFFF")
        likeCountLabel.frame = CGRectMake(297.75, 86, 30, 18.7)
        return likeCountLabel
    }()
    
    lazy var cyxbsIcon : UIImageView = {
        let cyxbsIcon = UIImageView()
        cyxbsIcon.image = UIImage(named: "CyxbsWaterMark")
        cyxbsIcon.frame = CGRectMake(243, 65, 100, 67)
        return cyxbsIcon
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
