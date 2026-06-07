//
//  StudentSearchResultTableViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/9.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

protocol SearchResultTableViewCellDelegate: AnyObject {
    func addDataAt(_ index: Int)
}

/// 复用标志
let StudentSearchResultTableViewCellReuseIdentifier = "StudentSearchResultTableViewCell"

class StudentSearchResultTableViewCell: UITableViewCell {

    weak var delegate: SearchResultTableViewCellDelegate?
    /// 是否需要头像(仅组名与学生重名时才需要)
    var needPicture = Bool()
    
    // MARK: - Life Cycle
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(imgView)
        contentView.addSubview(nameLab)
        contentView.addSubview(studentIDLab)
        contentView.addSubview(addBtn)
        contentView.addSubview(departLab)
        backgroundColor = .weDatePanelBackground
        contentView.backgroundColor = .weDatePanelBackground
    }
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        
        if needPicture {
            imgView.frame = CGRect(x: 16, y: 4, width: 34, height: 34)
            nameLab.frame = CGRect(x: imgView.right + 10, y: 0, width: 80, height: 22)
        } else {
            imgView.frame = .zero
            nameLab.frame = CGRect(x: 16, y: 0, width: 80, height: 22)
        }
        addBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 17, y: 12.5, width: 17, height: 17)
        departLab.snp.makeConstraints { make in
            make.left.equalTo(nameLab.snp.left)
            make.top.equalTo(nameLab.snp.bottom).offset(5)
            make.height.equalTo(17)
        }
        studentIDLab.snp.makeConstraints { make in
            make.top.height.equalTo(departLab)
            make.left.equalTo(departLab.snp.right).offset(5)
        }
    }
    
    // MARK: - Method
    
    @objc private func clickAddBtn(_ sender: UIButton) {
        delegate?.addDataAt(sender.tag)
    }
    
    // MARK: - Lazy
    
    /// 头像图片(默认)
    private lazy var imgView: UIImageView = {
        let imgView = UIImageView()
        imgView.image = UIImage(named: "个人标识头像")
        return imgView
    }()
    /// 姓名文本
    lazy var nameLab: UILabel = {
        let nameLab = UILabel()
        nameLab.font = .boldSystemFont(ofSize: 16)
        nameLab.textColor = .weDateDeepText
        return nameLab
    }()
    /// 学号文本
    lazy var studentIDLab: UILabel = {
        let studentIDLab = UILabel()
        studentIDLab.font = .systemFont(ofSize: 12)
        studentIDLab.textColor = .weDateMutedText
        return studentIDLab
    }()
    /// 加号按钮
    lazy var addBtn: UIButton = {
        let addBtn = UIButton(type: .system)
        addBtn.setBackgroundImage(UIImage(named: "浅色加号"), for: .normal)
        addBtn.addTarget(self, action: #selector(clickAddBtn), for: .touchUpInside)
        return addBtn
    }()
    /// 学院文本
    lazy var departLab: UILabel = {
        let departLab = UILabel()
        departLab.font = .systemFont(ofSize: 12)
        departLab.textColor = .weDateMutedText
        return departLab
    }()
}
