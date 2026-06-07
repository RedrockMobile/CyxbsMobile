//
//  GroupTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/24.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let GroupTableViewCellReuseIdentifier = "GroupTableViewCell"

class GroupTableViewCell: UITableViewCell {
    
    // MARK: - Life Cycle
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(imgView)
        contentView.addSubview(nameLab)
        backgroundColor = .weDatePageBackground
        contentView.backgroundColor = .weDatePageBackground
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Lazy
    
    /// 头像图片(统一)
    private lazy var imgView: UIImageView = {
        let imgView = UIImageView(frame: CGRect(x: 16, y: 0, width: 34, height: 34))
        imgView.image = UIImage(named: "分组标识头像")
        return imgView
    }()
    /// 组名文本
    lazy var nameLab: UILabel = {
        let nameLab = UILabel(frame: CGRect(x: imgView.right + 9, y: 6, width: 250, height: 22))
        nameLab.font = .boldSystemFont(ofSize: 16)
        nameLab.textColor = .weDateDeepText
        return nameLab
    }()
}
