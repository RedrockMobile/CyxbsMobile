//
//  ForgotViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/10/13.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import ProgressHUD

class ForgotViewController: BaseTextFiledViewController {
    
    enum RetrieveWay {
    case email
    case question
    }
    
    let sno: String?
    let bidingType: EmailBidingViewController.BidingType
    
    var email: String?
    var question: ConfidentialityQuestionModel?
    var code: String?
    var retrieveWay: RetrieveWay

    init(sno: String?, bidingType: EmailBidingViewController.BidingType) {
        self.sno = sno
        self.bidingType = bidingType
        retrieveWay = bidingType.email ? .email : .question
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        contentView.addSubview(backBtn)
        contentView.addSubview(changeBtn)
        contentView.addSubview(titleLab)
        contentView.addSubview(contentLab)
        contentView.addSubview(questionLab)
        contentView.addSubview(codeTextField)
        contentView.addSubview(sureBtn)
        contentView.addSubview(newPwdTextField)
        
        commitInit()
    }
    
    // MARK: lazy
    
    lazy var backBtn: UIButton = {
        let btn = UIButton(frame: CGRect(x: marginSpaceForHorizontal, y: Constants.statusBarHeight + marginSpaceForHorizontal, width: 30, height: 30))
        btn.backgroundColor = .gray.withAlphaComponent(0.5)
        btn.layer.cornerRadius = 10
        btn.clipsToBounds = true
        let img = UIImage(named: "direction_left")?
            .tint(.white, blendMode: .destinationIn)
            .scaled(toHeight: 20)
        btn.setImage(img, for: .normal)
        btn.addTarget(self, action: #selector(touchUpInside(backBtn:)), for: .touchUpInside)
        return btn
    }()
    
    lazy var questionLab: UILabel = {
        let lab = UILabel()
        lab.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin]
        lab.text = " "
        lab.textColor = UIColor.ry(light: "#6C809B", dark: "#909090")
        lab.font = .systemFont(ofSize: 18, weight: .semibold)
        return lab
    }()
    
    lazy var codeTextField: UITextField = {
        let textField = createForgotTypeTextFiled(placeholder: "请输入验证码", leftImgName: nil, countDown: true)
        textField.keyboardType = .default
        return textField
    }()
    
    lazy var newPwdTextField: UITextField = {
        let textField = createForgotTypeTextFiled(placeholder: "输入新的密码", leftImgName: nil, countDown: false)
        textField.keyboardType = .asciiCapable
        textField.isHidden = true
        return textField
    }()
    
    lazy var sureBtn: UIButton = {
        let btn = createSureButton(size: CGSize(width: 280, height: 52), title: "验 证", touchUpInside: #selector(clickSureBtn(btn:)))
        btn.isEnabled = false
        return btn
    }()
    
    lazy var changeBtn: UIButton = {
        let btn = createDetailBtn(title: "通过问题验证", touchUpInsideAction: #selector(touchupInside(changeBtn:)))
        return btn
    }()
    
    // MARK: override
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        checkSureBtn()
    }
    
    override func shouldCountDown() -> Bool {
        sendCode()
        return true
    }
    
    override var countDownPromptText: String {
        "邮箱获取"
    }
}

// MARK: update

extension ForgotViewController {
    
    func commitInit() {
        request()
        setupData()
        updateFrame()
    }
    
    func setupData() {
        let muStr = NSMutableAttributedString(string: "找回密码", attributes: [
            .font: UIFont.systemFont(ofSize: 34, weight: .semibold),
            .foregroundColor: UIColor.ry(light: "#15315B", dark: "#F0F0F0")
        ])
        muStr.append(NSAttributedString(string: "\(sno ?? "")", attributes: [
            .font: UIFont.systemFont(ofSize: 24, weight: .semibold),
            .foregroundColor: UIColor.ry(light: "#15315B", dark: "#F0F0F0")
        ]))
        titleLab.attributedText = muStr
        titleLab.sizeToFit()
        
        switch retrieveWay {
        case .email:
            contentLab.text = "通过邮箱找回"
            codeTextField.placeholder = "请输入验证码"
            codeTextField.rightViewMode = .always
        case .question:
            contentLab.text = "回答问题并验证"
            codeTextField.placeholder = "请输入答案"
            codeTextField.rightViewMode = .never
        }
        contentLab.sizeToFit()
    }
    
