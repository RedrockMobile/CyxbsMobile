//
//  ClassSearchResultTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/9.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let ClassSearchResultTableViewCellReuseIdentifier = "ClassSearchResultTableViewCell"

class ClassSearchResultTableViewCell: UITableViewCell {

    weak var delegate: SearchResultTableViewCellDelegate?
    
    // MARK: - Life Cycle
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(classIDLab)
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
    
    /// 班级号文本
    lazy var classIDLab: UILabel = {
        let classIDLab = UILabel(frame: CGRect(x: 16, y: 10, width: 100, height: 22))
        classIDLab.font = .boldSystemFont(ofSize: 16)
        classIDLab.textColor = .weDateDeepText
        return classIDLab
    }()
    /// 加号按钮
    lazy var addBtn: UIButton = {
        let addBtn = UIButton(type: .system)
        addBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 17, y: 12.5, width: 17, height: 17)
        addBtn.setBackgroundImage(UIImage(named: "浅色加号"), for: .normal)
        addBtn.addTarget(self, action: #selector(clickAddBtn), for: .touchUpInside)
        return addBtn
    }()
}
