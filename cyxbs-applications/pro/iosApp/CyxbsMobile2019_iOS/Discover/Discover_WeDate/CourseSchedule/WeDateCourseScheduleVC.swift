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
        view.backgroundColor = .white
        view.addSubview(titleLab)
        
        if(stuNumAry.count != 0){
            CourseScheduleModel.requestWithStuNum(stuNumAry[0]) { courseScheduleModel in
                self.nowWeek = courseScheduleModel.nowWeek
                self.fact = ScheduleFact(stuNumAry: self.stuNumAry, dateVersion: courseScheduleModel.dateVersion, nowWeek: self.nowWeek)
                self.fact?.delegate = self
                // 添加回到本周按钮时需确保fact有值，否则点击会崩溃
                self.view.addSubview(self.button)
                // 添加collectionView时需确保fact有值，否则会崩溃
                self.view.addSubview(self.collectionView)
            } failure: { error in
                print(error)
            }
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
    
    @objc private func clickButton() {
        collectionView.setContentOffset(CGPoint(x: Int(SCREEN_WIDTH) * nowWeek, y: 0), animated: true)
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
        titleLab.textColor = UIColor(hexString: "#112C54", alpha: 1)
        titleLab.text = "整学期"
        return titleLab
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
            UIColor(hexString: "#4741E0", alpha: 1).cgColor,
            UIColor(hexString: "#5D5EF7", alpha: 1).cgColor
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
