//
//  SearchResultTableViewHeaderView.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/21.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let SearchResultTableViewHeaderViewReuseIdentifier = "SearchResultTableViewHeaderView"

/// 当搜索结果有且仅有分组时展示此header
class SearchResultTableViewHeaderView: UITableViewHeaderFooterView {
    
    // MARK: - Life Cycle
    
    override init(reuseIdentifier: String?) {
        super.init(reuseIdentifier: reuseIdentifier)
        contentView.addSubview(nameLab)
        contentView.addSubview(addBtn)
        contentView.backgroundColor = .weDatePanelBackground
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Lazy
    
    /// 组名文本
    lazy var nameLab: UILabel = {
        let nameLab = UILabel(frame: CGRect(x: 16, y: 19, width: 200, height: 24.65))
        nameLab.font = .systemFont(ofSize: 18, weight: .black)
        nameLab.textColor = .weDatePrimaryText
        return nameLab
    }()
    /// 加号按钮
    lazy var addBtn: UIButton = {
        let addBtn = UIButton(type: .system)
        addBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 17, y: nameLab.top + 4, width: 17, height: 17)
        addBtn.setBackgroundImage(UIImage(named: "深色加号"), for: .normal)
        return addBtn
    }()
}
