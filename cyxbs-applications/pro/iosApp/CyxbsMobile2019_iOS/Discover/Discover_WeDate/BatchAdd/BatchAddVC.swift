//
//  BatchAddVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/3.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class BatchAddVC: UIViewController {
    
    /// 信息有误文字
    private var alertStr: String = "" {
        willSet {
            alertLabel.text = newValue
        }
    }
    /// 学号数组
    private var studentIDAry: [String] = []
    
    private var scheduleVC: WeDateCourseScheduleVC?

    // MARK: - Life Cycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(returnBtn)
        view.addSubview(label)
        view.addSubview(dividingLine)
        view.addSubview(inquireBtn)
        view.addSubview(textViewBackView)
        textViewBackView.addSubview(textView)
        alertView.addSubview(alertLabel)
        alertView.addSubview(alertBtn)
        textView.addSubview(firstLineLab)
        textView.addSubview(secondLineLab)
        textView.addSubview(thirdLineLab)
        textView.addSubview(fourthLineLab)
        textView.addSubview(fifthLineLab)
        textView.addSubview(sixthLineLab)
        textView.addSubview(seventhLineLab)
        textView.addSubview(eighthLineLab)
        view.backgroundColor = .weDatePageBackground
    }
    
    // MARK: - Method
    
    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    @objc private func clickDoneBtn() {
        view.endEditing(true)
    }
    
    @objc private func clickInquireBtn() {
        BatchAddModel.checkDataWithContent(textView.text) { batchAddModel in
            var isFormCorrect: Bool = true
            // 判断格式是否合格
            if self.textView.text.isEmpty {
                isFormCorrect = false
            } else {
                let string = self.textView.text.replacingOccurrences(of: "\n", with: "")
                let numPattern = "^[0-9]+$"
                let chinesePattern = "^[\\u4e00-\\u9fa5]+([·]?[\\u4e00-\\u9fa5]+)+$"
                if let numRegex = try? NSRegularExpression(pattern: numPattern),
                   let chineseRegex = try? NSRegularExpression(pattern: chinesePattern, options: .caseInsensitive) {
                    let range = NSRange(location: 0, length: string.utf16.count)
                    if numRegex.firstMatch(in: string, options: [], range: range) == nil && chineseRegex.firstMatch(in: string, options: [], range: range) == nil {
                        isFormCorrect = false
                    }
                }
            }
            
            // 格式错
            if !isFormCorrect {
                let vc = SearchAlertVC(alertStr: "格式有误，请重新输入")
                vc.modalPresentationStyle = .overFullScreen
                self.navigationController?.present(vc, animated: false, completion: nil)
            } else {
                // 有错误数据
                if batchAddModel.isWrong {
                    self.view.addSubview(self.overlayView)
                    let count = batchAddModel.errorName.count
                    var name: String = ""
                    let firstName = batchAddModel.errorName.first!
                    if firstName.count > 5 {
                        name = firstName.prefix(5) + ".."
                    } else {
                        name = firstName
                    }
                    self.alertStr = "\"\(name)\"等\(count)人信息有误\n请重新输入"
                    self.view.addSubview(self.alertView)
                } else {
                    // 有正常数据
                    if !batchAddModel.normalStudent.isEmpty {
                        for student in batchAddModel.normalStudent {
                            self.studentIDAry.append(student.studentID)
                        }
                    }
                    // 有重复数据
                    if !batchAddModel.repeatStudent.isEmpty {
                        let vc = RepeatNameVC(studentAry: batchAddModel.repeatStudent)
                        vc.delegate = self
                        vc.modalPresentationStyle = .custom
                        self.navigationController?.present(vc, animated: true, completion: nil)
                    } else {
                        self.showCourseSchedule()
                    }
                }
            }
            
        } failure: { error in
            print(error)
        }
    }
    
    @objc private func clickConfirmBtn() {
        alertView.removeFromSuperview()
        overlayView.removeFromSuperview()
    }
    
    private func showCourseSchedule() {
        let vc = WeDateCourseScheduleVC(stuNumAry: self.studentIDAry)
        self.scheduleVC = vc
        self.navigationController?.present(vc, animated: true, completion: {
            if !UserDefaults.standard.bool(forKey: "noMoreReminders") {
                let alertVC = CreateGroupAlertVC()
                alertVC.modalPresentationStyle = .overFullScreen
                alertVC.delegate = self
                vc.present(alertVC, animated: false, completion: nil)
            }
        })
        studentIDAry.removeAll()
    }
    
    // MARK: - Lazy
    
    /// 返回按钮
    private lazy var returnBtn: UIButton = {
        let returnBtn = MXBackButton(frame: CGRect(x: 16, y: statusBarHeight + 16, width: 9, height: 17), isAutoHotspotExpand: true)
        returnBtn.setImage(UIImage(named: "空教室返回"), for: .normal)
        returnBtn.addTarget(self, action: #selector(clickReturnBtn), for: .touchUpInside)
        return returnBtn
    }()
    /// '批量添加'文本
    private lazy var label: UILabel = {
        let label = UILabel(frame: CGRect(x: returnBtn.left + 23, y: returnBtn.top - 2, width: 96, height: 23))
        label.text = "批量添加"
        label.font = .boldSystemFont(ofSize: 21)
        label.textColor = .weDatePrimaryText
        return label
    }()
    /// 分割线
    private lazy var dividingLine: UIView = {
        let dividingLine = UIView(frame: CGRect(x: 0, y: label.bottom + 7, width: SCREEN_WIDTH, height: 1))
        dividingLine.backgroundColor = .weDateSeparator
        return dividingLine
    }()
    /// 输入框所在视图
    private lazy var textViewBackView: UIView = {
        let textViewBackView = UIView(frame: CGRect(x: 17, y: dividingLine.bottom + 15, width: SCREEN_WIDTH - 17 - 15, height: 470))
        textViewBackView.layer.cornerRadius = 12
        textViewBackView.clipsToBounds = true
        textViewBackView.layer.borderWidth = 2
        textViewBackView.layer.borderColor = UIColor.weDateInputBorder.cgColor
        textViewBackView.backgroundColor = .weDatePanelBackground
        return textViewBackView
    }()
    /// 输入框
    private lazy var textView: UITextView = {
        let textView = UITextView(frame: CGRect(x: 13, y: 13, width: textViewBackView.width - 13 * 2, height: textViewBackView.height - 13))
        textView.autocorrectionType = .no
        textView.autocapitalizationType = .none
        textView.backgroundColor = .weDatePanelBackground
        textView.textColor = .weDateDeepText
        textView.delegate = self
        return textView
    }()
    /// 查询按钮
    private lazy var inquireBtn: UIButton = {
        let inquireBtn = UIButton(frame: CGRect(x: (SCREEN_WIDTH - 120) / 2, y: view.bottom - 57 - 42, width: 120, height: 42))
        inquireBtn.layer.cornerRadius = 22
        inquireBtn.clipsToBounds = true
        inquireBtn.setTitle("查询", for: .normal)
        inquireBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        inquireBtn.addTarget(self, action: #selector(clickInquireBtn), for: .touchUpInside)
        // 为按钮添加线性渐变
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = inquireBtn.bounds
        inquireBtn.layer.insertSublayer(gradientLayer, at: 0)
        return inquireBtn
    }()
    /// 信息有误弹窗
    private lazy var alertView: UIView = {
        let alertView = UIView(frame: CGRect(x: (SCREEN_WIDTH - 283) / 2, y: (SCREEN_HEIGHT - 170 ) / 2 - 50, width: 283, height: 170))
        alertView.layer.cornerRadius = 12.0
        alertView.clipsToBounds = true
        alertView.backgroundColor = .weDatePanelBackground
        return alertView
    }()
    /// 信息有误文本
    private lazy var alertLabel: UILabel = {
        let alertLabel = UILabel(frame: CGRect(x: 0, y: 31, width: alertView.width, height: 36))
        alertLabel.numberOfLines = 0
        alertLabel.textAlignment = .center
        alertLabel.font = .systemFont(ofSize: 15)
        alertLabel.textColor = .weDatePrimaryText
        return alertLabel
    }()
    /// 信息有误按钮
    private lazy var alertBtn: UIButton = {
        let alertBtn = UIButton(frame: CGRect(x: 95.5, y: 103, width: 92, height: 36))
        alertBtn.layer.cornerRadius = 18
        alertBtn.clipsToBounds = true
        alertBtn.setTitle("确定", for: .normal)
        alertBtn.titleLabel?.font = .boldSystemFont(ofSize: 15)
        alertBtn.addTarget(self, action: #selector(clickConfirmBtn), for: .touchUpInside)
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = alertBtn.bounds
        alertBtn.layer.insertSublayer(gradientLayer, at: 0)
        return alertBtn
    }()
    /// 黑色半透明蒙板
    private lazy var overlayView: UIView = {
        let overlayView = UIView(frame: view.bounds)
        overlayView.backgroundColor = UIColor.black.withAlphaComponent(0.47)
        overlayView.isUserInteractionEnabled = false
        return overlayView
    }()
    /// 输入框水印
    private lazy var firstLineLab: UILabel = {
        let firstLineLab = UILabel(frame: CGRect(x: 0, y: 0, width: 115, height: 20))
        firstLineLab.text = "样例输入1：卷卷"
        firstLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        firstLineLab.textColor = .weDateCorrectExampleText
        return firstLineLab
    }()
    private lazy var secondLineLab: UILabel = {
        let secondLineLab = UILabel(frame: CGRect(x:82, y: firstLineLab.bottom, width: 30, height: 20))
        secondLineLab.text = "卷娘"
        secondLineLab.font = .systemFont(ofSize: 14)
        secondLineLab.textColor = .weDateCorrectExampleText
        return secondLineLab
    }()
    private lazy var thirdLineLab: UILabel = {
        let thirdLineLab = UILabel(frame: CGRect(x: 0, y: secondLineLab.bottom + 6, width: 170, height: 20))
        thirdLineLab.text = "样例输入2：2022213333"
        thirdLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        thirdLineLab.textColor = .weDateCorrectExampleText
        return thirdLineLab
    }()
    private lazy var fourthLineLab: UILabel = {
        let fourthLineLab = UILabel(frame: CGRect(x: 82, y: thirdLineLab.bottom, width: 90, height: 20))
        fourthLineLab.text = "2011118888"
        fourthLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        fourthLineLab.textColor = .weDateCorrectExampleText
        return fourthLineLab
    }()
    private lazy var fifthLineLab: UILabel = {
        let fifthLineLab = UILabel(frame: CGRect(x: 0, y: fourthLineLab.bottom + 14, width: 159, height: 20))
        fifthLineLab.text = "错误输入1：卷卷，卷娘"
        fifthLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        fifthLineLab.textColor = .weDateWrongExampleText
        return fifthLineLab
    }()
    private lazy var sixthLineLab: UILabel = {
        let sixthLineLab = UILabel(frame: CGRect(x: 82, y: fifthLineLab.bottom, width: 75, height: 20))
        sixthLineLab.text = "卷卷；卷娘"
        sixthLineLab.font = .systemFont(ofSize: 14)
        sixthLineLab.textColor = .weDateWrongExampleText
        return sixthLineLab
    }()
    private lazy var seventhLineLab: UILabel = {
        let seventhLineLab = UILabel(frame: CGRect(x: 0, y: sixthLineLab.bottom + 6, width: 159, height: 20))
        seventhLineLab.text = "错误输入2：卷卷"
        seventhLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        seventhLineLab.textColor = .weDateWrongExampleText
        return seventhLineLab
    }()
    private lazy var eighthLineLab: UILabel = {
        let eighthLineLab = UILabel(frame: CGRect(x: 82, y: seventhLineLab.bottom, width: 90, height: 20))
        eighthLineLab.text = "2022222222"
        eighthLineLab.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular)
        eighthLineLab.textColor = .weDateWrongExampleText
        return eighthLineLab
    }()
}

// MARK: - UITextViewDelegate

extension BatchAddVC: UITextViewDelegate {
    
    func textViewDidBeginEditing(_ textView: UITextView) {
        firstLineLab.isHidden = true
        secondLineLab.isHidden = true
        thirdLineLab.isHidden = true
        fourthLineLab.isHidden = true
        fifthLineLab.isHidden = true
        sixthLineLab.isHidden = true
        seventhLineLab.isHidden = true
        eighthLineLab.isHidden = true
    }
    
    func textViewDidEndEditing(_ textView: UITextView) {
        firstLineLab.isHidden = !textView.text.isEmpty
        secondLineLab.isHidden = !textView.text.isEmpty
        thirdLineLab.isHidden = !textView.text.isEmpty
        fourthLineLab.isHidden = !textView.text.isEmpty
        fifthLineLab.isHidden = !textView.text.isEmpty
        sixthLineLab.isHidden = !textView.text.isEmpty
        seventhLineLab.isHidden = !textView.text.isEmpty
        eighthLineLab.isHidden = !textView.text.isEmpty
    }
    
    func textViewDidChange(_ textView: UITextView) {
        if let text = textView.text,
           textView.markedTextRange == nil {
            let attributedString = NSMutableAttributedString(string: "")
            let paragraphStyle = NSMutableParagraphStyle()
            paragraphStyle.lineSpacing = 8
            let attributes: [NSAttributedString.Key: Any] = [
                .paragraphStyle: paragraphStyle,
                .font: UIFont.systemFont(ofSize: 14),
                .foregroundColor: UIColor.weDateDeepText
            ]
            let attributedUserInput = NSAttributedString(string: text, attributes: attributes)
            attributedString.append(attributedUserInput)
            textView.attributedText = attributedString
        }
    }
}

// MARK: - RepeatNameVCDelegate

extension BatchAddVC: RepeatNameVCDelegate {
    
    func addRepeatStudent(_ array: [StudentResultItem]) {
        for student in array {
            studentIDAry.append(student.studentID)
        }
        showCourseSchedule()
    }
}

// MARK: - CreateGroupAlertVCDelegate

extension BatchAddVC: CreateGroupAlertVCDelegate {
    
    func createGroupAlertVCDidDismiss() {
        let vc = CreateGroupVC(studentIDAry: scheduleVC!.stuNumAry)
        vc.modalPresentationStyle = .custom
        scheduleVC?.present(vc, animated: true, completion: nil)
    }
}
