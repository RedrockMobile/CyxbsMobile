//
//  RepeatNameTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/9.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 复用标志
let RepeatNameTableViewCellReuseIdentifier = "RepeatNameTableViewCell"

protocol RepeatNameTableViewCellDelegate: AnyObject {
    func addStudentDataAt(_ index: Int)
}

class RepeatNameTableViewCell: UITableViewCell {
    
    weak var delegate: RepeatNameTableViewCellDelegate?
    /// 按钮是否选中
    var isButtonSelected: Bool = false {
        willSet {
            if newValue {
                button.isSelected = true
            } else {
                button.isSelected = false
            }
        }
    }

    // MARK: - Life Cycle
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(nameLab)
        contentView.addSubview(stuNumLab)
        contentView.addSubview(button)
        contentView.addSubview(departLab)
        backgroundColor = .weDatePanelBackground
        contentView.backgroundColor = .weDatePanelBackground
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        
        nameLab.frame = CGRect(x: 16, y: 0, width: SCREEN_WIDTH / 2, height: 20)
        button.frame = CGRect(x: SCREEN_WIDTH - 16 - 17, y: 12.5, width: 17, height: 17)
        departLab.snp.makeConstraints { make in
            make.left.equalTo(nameLab.snp.left)
            make.top.equalTo(nameLab.snp.bottom).offset(5)
            make.height.equalTo(17)
        }
        stuNumLab.snp.makeConstraints { make in
            make.top.height.equalTo(departLab)
            make.left.equalTo(departLab.snp.right).offset(5)
        }
    }
    
    // MARK: - Method
    
    @objc private func clickBtn(_ sender: UIButton) {
        delegate?.addStudentDataAt(sender.tag)
    }
    
    // MARK: - Lazy
    
    /// 姓名文本
    lazy var nameLab: UILabel = {
        let nameLab = UILabel()
        nameLab.font = .boldSystemFont(ofSize: 14)
        nameLab.textColor = .weDateDeepText
        return nameLab
    }()
    /// 学号文本
    lazy var stuNumLab: UILabel = {
        let stuNumLab = UILabel()
        stuNumLab.font = .boldSystemFont(ofSize: 12)
        stuNumLab.textColor = .weDateMutedText
        return stuNumLab
    }()
    /// 圆圈按钮
    lazy var button: UIButton = {
        let button = UIButton()
        button.setBackgroundImage(UIImage(named: "圈圈"), for: .normal)
        button.setBackgroundImage(UIImage(named: "圈圈_已选中"), for: .selected)
        button.addTarget(self, action: #selector(clickBtn), for: .touchUpInside)
        return button
    }()
    @objc func imageTapped() {
        print("点击事件")
    }
    /// 学院文本
    lazy var departLab: UILabel = {
        let departLab = UILabel()
        departLab.font = .systemFont(ofSize: 12)
        departLab.textColor = .weDateMutedText
        return departLab
    }()
}
