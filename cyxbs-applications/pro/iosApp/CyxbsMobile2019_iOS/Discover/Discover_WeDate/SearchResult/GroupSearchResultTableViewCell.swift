//
//  GroupSearchResultTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/9.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let GroupSearchResultTableViewCellReuseIdentifier = "GroupSearchResultTableViewCell"

class GroupSearchResultTableViewCell: UITableViewCell {

    weak var delegate: SearchResultTableViewCellDelegate?
    
    // MARK: - Life Cycle
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(imgView)
        contentView.addSubview(nameLab)
        contentView.addSubview(addBtn)
        backgroundColor = .weDatePanelBackground
        contentView.backgroundColor = .weDatePanelBackground
    }
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Method
    
    @objc private func clickAddBtn(_ sender: UIButton) {
        delegate?.addDataAt(sender.tag)
    }
    
    // MARK: - Lazy
    
    /// 头像图片(默认)
    private lazy var imgView: UIImageView = {
        let imgView = UIImageView(frame: CGRect(x: 16, y: 0, width: 34, height: 34))
        imgView.image = UIImage(named: "分组标识头像")
        return imgView
    }()
    /// 组名文本
    lazy var nameLab: UILabel = {
        let nameLab = UILabel(frame: CGRect(x: imgView.right + 10, y: 4.8, width: 170, height: 24.65))
        nameLab.font = .systemFont(ofSize: 18, weight: .black)
        nameLab.textColor = .weDatePrimaryText
        return nameLab
    }()
    /// 加号按钮
    lazy var addBtn: UIButton = {
        let addBtn = UIButton(type: .system)
        addBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 17, y: 8.5, width: 17, height: 17)
        addBtn.setBackgroundImage(UIImage(named: "深色加号"), for: .normal)
        addBtn.addTarget(self, action: #selector(clickAddBtn), for: .touchUpInside)
        return addBtn
    }()
}