    func updateFrame() {
        
        changeBtn.frame.origin = CGPoint(x: view.bounds.width - changeBtn.bounds.width - marginSpaceForHorizontal, y: Constants.statusBarHeight + 32)
        
        titleLab.frame.origin = CGPoint(x: 15, y: 80 + Constants.statusBarHeight)
        
        contentLab.frame.origin = CGPoint(x: titleLab.frame.minX, y: titleLab.frame.maxY + 8)
        
        questionLab.frame.origin = CGPoint(x: contentLab.frame.minX, y: contentLab.frame.maxY + 16)
        questionLab.sizeToFit()
        
        codeTextField.frame.origin.y = questionLab.frame.maxY + 48
        
        newPwdTextField.frame.origin.y = codeTextField.frame.maxY + 48
        
        sureBtn.frame.origin.y = contentView.bounds.height - sureBtn.bounds.height - 120
        sureBtn.center.x = view.bounds.width / 2
    }
}

// MARK: data

extension ForgotViewController {
    
    // request
    
    func request() {
        switch retrieveWay {
        case .email:
            ForgotViewController.requestEmail(sno: sno) { email in
                if let email {
                    // 完整邮箱保留给 verify 接口使用；界面仍展示打码后的邮箱（与 Android 行为一致）
                    self.email = email
                    self.questionLab.text = Self.maskEmail(email: email)
                } else {
                    self.questionLab.text = "您的网络出现了问题。"
                }
                self.questionLab.sizeToFit()
            }
        case .question:
            requestQuestion()
        }
    }
    
    func requestQuestion() {
        HttpManager.shared.user_secret_user_bind_question_detail(stu_num: sno ?? "").ry_JSON { response in
            switch response {
            case .success(let model):
                if model["status"].intValue == 10000 {
                    if let question = model["data"].arrayValue.map(ConfidentialityQuestionModel.init(json:)).first {
                        self.question = question
                        self.questionLab.text = "请问: " + question.content
                        self.questionLab.sizeToFit()
                        return
                    }
                }
                fallthrough
            case .failure(_):
                self.questionLab.text = "您的网络出现了问题。"
                self.questionLab.sizeToFit()
            }
        }
    }
}

// MARK: interactive

extension ForgotViewController {
    
    @objc
    func touchUpInside(backBtn: UIButton) {
        navigationController?.popViewController(animated: true)
    }
    
