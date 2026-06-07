//
//  BusyDetailVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/16.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class BusyDetailVC: UIViewController {
    
    /// 是否全员空闲
    private var isAllLeisure = Bool()
    /// 忙碌人员姓名数组
    private var nameAry: [String] = []
    /// 所有人员学号数组
    private var sumIDAry: [String] = []
    /// 忙碌人员学号数组
    private var busyIDAry: [String] = []
    /// 时间段
    private var timePeriod: String = ""
    /// 时间字典
    private var timeDic: [String: Int] = [:]
    
    // MARK: - Life Cycle
    
    init(studentAry: [StudentResultItem], sumIDAry: [String], timePeriod: String, timeDic: [String: Int]) {
        super.init(nibName: nil, bundle: nil)
        if studentAry.isEmpty {
            self.isAllLeisure = true
        } else {
            self.isAllLeisure = false
        }
        for student in studentAry {
            nameAry.append(student.name)
            busyIDAry.append(student.studentID)
        }
        self.sumIDAry = sumIDAry
        self.timePeriod = timePeriod
        self.timeDic = timeDic
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        if !isAllLeisure {
            showBusyInfo()
        }
        // 只有两节课才添加安排行程按钮，只有一节课(即period为0)时不添加安排行程按钮
        if timeDic["period"] == 1 {
            containerView.addSubview(button)
        }
        containerView.addSubview(titleLab)
        containerView.addSubview(numberLab)
        containerView.addSubview(timeLab)
        view.addSubview(containerView)
        
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        view.addGestureRecognizer(tapGesture)
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        UIView.animate(withDuration: 0.1) {
            self.view.backgroundColor = UIColor(hexString: "000000", alpha: 0.47)
        }
    }
        
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        view.backgroundColor = .clear
    }
    
    // MARK: - Method
    
    @objc private func clickButton() {
        let vc = AddArrangeTitleVC(sumIDAry, busyIDAry, timeDic)
        self.navigationController?.pushViewController(vc, animated: true)
    }
    
    private func showBusyInfo() {
        let scrollView = UIScrollView(frame: CGRect(x: 16, y: lineView.bottom + 14, width: SCREEN_WIDTH - 16 * 2, height: 70))
        scrollView.isPagingEnabled = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.delegate = self
        
        let space = 8.0
        var nowWidth = 0.0
        var nowHeight = 0.0
        var pageNum = 1
        for i in 0..<nameAry.count {
            let width = Double((nameAry[i].count - 1) * 12 + 42)

            // 如果同一行没位置了
            if nowWidth + width + space > scrollView.width {
                // 如果同一页还有位置
                if nowHeight + space + 31 < scrollView.height {
                    nowHeight += (31 + space)
                    nowWidth = 0.0
                } else {
                    nowWidth = 0.0
                    nowHeight = 0.0
                    pageNum += 1
                }
            }
            
            let label = UILabel(frame: CGRect(x: scrollView.width * Double((pageNum - 1)) + nowWidth, y: nowHeight, width: width, height: 31))
            label.text = nameAry[i]
            label.textAlignment = .center
            label.font = .systemFont(ofSize: 12)
            label.layer.cornerRadius = 16
            label.clipsToBounds = true
            label.layer.borderWidth = 1.2
            label.layer.borderColor = UIColor.weDateSearchBackground.cgColor
            label.textColor = .weDateLightMutedText
            scrollView.addSubview(label)
            
            nowWidth += (width + space)
            scrollView.contentSize.width = CGFloat(pageNum) * scrollView.width
        }
        
        containerView.addSubview(lineView)
        containerView.addSubview(scrollView)
        pageControl.frame = CGRect(x: 0, y: scrollView.bottom + 18, width: SCREEN_WIDTH, height: 7)
        pageControl.numberOfPages = pageNum
        if pageNum > 1 {
            containerView.addSubview(pageControl)
        }
    }
    
    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let location = gesture.location(in: view)
        if !containerView.frame.contains(location) {
            self.dismiss(animated: true, completion: nil)
        }
    }
    
    // MARK: - Lazy
    
    /// 此VC所有UI的容器视图
    private lazy var containerView: UIView = {
        let height = isAllLeisure ? 198.0 : 247.0
        let containerView = UIView(frame: CGRect(x: 0, y: SCREEN_HEIGHT - height, width: SCREEN_WIDTH, height: height))
        let maskPath = UIBezierPath(roundedRect: containerView.bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 12, height: 12))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        containerView.layer.mask = maskLayer
        containerView.backgroundColor = .weDatePanelBackground
        return containerView
    }()
    /// "忙碌x人"文本
    private lazy var titleLab: UILabel = {
        let titleLab = UILabel(frame: CGRect(x: 16, y: 20, width: 110, height: 21))
        titleLab.textColor = .weDatePrimaryText
        titleLab.font = .boldSystemFont(ofSize: 18)
        titleLab.text = "忙碌" + String(nameAry.count) + "人"
        return titleLab
    }()
    /// 安排行程按钮
    private lazy var button: UIButton = {
        let button = UIButton(frame: CGRect(x: SCREEN_WIDTH - 16 - 78, y: titleLab.top, width: 78, height: 28))
        button.setTitle("安排行程", for: .normal)
        button.titleLabel?.textAlignment = .center
        button.layer.cornerRadius = 15
        button.clipsToBounds = true
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 12)
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
    /// '人数：共计x人'文本
    private lazy var numberLab: UILabel = {
        let numberLab = UILabel(frame: CGRect(x: titleLab.left, y: titleLab.bottom + 16, width: 148, height: 13))
        let attributedString = NSMutableAttributedString(string: "人数：", attributes: [
            .foregroundColor: UIColor.weDateLightMutedText,
            .font: UIFont.systemFont(ofSize: 14)
        ])
        let string = "共计 " + String(sumIDAry.count) + " 人"
        attributedString.append(NSAttributedString(string: string, attributes: [
            .foregroundColor: UIColor.weDateMutedText,
            .font: UIFont.systemFont(ofSize: 14)
        ]))
        numberLab.attributedText = attributedString
        return numberLab
    }()
    /// '时间：xxx'文本
    private lazy var timeLab: UILabel = {
        let timeLab = UILabel(frame: CGRect(x: titleLab.left, y: numberLab.bottom + 8, width: 250, height: 15))
        let attributedString = NSMutableAttributedString(string: "时间：", attributes: [
            .foregroundColor: UIColor.weDateLightMutedText,
            .font: UIFont.systemFont(ofSize: 14)
        ])
        attributedString.append(NSAttributedString(string: timePeriod, attributes: [
            .foregroundColor: UIColor.weDateMutedText,
            .font: UIFont.systemFont(ofSize: 14)
        ]))
        timeLab.attributedText = attributedString
        return timeLab
    }()
    /// 分割线
    private lazy var lineView: UIView = {
        let lineView = UIView(frame: CGRect(x: 0, y: timeLab.bottom + 14, width: SCREEN_WIDTH, height: 1))
        lineView.backgroundColor = .weDateThinSeparator
        return lineView
    }()
    /// 分页控制
    private lazy var pageControl: UIPageControl = {
        let pageControl = UIPageControl()
        pageControl.isEnabled = false
        pageControl.currentPageIndicatorTintColor = .weDateIndicator
        pageControl.pageIndicatorTintColor = .weDateDisabledButton
        return pageControl
    }()
}

// MARK: - CreateGroupAlertVCDelegate

extension BusyDetailVC: UIScrollViewDelegate {
    
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        let pageIndex = Int(scrollView.contentOffset.x / scrollView.frame.width)
        pageControl.currentPage = pageIndex
    }
}
