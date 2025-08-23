//
//  TopView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class NewQuestionTopView: UIView{
    
    func commonInit(){
        addSubview(backButton)
        addSubview(publishButton)
        setPosition()
    }
    
    func setPosition(){
        
        backButton.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(60)
            make.left.equalToSuperview().offset(20)
            make.height.equalTo(16)
            make.width.equalTo(7)
        }
        
        publishButton.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(55)
            make.right.equalToSuperview().offset(-20)
            make.height.equalTo(28)
            make.width.equalTo(60)
        }
        
    }
    
    lazy var backButton: UIButton = {
        let button = UIButton()
        button.setImage(UIImage(named: "Back"), for: .normal)
        return button
    }()
    
    lazy var publishButton : UIButton = {
        let publishButton = UIButton()
        publishButton.setTitle("发布", for: .normal)
        publishButton.setTitleColor(.white, for: .normal)
        publishButton.titleLabel?.font = UIFont(name: PingFangSC, size: 14)
        publishButton.backgroundColor = UIColor(hexString: "#4841E2")
        publishButton.layer.cornerRadius = 14
        publishButton.layer.masksToBounds = true
        return publishButton
    }()
}

