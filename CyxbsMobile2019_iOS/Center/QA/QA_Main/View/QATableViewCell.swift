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
        
        hashTag.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(12)
            make.top.equalToSuperview().offset(16)
            make.height.equalTo(22)
            make.width.equalTo(22)
        }
        
        paraIcon.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(24)
            make.top.equalToSuperview().offset(57)
            make.height.equalTo(6)
            make.width.equalTo(6)
        }
        
        ansPrevLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(48)
            make.top.equalToSuperview().offset(48)
            make.right.equalToSuperview().offset(-16)
        }
        
        questionLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(12)
            make.left.equalToSuperview().offset(48)
            make.height.equalTo(22)
            make.width.equalTo(200)
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(15)
            make.right.equalToSuperview().offset(16)
            make.height.equalTo(16)
            make.width.equalTo(48)
        }
        
        dateLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(99)
            make.left.equalToSuperview().offset(16)
            make.width.equalTo(70)
            make.height.equalTo(17)
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
        
        cyxbsIcon.snp.makeConstraints{ make in
            make.right.equalToSuperview()
            make.bottom.equalToSuperview()
            make.width.equalTo(100)
            make.height.equalTo(67)
        }
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
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
        return categoryLabel
    }()
    
    lazy var dateLabel : UILabel = {
        let dateLabel = UILabel()
        dateLabel.font = UIFont(name: PingFangSC, size: 12)
        dateLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        return dateLabel
    }()
    
    lazy var likeButton : UIButton = {
        let likeButton = UIButton()
        likeButton.setImage(UIImage(named: "Unlike"), for: .normal)
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
    
    lazy var cyxbsIcon : UIImageView = {
        let cyxbsIcon = UIImageView()
        cyxbsIcon.image = UIImage(named: "CyxbsWaterMark")
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
