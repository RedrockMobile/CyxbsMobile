//
//  WeDateTopView.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 没课约顶部视图
class WeDateTopView: UIView {
    
    // MARK: - Life Cycle
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(returnBtn)
        addSubview(label)
        addSubview(batchAddBtn)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Lazy
    
    /// 返回按钮
    lazy var returnBtn: MXBackButton = {
        let returnBtn = MXBackButton(frame: CGRect(x: 16, y: 7, width: 7, height: 16), isAutoHotspotExpand: true)
        returnBtn.setImage(UIImage(named: "空教室返回"), for: .normal)
        return returnBtn
    }()
    /// '没课约'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: returnBtn.right + 13 , y: 0, width: 67, height: 31))
        label.text = "没课约"
        label.font = .systemFont(ofSize: 22, weight: .medium)
        label.textColor = .weDateTitleText
        return label
    }()
    /// 批量添加按钮
    lazy var batchAddBtn: UIButton = {
        let batchAddBtn = UIButton(frame: CGRect(x: SCREEN_WIDTH - 16 - 85, y: 4, width: 85, height: 23))
        batchAddBtn.setBackgroundImage(UIImage(named: "批量添加按钮"), for: .normal)
        return batchAddBtn
    }()
}
