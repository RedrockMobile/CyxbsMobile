//
//  QAMainTopView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/20.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class QAMainTopView: UIView{
    
    func commonInit(){
        addSubview(backButton)
        addSubview(titleLable)
        setPosition()
    }
    
    func setPosition(){
        
        backButton.snp.makeConstraints{ make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 13)
            make.width.equalTo(9)
            make.height.equalTo(18)
        }
        
        titleLable.snp.makeConstraints{ make in
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 5)
            make.centerX.equalToSuperview()
            make.height.equalTo(31)
            make.width.equalTo(85)
        }
        
    }
    
    let titleLable: UILabel = {
        let label = UILabel()
        label.text = "Q and A"
        label.font = UIFont(name: PingFangSCMedium, size: 22)
        label.textColor = UIColor.ry(light: "#15315B", dark: "#FFFFFF")
        return label
    }()
    
    let backButton: UIButton = {
        let button = UIButton()
        button.setImage(UIImage(named: "Back"), for: .normal)
        return button
    }()
}
