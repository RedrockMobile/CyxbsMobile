//
//  WeDateCourseScheduleVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/17.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class WeDateCourseScheduleVC: UIViewController {
    
    private var fact: ScheduleFact?
    /// 现在的周数
    private var nowWeek: Int = 0
    /// 学号数组
    var stuNumAry: [String] = []
    
    private var shouldWaitForInitialSchedules: Bool {
        stuNumAry.count > 5
    }
    
    // MARK: - Life Cycle
    
    init(stuNumAry: [String]) {
        super.init(nibName: nil, bundle: nil)
        self.stuNumAry = stuNumAry
    }
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func loadView() {
        super.loadView()
        view.frame.size.height -= statusBarHeight
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .weDatePageBackground
        view.addSubview(titleLab)
        #if DEBUG
        print("[WeDateCourseSchedule] schedule page received stuNumCount=\(stuNumAry.count)")
        #endif
        
        if shouldWaitForInitialSchedules {
            showLoadingView()
        }
        
        if !stuNumAry.isEmpty {
            CourseScheduleModel.requestWithStuNum(stuNumAry[0]) { [weak self] courseScheduleModel in
                guard let self = self else { return }
                self.nowWeek = courseScheduleModel.nowWeek
                WeekMaping.cacheCourseSchedule(courseScheduleModel, for: self.stuNumAry[0])
                self.fact = ScheduleFact(stuNumAry: self.stuNumAry, dateVersion: courseScheduleModel.dateVersion, nowWeek: self.nowWeek)
                self.fact?.delegate = self
                
                if self.shouldWaitForInitialSchedules {
                    _ = self.collectionView
                    self.fact?.loadInitialSchedules { [weak self] in
                        self?.showScheduleContent()
                    }
                } else {
                    self.showScheduleContent()
                    self.fact?.loadInitialSchedules {}
                }
            } failure: { [weak self] error in
                print(error)
                self?.hideLoadingView()
            }
        } else {
            hideLoadingView()
        }
    }
    
    // MARK: - Method
    
    /// 将数字转换为汉字
    static func numberToChinese(_ number: Int) -> String {
        let units = ["", "十", "百", "千", "万"]
        let digits = ["", "一", "二", "三", "四", "五", "六", "七", "八", "九"]
        var result = ""
        var num = number
        if num == 0 {
            return digits[0]
        }
        if num >= 10 && num < 20 {
            result = "十" + digits[num % 10]
            return result
        }
        var unitIndex = 0
        while num > 0 {
            let digit = num % 10
            if digit != 0 {
                result = digits[digit] + units[unitIndex] + result
            } else {
                if result != "" {
                    result = digits[digit] + result
                }
            }
            num /= 10
            unitIndex += 1
        }
        return result
    }
    
    private func showScheduleContent() {
        hideLoadingView()
        if button.superview == nil {
            view.addSubview(button)
        }
        if collectionView.superview == nil {
            view.addSubview(collectionView)
        }
    }
    
    private func showLoadingView() {
        if loadingView.superview == nil {
            view.addSubview(loadingView)
        }
        loadingIndicator.startAnimating()
    }
    
    private func hideLoadingView() {
        loadingIndicator.stopAnimating()
        loadingView.removeFromSuperview()
    }
    
    @objc private func clickButton() {
        let maxPage = max(collectionView.numberOfSections - 1, 0)
        let targetWeek = min(max(nowWeek, 0), maxPage)
        collectionView.setContentOffset(CGPoint(x: collectionView.bounds.width * CGFloat(targetWeek), y: 0), animated: true)
    }
    
    // MARK: - Lazy
    
    private lazy var collectionView: UICollectionView = {
        let collectionView = fact!.createCollectionView()
        let y: CGFloat = 64
        collectionView.frame = CGRect(x: 0, y: y, width: view.bounds.width, height: view.bounds.height - y)
        collectionView.contentInset.bottom = tabBarController?.tabBar.bounds.height ?? 0
        collectionView.backgroundColor = .clear
        return collectionView
    }()
    
    private lazy var titleLab: UILabel = {
        let titleLab = UILabel(frame: CGRect(x: 16, y: 21, width: 90, height: 31))
        titleLab.font = .systemFont(ofSize: 22, weight: .black)
        titleLab.textColor = .weDateTitleText
        titleLab.text = "整学期"
        return titleLab
    }()
    
    private lazy var loadingView: UIView = {
        let y: CGFloat = 64
        let loadingView = UIView(frame: CGRect(x: 0, y: y, width: view.bounds.width, height: view.bounds.height - y))
        loadingView.backgroundColor = .weDatePageBackground
        loadingView.addSubview(loadingIndicator)
        loadingView.addSubview(loadingLabel)
        loadingIndicator.center = CGPoint(x: loadingView.bounds.midX, y: loadingView.bounds.midY - 18)
        loadingLabel.frame = CGRect(x: 24, y: loadingIndicator.frame.maxY + 12, width: loadingView.bounds.width - 48, height: 22)
        return loadingView
    }()
    
    private lazy var loadingIndicator: UIActivityIndicatorView = {
        let indicator = UIActivityIndicatorView(style: .medium)
        indicator.color = .weDateAccent
        return indicator
    }()
    
    private lazy var loadingLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = .weDateSecondaryText
        label.textAlignment = .center
        label.text = "课表加载中..."
        return label
    }()
    
    private lazy var button: UIButton = {
        let button = UIButton(frame: CGRect(x: SCREEN_WIDTH - 84 - 16, y: titleLab.top, width: 84, height: 32))
        button.setTitle("回到本周", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 13)
        button.layer.cornerRadius = 17
        button.clipsToBounds = true
        button.addTarget(self, action: #selector(clickButton), for: .touchUpInside)
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = button.bounds
        button.layer.insertSublayer(gradientLayer, at: 0)
        return button
    }()
}

// MARK: - ScheduleFactDelegate

extension WeDateCourseScheduleVC: ScheduleFactDelegate {
    func updateCpllectionViewPageNum(_ num: Int) {
        if num == 0 {
            titleLab.text = "整学期"
        } else if num <= 10 {
            titleLab.text = "第" + WeDateCourseScheduleVC.numberToChinese(num) + "周"
        } else {
            titleLab.text = WeDateCourseScheduleVC.numberToChinese(num) + "周"
        }
        
        if num == nowWeek {
            button.isHidden = true
        } else {
            button.isHidden = false
        }
    }
    
    func didSelectItemWith(_ studentAry: [StudentResultItem], _ timePeriod: String, _ timeDic: [String : Int]) {
        let vc = BusyDetailVC(studentAry: studentAry, sumIDAry: stuNumAry, timePeriod: timePeriod, timeDic: timeDic)
        vc.modalPresentationStyle = .custom
        let nav = UINavigationController(rootViewController: vc)
        nav.modalPresentationStyle = .custom
        present(nav, animated: true, completion: nil)
    }
}