    func sendCode() {
        ProgressHUD.show("发送验证码...")
        HttpManager.shared.user_secret_user_valid_email_code(stu_num: sno ?? "").ry_JSON { response in
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    let expired_time = model["data"]["expired_time"].intValue
                    let expired_date = Date(timeIntervalSince1970: TimeInterval(expired_time))
                    ProgressHUD.showSuccess("验证码已发送到邮箱，过期时间: \(expired_date.string(locale: .cn, format: "hh:mm"))")
                } else {
                    ProgressHUD.showFailed(self.userSecretTip(status: status, info: model["info"].stringValue, fallback: "验证码发送失败"))
                }
            case .failure(_):
                ProgressHUD.showFailed("网络异常，验证码发送失败")
            }
        }
    }
    
    // sure btn
    
    func checkSureBtn() {
        var code: String? = nil
        if newPwdTextField.isHidden {
            code = codeTextField.text
        } else {
            code = newPwdTextField.text
        }
        
        if let code, code.count > 0 {
            sureBtn.isEnabled = true
            sureBtn.backgroundColor = .hex("#4A45DC")
        } else {
            sureBtn.isEnabled = false
            sureBtn.backgroundColor = .ry(light: "#C2CBFE", dark: "#AFBAD6")
        }
    }
    
    @objc
    func clickSureBtn(btn: UIButton) {
        if newPwdTextField.isHidden {
            verify()
        } else {
            changePwd()
        }
    }
    
    // - verify
    
    func verify() {
        ProgressHUD.show("正在验证")
        switch retrieveWay {
        case .email:
            verifyEmail()
        case .question:
            verifyQuestion()
        }
    }
    
    func verifyEmail() {
        let code = codeTextField.text ?? ""
        let email = email ?? ""
        let stu_num = sno ?? ""
        HttpManager.shared.user_secret_user_valid_email(code: code, email: email, stu_num: stu_num).ry_JSON { response in
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    self.code = model["data"]["code"].stringValue
                    ProgressHUD.showSuccess("验证成功")
                    self.successVerify()
                } else {
                    ProgressHUD.showError(self.userSecretTip(status: status, info: model["info"].stringValue, fallback: "验证邮箱失败"))
                }
            case .failure(_):
                ProgressHUD.showError("网络异常，验证邮箱失败")
            }
        }
    }
    
    func verifyQuestion() {
        let question_id = "\(question?.id ?? 0)"
        let content = codeTextField.text ?? ""
        let stu_num = sno ?? ""
        HttpManager.shared.user_secret_user_valid_question(question_id: question_id, content: content, stu_num: stu_num).ry_JSON { response in
            switch response {
            case .success(let model):
                if model["status"].intValue == 10000 {
                    self.code = model["data"]["code"].stringValue
                    ProgressHUD.showSuccess("验证成功")
                    self.successVerify()
                } else {
                    fallthrough
                }
            case .failure(_):
                ProgressHUD.showFailed("验证问题失败")
            }
        }
    }
    
    // 把 user-secret 业务状态码翻译成用户可读文案；未知码优先取后端 info，否则用兜底文案
    private func userSecretTip(status: Int, info: String, fallback: String) -> String {
        switch status {
        case 10005: return "回答错误，请重新输入"
        case 10006: return "尝试次数已达上限，请十分钟后再试"
        case 10007: return "验证码错误，请重新输入"
        case 10008: return "邮箱信息错误"
        case 10009: return "发送次数已达上限，请十分钟后再试"
        case 10022: return "邮箱格式错误"
        default:
            return info.isEmpty ? fallback : info
        }
    }

    func successVerify() {
        changeBtn.isHidden = true
        codeTextField.isEnabled = false
        newPwdTextField.isHidden = false
        sureBtn.setTitle("更改密码", for: .normal)
        checkSureBtn()
    }
    
    // - change pwd
    
    func changePwd() {
        let new_password = newPwdTextField.text ?? ""
        let code = code ?? ""
        let stu_num = sno ?? ""
        HttpManager.shared.user_secret_user_password_valid(new_password: new_password, code: code, stu_num: stu_num).ry_JSON { response in
            if case .success(let model) = response, model["status"].intValue == 10000 {
                ProgressHUD.showSucceed("修改密码成功")
                self.navigationController?.popToRootViewController(animated: true)
            } else {
                ProgressHUD.showSucceed("修改密码失败")
            }
        }
    }
    
    // change btn
    
    @objc
    func touchupInside(changeBtn: UIButton) {
        changeTypeIfVisible()
    }
    
    func changeTypeIfVisible() {
        switch retrieveWay {
        case .email:
            if bidingType.question {
                changeTo(retrieveWay: .question)
            } else {
                self.askToQQGroup()
            }
            break
        case .question:
            if bidingType.email {
                changeTo(retrieveWay: .email)
            } else {
                self.askToQQGroup()
            }
        }
    }
    
    func changeTo(retrieveWay: RetrieveWay) {
        UIView.transition(with: view, duration: 0.3, options: .transitionFlipFromRight) {
            self.retrieveWay = retrieveWay
            self.commitInit()
        }
    }
}

// MARK: static request

extension ForgotViewController {
    
    // 获取用户绑定的邮箱，返回完整地址。完整地址仅用于验证接口；展示请用 maskEmail(email:)
    static func requestEmail(sno: String?, handle: @escaping (String?) -> ()) {
        HttpManager.shared.user_secret_user_bind_email_detail(stu_num: sno ?? "").ry_JSON { response in
            switch response {
            case .success(let model):
                if model["status"].intValue == 10000 {
                    handle(model["data"]["email"].stringValue)
                } else {
                    handle(nil)
                }
            case .failure(_):
                handle(nil)
            }
        }
    }

    // 打码邮箱，仅用于界面展示。不要把打码结果回传给验证接口——
    // 后端会校验邮箱格式（状态码 10022 "the email pattern is wrong"）
    static func maskEmail(email: String) -> String {
        let emailComponents = email.components(separatedBy: "@")
        guard emailComponents.count == 2 else {
            return email
        }

        let username = emailComponents[0]
        let domain = emailComponents[1]

        var maskedUsername = ""
        if username.count > 2 {
            let startIndex = username.index(username.startIndex, offsetBy: 2)
            if username.count > 7 {
                let endIndex = username.index(username.startIndex, offsetBy: 7)
                maskedUsername = String(username.prefix(upTo: startIndex)) + String(repeating: "*", count: username.distance(from: startIndex, to: endIndex)) + String(username.suffix(from: endIndex))
            } else {
                maskedUsername = String(username.prefix(upTo: startIndex)) + String(repeating: "*", count: username.distance(from: startIndex, to: username.endIndex))
            }
        } else if username.count == 2 {
            maskedUsername = String(username.prefix(1)) + "*"
        } else if username.count == 1 {
            maskedUsername = "*"
        }

        return maskedUsername + "@" + domain
    }
}
