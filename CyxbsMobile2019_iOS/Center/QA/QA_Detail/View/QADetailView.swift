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
        
    }
    
    func setPosition(){
        
    }
    
    lazy var categoryLabel : UILabel = {
        let categoryLabel = UILabel()
        categoryLabel.backgroundColor = UIColor(hexString: "#E1E9FA")
        categoryLabel.textColor = UIColor(hexString: "#4594D5")
        categoryLabel.font = UIFont(name: PingFangSC, size: 16)
        categoryLabel.textAlignment = .center
        return categoryLabel
    }()
    
    lazy var questionLabel : UILabel = {
        let questionLabel = UILabel()
        questionLabel.font = UIFont(name: PingFangSC, size: 20)
        return questionLabel
    }()
    
    lazy var dateLabel : UILabel = {
        let dateLabel = UILabel()
        dateLabel.font = UIFont(name: PingFangSC, size: 16)
        dateLabel.textColor = UIColor(hexString: "#707070")
        return dateLabel
    }()
    
    lazy var viewIconImage : UIImageView = {
        let viewIconImage = UIImageView()
        viewIconImage.image = UIImage(named: "眼睛1")
        return viewIconImage
    }()
    
    lazy var viewCountLabel : UILabel = {
        let viewCountLabel = UILabel()
        viewCountLabel.font = UIFont(name: PingFangSC, size: 16)
        viewCountLabel.textColor = UIColor(hexString: "#707070")
        return viewCountLabel
    }()
    
}
