//
//  ScheduleCollectionViewCell.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/17.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class ScheduleCollectionViewCell: UICollectionViewCell {
    
    // MARK: ReuseIdentifier
    
    static let curriculumReuseIdentifier = "CyxbsMobile2019_iOS.ScheduleCollectionViewCell.curriculum"
    static let supplementaryReuseIdentifier = "CyxbsMobile2019_iOS.ScheduleCollectionViewCell.supplementary"
    
    // MARK: DrawType
    
    enum DrawType {
        
        enum CurriculumType {
            case allBusy
            case leisureMore
            case busyMore
            case allLeisure
        }
        
        enum SupplementaryType {
            case normal
            case today
        }
        
        case curriculum(CurriculumType)
        case supplementary(SupplementaryType)
    }
    
    // MARK: init

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.layer.cornerRadius = 8
        contentView.clipsToBounds = true
        contentView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        
        contentView.addSubview(titleLab)
        contentView.addSubview(contentLab)
        updateFrame()
        
        contentView.insertSubview(darkBackView, at: 0)
        contentView.insertSubview(lightBackView, at: 0)
        darkBackView.isHidden = true
        lightBackView.isHidden = true
        
        contentView.addSubview(timePointerView)
        timePointerView.isHidden = true
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private(set) var drawType: DrawType = .curriculum(.allBusy)
    
    private(set) var isTitleOnly: Bool = false
    
    override func apply(_ layoutAttributes: UICollectionViewLayoutAttributes) {
        super.apply(layoutAttributes)
        updateFrame()
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        lightBackView.frame = contentView.frame
        lightBackView.top += 10
        darkBackView.frame = contentView.frame
        darkBackView.height -= 5
        timePointerView.frame = CGRect(x: 2, y: 0, width: contentView.width - 3, height: 6)
    }
    
    // MARK: lazy
    
    lazy var titleLab: UILabel = {
        let lab = UILabel()
        lab.backgroundColor = .clear
        lab.textAlignment = .center
        lab.numberOfLines = 3
        return lab
    }()
    
    lazy var contentLab: UILabel = {
        let lab = UILabel()
        lab.backgroundColor = .clear
        lab.textAlignment = .center
        lab.numberOfLines = 3
        return lab
    }()
    
    private lazy var lightBackView: UIView = {
        let lightBackView = UIView()
        lightBackView.backgroundColor = .weDateTodayColumnBackground
        return lightBackView
    }()
    
    private lazy var darkBackView: UIView = {
        let darkBackView = UIView()
        darkBackView.backgroundColor = .weDateTodayHighlight
        darkBackView.layer.cornerRadius = 8
        darkBackView.clipsToBounds = true
        return darkBackView
    }()
    
    lazy var timePointerView: UIImageView = {
        let timePointerView = UIImageView()
        timePointerView.image = UIImage(named: "课表指示")
        return timePointerView
    }()
}

// MARK: - init

extension ScheduleCollectionViewCell {
    
    func initCurriculum() {
        drawType = .curriculum(.allBusy)
        titleLab.font = .systemFont(ofSize: 10, weight: .regular)
        contentLab.font = .systemFont(ofSize: 10, weight: .regular)
    }
    
    func initSupplementary() {
        drawType = .supplementary(.normal)
        titleLab.font = .systemFont(ofSize: 12, weight: .regular)
        contentLab.font = .systemFont(ofSize: 11, weight: .regular)
    }
}

// MARK: Method

extension ScheduleCollectionViewCell {
    
    func set(curriculumType: DrawType.CurriculumType, title: String?) {
        drawType = .curriculum(curriculumType)
        initCurriculum()
        titleLab.text = title
        switch curriculumType {
            
        case .allBusy:
            contentView.backgroundColor = .weDateBusyAllBackground
            titleLab.textColor = .weDateBusyStateText
            
        case .leisureMore:
            contentView.backgroundColor = .weDateLeisureMoreBackground
            titleLab.textColor = .weDateLeisureStateText
            
        case .busyMore:
            contentView.backgroundColor = .weDateBusyMoreBackground
            titleLab.textColor = .weDateBusyMoreText
            
        case .allLeisure:
            contentView.backgroundColor = .clear
            titleLab.textColor = .clear
        }
        updateFrame()
    }
    
    func set(supplementaryType: DrawType.SupplementaryType, title: String?, content: String?, isTitleOnly: Bool, isNeedTimePointer: Bool) {
        drawType = .supplementary(supplementaryType)
        initSupplementary()
        self.isTitleOnly = isTitleOnly
        titleLab.text = title
        contentLab.text = content
        switch supplementaryType {
            
        case .normal:
            contentView.backgroundColor = .weDatePageBackground
            contentView.layer.cornerRadius = 0
            titleLab.textColor = .weDatePrimaryText
            contentLab.textColor = .weDateSecondaryText
            lightBackView.isHidden = true
            darkBackView.isHidden = true
            
            if isNeedTimePointer {
                timePointerView.isHidden = false
            } else {
                timePointerView.isHidden = true
            }
            
        case .today:
            titleLab.textColor = .white
            contentView.backgroundColor = .weDatePageBackground
            lightBackView.isHidden = false
            darkBackView.isHidden = false
        }
        updateFrame()
    }
    
    func updateFrame() {
        switch drawType {
        case .curriculum(_):
            titleLab.frame.size.width = bounds.width - 2 * 13
            titleLab.sizeToFit()
            titleLab.center.x = bounds.width / 2
            titleLab.center.y = bounds.height / 2
            contentLab.isHidden = true
            
        case .supplementary(_):
            titleLab.sizeToFit()
            titleLab.frame.origin.x = 0
            titleLab.frame.size.width = bounds.width
            
            contentLab.sizeToFit()
            contentLab.frame.origin.x = 0
            contentLab.frame.size.width = bounds.width
            
            if isTitleOnly {
                titleLab.center.y = bounds.height / 2
            } else {
                titleLab.frame.origin.y = 13
            }
            contentLab.isHidden = isTitleOnly
            contentLab.frame.origin.y = bounds.height - contentLab.bounds.height - 13
        }
    }
}
