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
            make.top.equalToSuperview().offset(23)
            make.height.equalTo(6)
        }
        
        contentLabel.snp.makeConstraints { make in
            make.left.equalToSuperview().offset(32)
            make.top.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-32)
            //let text = contentLabel.text ?? ""
            //make.height.equalTo(textHeightFromTextString(text: text, textWidth: UIScreen.main.bounds.width-96, fontSize: 14, isBold: false))
        }
        
        viewIcon.snp.makeConstraints { make in
            make.bottom.equalToSuperview().offset(-15)
            make.right.equalToSuperview().offset(-106)
            make.height.equalTo(24)
            make.width.equalTo(24)
        }
        
        viewCountLabel.snp.makeConstraints { make in
            make.right.equalToSuperview().offset(-75.25)
            make.bottom.equalToSuperview().offset(-28)
            make.width.equalTo(30)
            make.height.equalTo(18)
        }
        
        likeButton.snp.makeConstraints { make in
            make.bottom.equalToSuperview().offset(-15)
            make.right.equalToSuperview().offset(-46)
            make.height.equalTo(24)
            make.width.equalTo(24)
        }
        
        likeCountLabel.snp.makeConstraints { make in
            make.right.equalToSuperview().offset(-15.25)
            make.bottom.equalToSuperview().offset(-28)
            make.width.equalTo(30)
            make.height.equalTo(18)
        }
        
        cyxbsIcon.snp.makeConstraints { make in
            make.right.equalToSuperview()
            make.bottom.equalToSuperview()
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
    
    
    
    func textHeightFromTextString(text: String, textWidth: CGFloat, fontSize: CGFloat, isBold: Bool) -> CGFloat {
        var dict: NSDictionary = NSDictionary()
        if (isBold) {
            dict = NSDictionary(object: UIFont.boldSystemFont(ofSize: fontSize),forKey: NSAttributedString.Key.font as NSCopying)
        } else {
            dict = NSDictionary(object: UIFont.systemFont(ofSize: fontSize),forKey: NSAttributedString.Key.font as NSCopying)
        }
        
        let rect: CGRect = (text as NSString).boundingRect(with: CGSize(width: textWidth,height: CGFloat(MAXFLOAT)), options: [NSStringDrawingOptions.truncatesLastVisibleLine, NSStringDrawingOptions.usesFontLeading,NSStringDrawingOptions.usesLineFragmentOrigin],attributes: dict as? [NSAttributedString.Key : Any] ,context: nil)
        return rect.size.height
    }
    
}
