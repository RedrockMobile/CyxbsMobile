//
//  EmailBidingViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/10/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import ProgressHUD

class EmailBidingViewController: BaseTextFiledViewController {
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        contentView.addSubview(jumpBtn)
        contentView.addSubview(titleLab)
        contentView.addSubview(contentLab)
        contentView.addSubview(emailTextField)
        contentView.addSubview(codeTextField)
        contentView.addSubview(sureBtn)
        
        setupUI()
        updateFrame()
    }
    
    // MARK: lazy
    
    lazy var emailTextField: UITextField = {
        let textField = createForgotTypeTextFiled(placeholder: "请输入邮箱", leftImgName: nil)
        textField.keyboardType = .asciiCapable
        textField.addTarget(self, action: #selector(editingDidEnd(emailTextField:)), for: .editingDidEnd)
        return textField
    }()
    
    lazy var codeTextField: UITextField = {
        let textField = createForgotTypeTextFiled(placeholder: "请输入邮箱验证码", leftImgName: nil, countDown: true)
        textField.keyboardType = .numberPad
        return textField
    }()
    
    lazy var sureBtn: UIButton = {
        let btn = createSureButton(size: CGSize(width: 280, height: 52), title: "绑 定", touchUpInside: #selector(clickSureBtn(btn:)))
        btn.isEnabled = false
        return btn
    }()
    
    lazy var jumpBtn: UIButton = {
        let btn = createSmallBtn(title: " 跳过 ", touchUpInsideAction: #selector(touchUpInside(jumpBtn:)))
        btn.frame.origin = CGPoint(x: view.bounds.width - btn.bounds.width - 17, y: Constants.statusBarHeight + 10)
        return btn
    }()
    
    // MARK: countdown
    
    override func shouldCountDown() -> Bool {
        if let email = emailCheckedValid() {
            requestCode(email: email)
            return true
        }
        return false
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        checkSureBtn()
    }
}

// MARK: data

extension EmailBidingViewController {
    
    func setupUI() {
        titleLab.text = "绑定你的邮箱"
        titleLab.sizeToFit()
        
        contentLab.text = "用于忘记密码时可以通过邮箱找回"
        contentLab.sizeToFit()
    }
    
    func updateFrame() {
        titleLab.sizeToFit()
        titleLab.frame.origin = CGPoint(x: 15, y: 80 + Constants.statusBarHeight)
        
        contentLab.sizeToFit()
        contentLab.frame.origin = CGPoint(x: titleLab.frame.minX, y: titleLab.frame.maxY + 8)
        
        emailTextField.frame.origin.y = contentLab.frame.maxY + 32
        
        codeTextField.frame.origin.y = emailTextField.frame.maxY + 32
        
        sureBtn.frame.origin.y = codeTextField.frame.maxY + 82
        sureBtn.center.x = view.bounds.width / 2
    }
}

// MARK: interactive

extension EmailBidingViewController {
    
    @objc
    func touchUpInside(jumpBtn: UIButton) {
        showJump(title: "跳过绑定")
    }
    
    @discardableResult
    func emailCheckedValid() -> String? {
        let email = emailTextField.text ?? ""
        if !isValidEmail(email) {
            ProgressHUD .showError("请输入正确的email格式")
            return nil
        }
        return email
    }
    
    func checkSureBtn() {
        if let email = emailTextField.text, email.count > 0,
           let code = codeTextField.text, code.count > 0 {
            
            sureBtn.isEnabled = true
            sureBtn.backgroundColor = .hex("#4A45DC")
        } else {
            
            sureBtn.isEnabled = false
            sureBtn.backgroundColor = .ry(light: "#C2CBFE", dark: "#AFBAD6")
        }
    }
    
    @objc
    func editingDidEnd(emailTextField: UITextField) {
        emailCheckedValid()
    }
    
    func isValidEmail(_ email: String) -> Bool {
        let emailRegex = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        do {
            let regex = try NSRegularExpression(pattern: emailRegex, options: .caseInsensitive)
            let matches = regex.matches(in: email, options: [], range: NSRange(location: 0, length: email.count))
            return !matches.isEmpty
        } catch {
            return false
        }
    }
    
    @objc
    func clickSureBtn(btn: UIButton) {
        biding()
    }
    
    func biding() {
        guard let email = emailCheckedValid() else { return }
        
        let code = codeTextField.text ?? ""
        if code.count == 0 {
            ProgressHUD.showError("请输入你的验证码")
            return
        }
        ProgressHUD.show("正在绑定")
        HttpManager.shared.user_secret_user_bind_email(email: email, code: code).ry_JSON { response in
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    ProgressHUD.showSuccess("绑定成功")
                    self.skipBinding()
                } else {
                    fallthrough
                }
            case .failure(_):
                ProgressHUD.showFailed("绑定失败")
                self.showJump(title: "绑定失败")
            }
        }
    }
    
    func showJump(title: String?) {
        let alertVC = UIAlertController.normalType(title: title, content: "你可以跳过本次绑定，但当你更改了密码却忘记密码时，你必须联系相关人员。", cancelText: "继续绑定", sureText: "跳过绑定") { action in
            
            if action.title == "跳过绑定" {
                // 避免 Alert 还在收起时直接触发 push/pop 转场冲突。
                DispatchQueue.main.async {
                    self.skipBinding()
                }
            }
        }
        present(alertVC, animated: true)
    }
    
    /// 该页面在登录后是 push 到导航栈里的；这里只能返回上一页，不能用 dismiss。
    /// 否则 iOS 27 会因为 presentedViewController 为 nil 而终止转场回调，导致跳过弹窗看似无响应。
    func skipBinding() {
        if let nav = navigationController, !nav.viewControllers.isEmpty {
            nav.popViewController(animated: true)
        } else {
            dismissSelf()
        }
    }

    func dismissSelf() {
        self.dismiss(animated: true) {
            self.dismissAction?(false, nil)
        }
    }
}

// MARK: request

extension EmailBidingViewController {
    
    struct BidingType {
        let question: Bool
        let email: Bool
    }
    
    static func isBiding(sno: String? = nil, handle: @escaping (BidingType?) -> ()) {
        HttpManager.shared.user_secret_user_bind_is(stu_num: sno ?? UserModel.default.token?.stuNum ?? "").ry_JSON { response in
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    let question_is = model["data"]["question_is"].intValue != 0
                    let email_is = model["data"]["email_is"].intValue != 0
                    let bidingType = BidingType(question: question_is, email: email_is)
                    handle(bidingType)
                } else {
                    fallthrough
                }
            case .failure(_):
                handle(nil)
            }
        }
    }
    
    func requestCode(email: String) {
        HttpManager.shared.user_secret_user_bind_email_code(email: email).ry_JSON { response in
            switch response {
            case .success(let model):
                let status = model["status"].intValue
                if status == 10000 {
                    let expired_time = model["data"]["expired_time"].intValue
                    let expired_date = Date(timeIntervalSince1970: TimeInterval(expired_time))
                    ProgressHUD.showSuccess("验证码已发送到邮箱，过期时间: \(expired_date.string(locale: .cn, format: "hh:mm"))")
                } else {
                    fallthrough
                }
            case .failure(_):
                ProgressHUD.showFailed("验证码发送失败")
            }
        }
    }
}
