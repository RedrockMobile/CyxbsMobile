//
//  RYFinderHeaderView.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/15.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class RYFinderHeaderView: UIView {
    
    var messageBtnTouched: ((RYFinderHeaderView) -> ())?
    
    var attendanceBtnTouched: ((RYFinderHeaderView) -> ())?
    
    //活动通知model，用于获取是否有未读活动通知
    var activityMsgModel: ActivityMessageModel!

    override init(frame: CGRect) {
        super.init(frame: frame)
        
        addSubview(sectionLab)
        addSubview(findLab)
        addSubview(messageBtn)
        addSubview(attendanceBtn)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    lazy var sectionLab: UILabel = {
        let lab = UILabel()
        lab.font = .systemFont(ofSize: 10, weight: .semibold)
        lab.textColor = .ry(light: "#15315B", dark: "#F0F0F2")
        lab.text = "欢迎来到掌上重邮"
        lab.sizeToFit()
        return lab
    }()
    
    lazy var findLab: UILabel = {
        let lab = UILabel()
        lab.font = .systemFont(ofSize: 24, weight: .heavy)
        lab.textColor = .ry(light: "#15315B", dark: "#FFFFFF")
        lab.text = "发现"
        lab.sizeToFit()
        return lab
    }()
    
    lazy var messageBtn: UIButton = {
        let btn = UIButton()
        btn.frame.size = CGSize(width: 40, height: 34)
        btn.autoresizingMask = [.flexibleLeftMargin]
        btn.setImage(messageImage(read: true), for: .normal)
        btn.imageEdgeInsets = UIEdgeInsets(top: 7, left: 8, bottom: 7, right: 8) // 调整内容区域
        btn.imageView?.contentMode = .scaleAspectFit
        btn.addTarget(self, action: #selector(touchUpInside(messageBtn:)), for: .touchUpInside)
        return btn
    }()
    
    lazy var attendanceBtn: UIButton = {
        let btn = UIButton()
        btn.frame.size = CGSize(width: 40, height: 34)
        btn.autoresizingMask = [.flexibleLeftMargin]
        btn.setImage(UIImage(named: "finder_attendance"), for: .normal)
        btn.imageEdgeInsets = UIEdgeInsets(top: 6, left: 8, bottom: 6, right: 8) // 调整内容区域
        btn.imageView?.contentMode = .scaleAspectFit
        btn.addTarget(self, action: #selector(touchUpInside(attendanceBtn:)), for: .touchUpInside)
        return btn
    }()
}

extension RYFinderHeaderView {
    
    func messageImage(read: Bool) -> UIImage? {
        (read ?
        UIImage(named: "finder_message_read") :
        UIImage(named: "finder_message_unread"))
    }
    
    func setupUI() {
        findLab.frame.origin.y = sectionLab.frame.maxY + 8
        
        attendanceBtn.center.y = findLab.center.y
        attendanceBtn.frame.origin.x = bounds.size.width - attendanceBtn.bounds.width
        
        messageBtn.center.y = findLab.center.y
        messageBtn.frame.origin.x = attendanceBtn.frame.minX - messageBtn.bounds.width - 10
    }
}

extension RYFinderHeaderView {
    
    func reloadData() {
        if let nowWeek = UserModel.default.nowWeek() {
            
            if nowWeek >= 0 {
                
                let sectionTitle = ScheduleDataFetch.sectionString(withSection: nowWeek)
                let EETitle = Date().string(locale: .cn, format: "EEE")
                
                sectionLab.text = "\(sectionTitle) \(EETitle)"
            } else {
                
                sectionLab.text = "假期中..."
            }
        } else {
            
            sectionLab.text = "欢迎来到掌上重邮"
        }
        sectionLab.sizeToFit()
        
        request()
    }
    
    func request() {
        HttpManager.shared.message_system_user_msgHasRead().ry_JSON { response in
            if case .success(let model) = response {
                if let status = model["status"].int, status == 10000 {
                    let hasMessage = model["data"]["has"].boolValue
                    self.messageBtn.setImage(self.messageImage(read: !hasMessage), for: .normal)
                    
                    let tabBarVC = Constants.keyWindow?.rootViewController as? UITabBarController
                    tabBarVC?.viewControllers?.last?.tabBarItem.needShowBadgePoint = hasMessage
                }
            }
        }
        
        activityMsgModel = ActivityMessageModel()
        activityMsgModel.requestActivityMessages(lower_id: nil) {
            self.messageBtn.setImage(self.messageImage(read: !self.activityMsgModel.needDot), for: .normal)
        } failure: { error in
            print(error)
        }
    }
}

extension RYFinderHeaderView {
    
    @objc
    func touchUpInside(messageBtn: UIButton) {
        messageBtnTouched?(self)
    }
    
    @objc
    func touchUpInside(attendanceBtn: UIButton) {
        attendanceBtnTouched?(self)
    }
}
