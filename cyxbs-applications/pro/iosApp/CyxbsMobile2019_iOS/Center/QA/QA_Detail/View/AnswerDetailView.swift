//
//  AnswerDetailView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/18.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class AnswerDetailView : UIView {
    
    func commonInit(){
        addSubview(paraIcon)
        addSubview(contentLabel)
        addSubview(viewIcon)
        addSubview(viewCountLabel)
        addSubview(likeButton)
        addSubview(likeCountLabel)
        addSubview(cyxbsIcon)
        setPosition()
    }
    
    func setPosition(){
        
        paraIcon.snp.makeConstraints { make in
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(24)
            make.width.equalTo(6)
            make.height.equalTo(6)
        }
        
        contentLabel.snp.makeConstraints { make in
            make.left.equalTo(paraIcon.snp.right).offset(10)
            make.top.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            // 不要设置高度约束，让内容自动决定高度
        }
        
        viewIcon.snp.makeConstraints { make in
            make.top.equalTo(contentLabel.snp.bottom).offset(16)
            make.right.equalTo(viewCountLabel.snp.left).offset(-4)
            make.height.equalTo(24)
            make.width.equalTo(24)
        }
        
        viewCountLabel.snp.makeConstraints { make in
            make.right.equalTo(likeButton.snp.left).offset(-20)
            make.centerY.equalTo(viewIcon).offset(-6)
        }
        
        likeButton.snp.makeConstraints { make in
            make.right.equalToSuperview().offset(-45)
            make.centerY.equalTo(viewIcon)
            make.height.equalTo(24)
            make.width.equalTo(24)
        }
        
        likeCountLabel.snp.makeConstraints { make in
            make.right.equalTo(likeButton.snp.right).offset(8)
            make.centerY.equalTo(viewIcon).offset(-6)
        }
        
        cyxbsIcon.snp.makeConstraints { make in
            make.right.equalToSuperview()
            make.bottom.equalToSuperview()
            make.height.equalTo(67)
            make.width.equalTo(100)
        }
        
        // 设置底部约束，确保视图高度正确计算
        self.snp.makeConstraints { make in
            make.bottom.equalTo(viewIcon.snp.bottom).offset(16).priority(.high)
        }
    }
    
    lazy var paraIcon : UIImageView = {
        let paraIcon = UIImageView()
        paraIcon.image = UIImage(named: "Ellipse")
        return paraIcon
    }()
    
    lazy var contentLabel : UILabel = {
        let contentLabel = UILabel()
        contentLabel.font = UIFont(name: PingFangSC, size: 14)
        contentLabel.textAlignment = .left
        contentLabel.numberOfLines = 0
        contentLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        return contentLabel
    }()
    
    lazy var cyxbsIcon : UIImageView = {
        let cyxbsIcon = UIImageView()
        cyxbsIcon.image = UIImage(named: "CyxbsWaterMark")
        return cyxbsIcon
    }()
    
    lazy var viewIcon : UIImageView = {
        let viewIcon = UIImageView()
        viewIcon.image = UIImage(named: "View")
        return viewIcon
    }()
    
    lazy var viewCountLabel : UILabel = {
        let viewCountLabel = UILabel()
        viewCountLabel.font = UIFont(name: PingFangSC, size: 12)
        viewCountLabel.textAlignment = .left
        viewCountLabel.textColor = UIColor.ry(light: "#A7B6D1", dark: "#FFFFFF")
        return viewCountLabel
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
