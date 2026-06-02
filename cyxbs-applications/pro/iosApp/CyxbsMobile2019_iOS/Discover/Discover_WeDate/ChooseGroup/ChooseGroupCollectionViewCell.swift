//
//  ChooseGroupCollectionViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/7.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let ChooseGroupCollectionViewCellReuseIdentifier = "ChooseGroupCollectionViewCell"

protocol ChooseGroupCollectionViewCellDelegate: AnyObject {
    func addGroupIDAt(_ index: Int)
}

class ChooseGroupCollectionViewCell: UICollectionViewCell {
    
    weak var delegate: ChooseGroupCollectionViewCellDelegate?
    
    /// 组名
    var groupName: String {
        get {
            return groupNameBtn.titleLabel?.text ?? ""
        }
        set {
            groupNameBtn.setTitle(newValue, for: .normal)
        }
    }
    /// 按钮是否选中
    var isButtonSelected: Bool = false {
        willSet {
            if newValue {
                hookBtn.isSelected = true
                groupNameBtn.isSelected = true
            } else {
                hookBtn.isSelected = false
                groupNameBtn.isSelected = false
            }
        }
    }
    
    // MARK: - Life Cycle
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.addSubview(hookBtn)
        contentView.addSubview(groupNameBtn)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Method
    
    @objc private func clickBtn(_ sender: UIButton) {
        delegate?.addGroupIDAt(sender.tag)
    }
    
    // MARK: - Lazy
    
    /// 对勾按钮
    lazy var hookBtn: UIButton = {
        let hookBtn = UIButton(frame: CGRect(x: 16, y: 3, width: 15.65, height: 15.65))
        hookBtn.setBackgroundImage(UIImage(named: "对勾"), for: .normal)
        hookBtn.setBackgroundImage(UIImage(named: "对勾_已选中"), for: .selected)
        hookBtn.addTarget(self, action: #selector(clickBtn), for: .touchUpInside)
        return hookBtn
    }()
    /// 组名按钮
    lazy var groupNameBtn: UIButton = {
        let groupNameBtn = UIButton(frame: CGRect(x: hookBtn.right + 7, y: 0, width: 150, height: 22))
        groupNameBtn.contentHorizontalAlignment = .left
        groupNameBtn.setTitleColor(.weDateLightMutedText, for: .normal)
        groupNameBtn.setTitleColor(.weDatePrimaryText, for: .selected)
        groupNameBtn.addTarget(self, action: #selector(clickBtn), for: .touchUpInside)
        return groupNameBtn
    }()
}
