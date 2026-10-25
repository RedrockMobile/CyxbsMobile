//
//  ActivityDetailViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by 许晋嘉 on 2023/8/22.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import MBProgressHUD
import Alamofire
import CyxbsApplicationsMultiplatform

protocol ActivityDetailVCDelegate: AnyObject {
    func updateModel(indexPathNum: Int, wantToWatch: Bool)
    func updateModel(indexPathNum: Int, addToDo: Bool)
}

class ActivityDetailVC: UIViewController {
    
    var activity: Activity!
    var numOfIndexPath: Int!
    weak var delegate: ActivityDetailVCDelegate?
    var countdownTimer: Timer?
    var detailView: ActivityDetailView!
    var detailScrollView: UIScrollView!
    var statusLabel: UILabel?
    var isActivityStarted = false  // 添加标志，标记活动是否已开始
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#F8F9FC", dark: "#F8F9FC")
        view.addSubview(backButton)
        view.addSubview(coverImgView)
        view.addSubview(statusImgView)
        view.addSubview(titleLabel)
        view.addSubview(typeImgView)
        view.addSubview(typeLabel)
        view.addSubview(activityWatchNumLabel)
        view.addSubview(wantToWatchButton)
        view.addSubview(distanceStartTimeLabel)
        view.addSubview(dayLabel)
        view.addSubview(hourLabel)
        view.addSubview(minuteLabel)
        view.addSubview(secondLabel)
        setBackGroundView()
        backGroundView1.addSubview(dayNumLabel)
        backGroundView2.addSubview(hourNumLabel)
        backGroundView3.addSubview(minuteNumLabel)
        backGroundView4.addSubview(secondNumLabel)
        addDetailView()
        setPosition()
        self.wantToWatchButton.isEnabled = !(self.activity.wantToWatch ?? true)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        switch activity.state {
        case "ended": setStatusView(statusText: "活动已结束", isEnded: true)
        case "rejected": setStatusView(statusText: "活动未通过审核", isEnded: true)
        default: startCountdownTimer()
        }
    }
    
    
    // MARK: - 懒加载
    lazy var backButton: UIButton = {
        let button = UIButton()
        button.setImage(UIImage(named: "activityBack"), for: .normal)
        button.imageView?.contentMode = .scaleAspectFit
        button.imageEdgeInsets = UIEdgeInsets(top: 7, left: 1, bottom: 8, right: 22)
        button.addTarget(self, action: #selector(popController), for: .touchUpInside)
        return button
    }()
    
    lazy var coverImgView: UIImageView = {
        let imgView = UIImageView()
        imgView.layer.cornerRadius = 8
        imgView.layer.masksToBounds = true
        imgView.sd_setImage(with: URL(string: activity.activityCoverURL))
        return imgView
    }()
    
    lazy var statusImgView: UIImageView = {
        let imgView = UIImageView()
        imgView.contentMode = .scaleAspectFit
        imgView.image = UIImage(named: "activityOngoing")
        return imgView
    }()
    
    lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54")
        label.font = UIFont(name: PingFangSCSemibold, size: 18)
        label.text = activity.activityTitle
        return label
    }()
    
    lazy var typeImgView: UIImageView = {
        let imgView = UIImageView()
        imgView.contentMode = .scaleAspectFit
        imgView.image = UIImage(named: "typeImage")
        return imgView
    }()
    
    lazy var typeLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54", alpha: 0.4)
        label.font = UIFont(name: PingFangSCMedium, size: 14)
        switch activity.activityType {
        case "culture": label.text = "文娱活动"
        case "sports": label.text = "体育活动"
        case "education": label.text = "教育活动"
        default: break
        }
        return label
    }()
    
    lazy var activityWatchNumLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#2A4E84", alpha: 0.6)
        label.font = UIFont(name: PingFangSCRegular, size: 12)
        label.text = "\(activity.activityWatchNumber)人想看"
        return label
    }()
    
    lazy var wantToWatchButton: UIButton = {
        let button = UIButton()
        button.setImage(UIImage(named: "wantToWatch"), for: .normal)
        button.setImage(UIImage(named: "alreadyWantToWatch"), for: .disabled)
        button.imageView?.contentMode = .scaleAspectFit
        button.imageEdgeInsets = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        button.addTarget(self, action: #selector(wantToWatchButtonTapped), for: .touchUpInside)
        return button
    }()
    
    lazy var distanceStartTimeLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
        label.font = UIFont(name: PingFangSCMedium, size: 14)
        return label
    }()
    
    lazy var dayLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
        label.font = UIFont(name: PingFangSCMedium, size: 12)
        label.text = "天"
        return label
    }()
    
    lazy var hourLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
        label.font = UIFont(name: PingFangSCMedium, size: 12)
        label.text = "小时"
        return label
    }()
    
    lazy var minuteLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
        label.font = UIFont(name: PingFangSCMedium, size: 12)
        label.text = "分"
        return label
    }()
    
    lazy var secondLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
        label.font = UIFont(name: PingFangSCMedium, size: 12)
        label.text = "秒"
        return label
    }()
    
    lazy var dayNumLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54")
        label.textAlignment = .center
        label.font = UIFont(name: PingFangSCMedium, size: 16)
        return label
    }()
    
    lazy var hourNumLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54")
        label.textAlignment = .center
        label.font = UIFont(name: PingFangSCMedium, size: 16)
        return label
    }()
    
    lazy var minuteNumLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54")
        label.textAlignment = .center
        label.font = UIFont(name: PingFangSCMedium, size: 16)
        return label
    }()
    
    lazy var secondNumLabel: UILabel = {
        let label = UILabel()
        label.textColor = UIColor(hexString: "#112C54")
        label.textAlignment = .center
        label.font = UIFont(name: PingFangSCMedium, size: 16)
        return label
    }()
    
    // MARK: - 子视图布局
    func setPosition() {
        self.backButton.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 6)
            make.width.equalTo(30)
            make.height.equalTo(31)
        }
        
        self.coverImgView.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 52)
            make.width.equalTo(106)
            make.height.equalTo(106)
        }
        
        self.statusImgView.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(138)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 59)
            make.width.equalTo(42)
            make.height.equalTo(18)
        }
        
        self.titleLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(138)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 78)
            make.height.equalTo(25)
        }
        
        self.typeImgView.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(138)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 106)
            make.width.equalTo(20)
            make.height.equalTo(20)
        }
        
        self.typeLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(163)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 106)
            make.height.equalTo(20)
        }
        
        self.activityWatchNumLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(138)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 136)
            make.height.equalTo(17)
        }
        
        self.wantToWatchButton.snp.makeConstraints { make in
            make.trailing.equalToSuperview().offset(-16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 132)
            make.width.equalTo(70)
            make.height.equalTo(26)
        }
        
        self.distanceStartTimeLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(29)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 181)
            make.height.equalTo(20)
        }
        
        self.dayLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(156)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 183)
            make.width.equalTo(12)
            make.height.equalTo(17)
        }
        
        self.hourLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(211)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 183)
            make.width.equalTo(24)
            make.height.equalTo(17)
        }
        
        self.minuteLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(278)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 183)
            make.width.equalTo(12)
            make.height.equalTo(17)
        }
        
        self.secondLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(333)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 183)
            make.width.equalTo(12)
            make.height.equalTo(17)
        }
        
        self.dayNumLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(5)
            make.top.equalToSuperview().offset(4)
            make.width.equalTo(20)
            make.height.equalTo(22)
        }

        self.hourNumLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(5)
            make.top.equalToSuperview().offset(4)
            make.width.equalTo(20)
            make.height.equalTo(22)
        }

        self.minuteNumLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(5)
            make.top.equalToSuperview().offset(4)
            make.width.equalTo(20)
            make.height.equalTo(22)
        }

        self.secondNumLabel.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(5)
            make.top.equalToSuperview().offset(4)
            make.width.equalTo(20)
            make.height.equalTo(22)
        }
    }
    
    // MARK: - Button Target
    
    //返回上一级界面
    @objc func popController() {
        self.navigationController?.popViewController(animated: true)
    }
    
    //想看按钮被点击
    @objc func wantToWatchButtonTapped() {
        HttpManager.shared.magipoke_ufield_activity_action_watch(activity_id: activity.activityId).ry_JSON { response in
            switch response {
            case.success(let jsonData):
                let wantToWatchResponseData = StandardResponse(from: jsonData)
                if (wantToWatchResponseData.status == 10000) {
                    self.wantToWatchButton.isEnabled = false
                    RemindHUD.shared().showDefaultHUD(withText: "添加成功") {
                        TaskManager.shared.uploadTaskProgress(title: "参加一次活动", stampCount: 10, remindText: "已参加活动1次，获得50张邮票")
                    }
                    self.delegate?.updateModel(indexPathNum: self.numOfIndexPath, wantToWatch: true)
                } else {
                    RemindHUD.shared().showDefaultHUD(withText: wantToWatchResponseData.info)
                }
                break
            case .failure(let error):
                print(error)
                ActivityHUD.shared.showNetworkError()
                break
            }
        }
    }
    
    @objc func addToDoButtonTapped() {
        // 本地日程创建期间禁止重复点击；Schedule 会使用活动 ID 做幂等去重。
        detailView.addTodoButton.isEnabled = false
        IOSAppKt.createUfieldActivitySchedule(
            activityId: Int64(activity.activityId),
            title: activity.activityTitle,
            place: activity.activityPlace,
            startAtEpochSeconds: Int64(activity.activityStartAt)
        ) { [weak self] failureReason in
            guard let self else { return }
            if let failureReason {
                self.detailView.addTodoButton.isEnabled = true
                RemindHUD.shared().showDefaultHUD(withText: failureReason)
                return
            }

            RemindHUD.shared().showDefaultHUD(withText: "添加待办成功")
            self.delegate?.updateModel(indexPathNum: self.numOfIndexPath, addToDo: true)
            self.markActivityAdded()
        }
    }

    /// 回写活动中心自身的“已添加”标记；失败不撤销 Schedule 已完成的本地日程创建。
    private func markActivityAdded() {
        HttpManager.shared.magipoke_ufield_activity_addTodo(activity_id: activity.activityId).ry_JSON { response in
            if case .failure(let error) = response {
                print("Failed to mark ufield activity as added: \(error)")
            }
        }
    }
    
    // MARK: - 开始计时器以及倒计时更新
    func startCountdownTimer() {
        // 首次立即更新倒计时显示
        updateCountdownLabel()
        // 创建定时器，每秒更新一次倒计时显示
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            // 在闭包内使用 self?.updateCountdownLabel() 以避免强引用
            self?.updateCountdownLabel()
        }
    }
    
    @objc func updateCountdownLabel() {
        let currentTimeStamp = Date().timeIntervalSince1970
        var timeRemaining: Double
        if (TimeInterval(activity.activityStartAt) - currentTimeStamp >= 0) {
            timeRemaining = max(0, TimeInterval(activity.activityStartAt) - currentTimeStamp)
            distanceStartTimeLabel.text = "距离开始还有"
            let days = Int(timeRemaining) / 86400
            let hours = (Int(timeRemaining) % 86400) / 3600
            let minutes = (Int(timeRemaining) % 3600) / 60
            let seconds = Int(timeRemaining) % 60
            
            dayNumLabel.text = String(format: "%d", days)
            hourNumLabel.text = String(format: "%d", hours)
            minuteNumLabel.text = String(format: "%d", minutes)
            secondNumLabel.text = String(format: "%d", seconds)
        } else if(isActivityStarted == false) {
            timeRemaining = max(0, TimeInterval(activity.activityEndAt) - currentTimeStamp)
            setStatusView(statusText: "活动已开始", isEnded: false)
            isActivityStarted = true
        } else {
            timeRemaining = max(0, TimeInterval(activity.activityEndAt) - currentTimeStamp)
        }
        if timeRemaining <= 0 {
            // 倒计时结束，停止定时器
            countdownTimer?.invalidate()
            //在禁用倒计时后，它还会再执行一次，如果重复执行两次setStatusView会导致文字的textColor异常，故加上这个判断
            if countdownTimer == nil {
                setStatusView(statusText: "活动已结束", isEnded: true)
            }
            countdownTimer = nil
        }
    }
    
    // MARK: - 设置倒计时后面的灰色背景
    var backGroundView1: UIView!
    var backGroundView2: UIView!
    var backGroundView3: UIView!
    var backGroundView4: UIView!
    
    func setBackGroundView() {
        backGroundView1 = createTimeLabelBackGroundView()
        backGroundView2 = createTimeLabelBackGroundView()
        backGroundView3 = createTimeLabelBackGroundView()
        backGroundView4 = createTimeLabelBackGroundView()
        view.addSubview(backGroundView1)
        view.addSubview(backGroundView2)
        view.addSubview(backGroundView3)
        view.addSubview(backGroundView4)
        
        backGroundView1.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(123)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 176)
            make.width.equalTo(30)
            make.height.equalTo(30)
        }
        backGroundView2.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(178)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 176)
            make.width.equalTo(30)
            make.height.equalTo(30)
        }
        backGroundView3.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(245)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 176)
            make.width.equalTo(30)
            make.height.equalTo(30)
        }
        backGroundView4.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(300)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 176)
            make.width.equalTo(30)
            make.height.equalTo(30)
        }
    }
    
    func createTimeLabelBackGroundView() -> UIView {
        let customView = UIView()
        customView.backgroundColor = UIColor(hexString: "#ECEEF2")
        customView.layer.cornerRadius = 4
        return customView
    }
    
    // MARK: - 详情页的初始化和重载
    func addDetailView() {
        detailScrollView = UIScrollView()
        detailScrollView.backgroundColor = .white
        detailScrollView.layer.cornerRadius = 16
        detailScrollView.showsVerticalScrollIndicator = false
        detailScrollView.showsHorizontalScrollIndicator = false
        detailScrollView.contentSize = CGSizeMake(UIScreen.main.bounds.width, 571)
        detailView = ActivityDetailView(frame: CGRectMake(0, 0, UIScreen.main.bounds.width, 571))
        view.addSubview(detailScrollView)
        detailScrollView.addSubview(detailView)
        detailScrollView.snp.makeConstraints { make in
            make.leading.equalToSuperview()
            make.trailing.equalToSuperview()
            make.top.equalToSuperview().offset(271)
            make.bottom.equalToSuperview()
        }
        detailView.commonInit()
        detailView.organizerLabel.text = activity.activityOrganizer
        detailView.creatorLabel.text = activity.activityCreator
        detailView.registrationLabel.text = activity.activityRegistrationType
        detailView.placeLabel.text = activity.activityPlace
        detailView.startTimeLabel.text = formatTimestamp(timestamp: activity.activityStartAt)
        detailView.endTimeLabel.text = formatTimestamp(timestamp: activity.activityEndAt)
        detailView.detailLabel.text = activity.activityDetail
        detailView.addTodoButton.addTarget(self, action: #selector(addToDoButtonTapped), for: .touchUpInside)
        if activity.isAdded ?? false {
            detailView.addTodoButton.isEnabled = false
        }
    }
    
    func reinitializeDetailView() {
        detailView.removeFromSuperview()
        detailScrollView.removeFromSuperview()
        detailView = nil
        detailScrollView = nil
        addDetailView()
    }
    
    // MARK: - 倒计时结束修改子视图样式(isEnded为true修改detialview中部分字体颜色）
    func setStatusView(statusText: String, isEnded:Bool) {
        statusLabel?.removeFromSuperview()
        statusLabel = nil
        backGroundView1.removeFromSuperview()
        backGroundView2.removeFromSuperview()
        backGroundView3.removeFromSuperview()
        backGroundView4.removeFromSuperview()
        distanceStartTimeLabel.removeFromSuperview()
        dayLabel.removeFromSuperview()
        hourLabel.removeFromSuperview()
        minuteLabel.removeFromSuperview()
        secondLabel.removeFromSuperview()
        dayNumLabel.removeFromSuperview()
        hourNumLabel.removeFromSuperview()
        minuteNumLabel.removeFromSuperview()
        secondNumLabel.removeFromSuperview()
        statusLabel = UILabel()
        statusLabel?.textColor = UIColor(hexString: "#15315B", alpha: 0.8)
        statusLabel?.font = UIFont(name: PingFangSCMedium, size: 16)
        statusLabel?.textAlignment = .center
        view.addSubview(statusLabel!)
        statusLabel?.text = statusText
        statusLabel?.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 179)
            make.width.equalTo(120)
            make.height.equalTo(22)
        }
        if isEnded {
            for label in detailView.informationViews {
                label.textColor = UIColor(hexString: "#15315B", alpha: 0.3)
            }
            for label in detailView.informationLabels {
                label.textColor = UIColor(hexString: "#15315B", alpha: 0.6)
            }
            detailView.placeLabel.textColor = UIColor(hexString: "#4A44E4", alpha: 0.6)
            detailView.placeImg.alpha = 0.4
            statusImgView.image = UIImage(named: "activityEnded")
            detailView.detailLabel . textColor = UIColor(hexString: "#15315B", alpha: 0.3)
            detailView.informationView.textColor = UIColor(hexString: "#112C54", alpha: 0.6)
            detailView.detailView.textColor = UIColor(hexString: "#112C54", alpha: 0.6)
            wantToWatchButton.removeFromSuperview()
        }
    }
}
