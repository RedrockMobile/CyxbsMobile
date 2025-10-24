//
//  QASearchTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class QASearchTableViewCell : UITableViewCell {
    
    private let space: CGFloat = 16
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        contentView.backgroundColor = .clear
        contentView.addSubview(containerView)
        containerView.addSubview(hashTag)
        containerView.addSubview(questionLabel)
        containerView.addSubview(categoryLabel)
        containerView.addSubview(paraIcon)
        containerView.addSubview(ansPrevLabel)
        containerView.addSubview(dateLabel)
        containerView.addSubview(likeButton)
        containerView.addSubview(likeCountLabel)
        containerView.addSubview(cyxbsIcon)
        
        setPosition()
    }
    
    func setPosition(){
        
        containerView.snp.makeConstraints{ make in
            make.top.equalToSuperview()
            make.bottom.equalToSuperview().offset(-16)
            make.left.right.equalToSuperview()
        }
        
        hashTag.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(14)
            make.height.equalTo(22)
            make.width.equalTo(22)
        }
        
        questionLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(14)
            make.left.equalToSuperview().offset(48)
            make.right.lessThanOrEqualTo(categoryLabel.snp.left).offset(-8)
        }
        
        categoryLabel.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(18)
            make.right.equalToSuperview().offset(-32)
            make.height.equalTo(16)
            make.width.equalTo(48)
        }
        
        paraIcon.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(24)
            make.top.equalTo(questionLabel.snp.bottom).offset(14)
            make.height.equalTo(6)
            make.width.equalTo(6)
        }
        
        ansPrevLabel.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(48)
            make.top.equalTo(questionLabel.snp.bottom).offset(5)
            make.right.equalToSuperview().offset(-64)
        }
        
        dateLabel.snp.makeConstraints{ make in
            make.bottom.equalToSuperview().offset(-16)
            make.left.equalToSuperview().offset(16)
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
        
        containerView.bringSubviewToFront(likeButton)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    lazy var containerView : UIView = {
        let containerView = UIView()
        containerView.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#2A2A2A")
        containerView.layer.cornerRadius = 8
        containerView.layer.masksToBounds = true
        return containerView
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
        questionLabel.numberOfLines = 0
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
}
