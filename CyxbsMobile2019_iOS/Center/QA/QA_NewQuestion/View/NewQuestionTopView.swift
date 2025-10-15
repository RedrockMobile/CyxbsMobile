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
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 13)
            make.width.equalTo(24)
            make.height.equalTo(18)
        }
        
        publishButton.snp.makeConstraints{ make in
            make.right.equalToSuperview().offset(-16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 13)
            make.width.equalTo(60)
            make.height.equalTo(28)
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

