//
//  NewQuestionVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import SnapKit

class NewQuestionVC : UIViewController, UITextFieldDelegate {
    
    // MARK: - 必要变量及生命周期
    
    var question : String?
    var tags : String?
    private var recommendedQA: QAObject? // 存储推荐的问题
    private var hasShownRecommendation = false // 新增：标记是否已显示推荐内容
    
    // 添加键盘相关属性
    private var optionButtonSetBottomConstraint: Constraint?
    private var originalOptionButtonSetBottomOffset: CGFloat = -64
    
    // 推荐视图相关属性
    private var recommendView: RecommendedView?
    private var recommendViewHeightConstraint: Constraint?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "#1A1A1A")
        view.addSubview(topView)
        topView.commonInit()
        view.addSubview(questionInput)
        view.addSubview(optionButtonSet)
        setPosition()
        
        // 添加键盘监听
        setupKeyboardObservers()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        // 自动弹出键盘
        questionInput.becomeFirstResponder()
    }
    
    deinit {
        // 移除键盘监听
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - 键盘监听
    
    private func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillShow(_:)),
            name: UIResponder.keyboardWillShowNotification,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillHide(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }
    
    @objc private func keyboardWillShow(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let keyboardFrame = userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect,
              let animationDuration = userInfo[UIResponder.keyboardAnimationDurationUserInfoKey] as? TimeInterval else {
            return
        }
        
        let keyboardHeight = keyboardFrame.height
        
        // 更新optionButtonSet的位置到键盘上方
        optionButtonSetBottomConstraint?.update(offset: -keyboardHeight - 16) // 16是间距
        
        UIView.animate(withDuration: animationDuration) {
            self.view.layoutIfNeeded()
        }
    }
    
    @objc private func keyboardWillHide(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let animationDuration = userInfo[UIResponder.keyboardAnimationDurationUserInfoKey] as? TimeInterval else {
            return
        }
        
        // 恢复optionButtonSet的原始位置
        optionButtonSetBottomConstraint?.update(offset: originalOptionButtonSetBottomOffset)
        
        UIView.animate(withDuration: animationDuration) {
            self.view.layoutIfNeeded()
        }
    }
    
    // MARK: - 重要方法
    
    func setPosition() {
        
        topView.snp.makeConstraints{ make in
            make.top.equalToSuperview()
            make.left.equalToSuperview()
            make.right.equalToSuperview()
            make.height.equalTo(80)
        }
        
        questionInput.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(32)
            make.right.equalToSuperview().offset(-32)
            make.top.equalTo(topView.snp.bottom)
            make.height.equalTo(80)
        }
        
        // 修改optionButtonSet的约束，保存底部约束引用
        optionButtonSet.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            self.optionButtonSetBottomConstraint = make.bottom.equalToSuperview().offset(originalOptionButtonSetBottomOffset).constraint
            make.height.equalTo(28)
        }
        
    }
    
    // MARK: - 推荐视图相关方法
    
    private func setupRecommendView() {
        // 移除现有的推荐视图
        recommendView?.removeFromSuperview()
        recommendView = nil
        
        // 创建新的推荐视图
        let recommendView = RecommendedView()
        recommendView.commonInit()
        recommendView.isHidden = true // 初始隐藏
        
        // 为整个推荐视图添加点击手势
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleRecommendationTap))
        recommendView.addGestureRecognizer(tapGesture)
        recommendView.isUserInteractionEnabled = true
        
        view.addSubview(recommendView)
        self.recommendView = recommendView
        
        // 设置推荐视图约束
        recommendView.snp.makeConstraints { make in
            make.top.equalTo(questionInput.snp.bottom).offset(16)
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            self.recommendViewHeightConstraint = make.height.equalTo(0).constraint // 初始高度为0
        }
    }
    
    private func showRecommendView(with qaObject: QAObject) {
        guard let recommendView = recommendView else { return }
        
        // 更新推荐视图内容
        recommendView.questionLabel.text = qaObject.questionString
        recommendView.categoryLabel.text = qaObject.tags
        recommendView.ansPrevLabel.text = qaObject.answerString
        recommendView.likeCountLabel.text = "\(qaObject.likeCount)"
        recommendView.likeButton.isSelected = qaObject.isLike
        recommendView.likeButton.addTarget(self, action: #selector(like(_:)), for: .touchUpInside)
        recommendView.layer.cornerRadius = 8
        recommendView.layer.masksToBounds = true
        
        
        // 格式化日期
        let dateFormatter = QAModel()
        if let formattedDate = dateFormatter.dateFormatter(dateString: qaObject.aTime) {
            recommendView.dateLabel.text = formattedDate
        } else {
            recommendView.dateLabel.text = "暂无日期"
        }
        
        // 计算推荐视图的高度
        let height = calculateRecommendViewHeight(for: qaObject)
        
        // 显示推荐视图
        recommendView.isHidden = false
        recommendViewHeightConstraint?.update(offset: height)
        UIView.animate(withDuration: 0.3) {
            self.view.layoutIfNeeded()
        }
        
        // 设置已显示推荐内容的标志
        hasShownRecommendation = true
    }
    
    private func hideRecommendView() {
        recommendViewHeightConstraint?.update(offset: 0)
        self.recommendView?.isHidden = true
        UIView.animate(withDuration: 0.3) {
            self.view.layoutIfNeeded()
        }
        
        // 重置已显示推荐内容的标志
        hasShownRecommendation = false
    }
    
    private func calculateRecommendViewHeight(for qaObject: QAObject) -> CGFloat {
        // 获取推荐视图的宽度（减去左右边距）
        let viewWidth = UIScreen.main.bounds.width - 32 // 左右各16的边距
        
        // 安全地获取字体，提供回退方案
        let questionFont = UIFont(name: "PingFangSC", size: 16) ?? UIFont.systemFont(ofSize: 16, weight: .regular)
        let answerFont = UIFont(name: "PingFangSC", size: 16) ?? UIFont.systemFont(ofSize: 16, weight: .regular)
        
        // 计算问题标签所需高度
        let questionWidth = viewWidth - 64 - 48 - 16 - 20 // 左边64 + 右边48(分类标签宽度) + 8(间距) + 20(调试)
        let questionHeight = calculateTextHeight(
            text: qaObject.questionString,
            font: questionFont,
            width: questionWidth
        )
        
        // 计算回答预览所需高度（固定两行）
        let answerHeight = answerFont.lineHeight * 2
        
        // 固定部分的高度
        let fixedHeight: CGFloat = 42 + 20 + 5 + 33 + 16 // 顶部区域 + 问题与回答间距 + 底部区域 + 底部间距
        
        // 总高度
        let totalHeight = fixedHeight + questionHeight + answerHeight
        
        // 确保高度不会太小
        return max(totalHeight, 120) // 最小高度为120
    }
    
    private func calculateTextHeight(text: String, font: UIFont, width: CGFloat) -> CGFloat {
        let constraintRect = CGSize(width: width, height: .greatestFiniteMagnitude)
        let boundingBox = text.boundingRect(
            with: constraintRect,
            options: .usesLineFragmentOrigin,
            attributes: [.font: font],
            context: nil
        )
        
        return ceil(boundingBox.height)
    }
    
    @objc private func handleRecommendationTap() {
        // 处理推荐视图的点击事件
        guard let recommendedQA = recommendedQA else { return }
        
        // 打开指定的视图控制器
        let detailVC = QADetailVC(objectId: recommendedQA.ID) // 请替换为实际的视图控制器类名
        self.navigationController?.pushViewController(detailVC, animated: true)
    }
    
    // MARK: - 搜索和发布逻辑
    
    private func searchBeforePublish(completion: @escaping (Bool) -> Void) {
        guard let questionText = questionInput.text?.trimmingCharacters(in: .whitespacesAndNewlines),
              !questionText.isEmpty else {
            completion(false)
            return
        }
        
        let model = QAModel()
        model.requestSearchObjects(keyword: questionText) { [weak self] qaObjects in
            guard let self = self else {
                completion(false)
                return
            }
            
            // 查找第一个status为2的对象
            if let recommended = qaObjects.first(where: { $0.status == 2 }) {
                self.recommendedQA = recommended
                DispatchQueue.main.async {
                    // 确保推荐视图已设置
                    if self.recommendView == nil {
                        self.setupRecommendView()
                    }
                    self.showRecommendView(with: recommended)
                    completion(true)
                }
            } else {
                self.recommendedQA = nil
                DispatchQueue.main.async {
                    self.hideRecommendView()
                    completion(false)
                }
            }
        } failure: { error in
            print("搜索失败: \(error)")
            completion(false)
        }
    }
    
    private func performPublish() {
        // 获取问题和标签
        guard let questionText = questionInput.text?.trimmingCharacters(in: .whitespacesAndNewlines),
              !questionText.isEmpty else {
            RemindHUD.shared().showDefaultHUD(withText: "请先输入问题")
            return
        }
        
        self.question = questionText
        
        // 获取选中的标签
        guard let selectedTag = optionButtonSet.getSelectedValue() else {
            RemindHUD.shared().showDefaultHUD(withText: "请选择问题标签")
            return
        }
        
        self.tags = selectedTag
        
        // 直接发布
        HttpManager.shared.magipoke_qa_publishQuestion(q: self.question!, tags: self.tags!).ry_JSON { [weak self] response in
            guard let self = self else { return }
            
            switch response{
            case .success:
                print("Successfully Published!")
                RemindHUD.shared().showDefaultHUD(withText: "发布成功")
                self.navigationController?.popViewController(animated: true)
            case .failure:
                print("Publish Failed!")
                RemindHUD.shared().showDefaultHUD(withText: "发布失败，请检查网络")
            }
        }
    }
    
    // MARK: - 懒加载
    
    lazy var topView : NewQuestionTopView = {
        let topView = NewQuestionTopView()
        topView.backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        // 直接绑定到发布方法
        topView.publishButton.addTarget(self, action: #selector(publish), for: .touchUpInside)
        return topView
    }()
    
    lazy var questionInput : UITextField = {
        let questionInput = UITextField()
        questionInput.borderStyle = .none
        questionInput.placeholder = "请输入你的问题~"
        questionInput.font = UIFont(name: PingFangSC, size: 16)
        questionInput.delegate = self
        // 添加文本变化监听
        questionInput.addTarget(self, action: #selector(textFieldDidChange(_:)), for: .editingChanged)
        return questionInput
    }()
    
    lazy var optionButtonSet : OptionButtonsView = {
        let optionButtonSet = OptionButtonsView()
        optionButtonSet.options = [
            ("新生类","新生"),
            ("生活类","生活"),
            ("学习类","学习"),
            ("其他","其他")
        ]
        return optionButtonSet
    }()
    
    // MARK: - 按钮选项
    
    @objc func popVC(){
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func publish(){
        // 如果已经显示过推荐内容，则直接发布
        if hasShownRecommendation {
            performPublish()
            return
        }
        
        // 否则先搜索相关内容
        searchBeforePublish { [weak self] hasRecommendation in
            guard let self = self else { return }
            
            if hasRecommendation {
                // 如果有推荐内容，显示推荐视图，不立即发布
                print("找到相关问题，显示推荐视图")
            } else {
                // 如果没有推荐内容，直接发布
                self.performPublish()
            }
        }
    }
    
    @objc func textFieldDidChange(_ textField: UITextField) {
        // 当输入框内容变化时，重置推荐状态
        hasShownRecommendation = false
        hideRecommendView()
    }
    
    @objc func like(_ sender: UIButton) {
        // 使用tag获取对应的indexPath
        
        var qaObject = recommendedQA
        
        
        if qaObject!.isLike{
            HttpManager.shared.magipoke_qa_unlike(id: qaObject!.ID).ry_JSON{ [weak self] response in
                guard let self = self else { return }
                
                switch response {
                case .success:
                    print("Unlike Succeed")
                    qaObject!.isLike = false
                    qaObject!.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                    
                    self.recommendedQA?.isLike = false
                    self.recommendedQA?.likeCount -= 1
                    
                    DispatchQueue.main.async {
                        self.recommendView!.likeCountLabel.text = "\(qaObject!.likeCount)"
                        self.recommendView!.likeButton.isSelected = qaObject!.isLike
                    }
                case .failure:
                    print("Unlike Failed")
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞失败，请检查网络")
                }
            }
        }else{
            HttpManager.shared.magipoke_qa_like(id: qaObject!.ID).ry_JSON { [weak self] response in
                guard let self = self else { return }
                
                switch response {
                case .success:
                    print("Like Succeed")
                    qaObject!.isLike = true
                    qaObject!.likeCount += 1
                    RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
                    
                    DispatchQueue.main.async {
                        self.recommendView!.likeCountLabel.text = "\(qaObject!.likeCount)"
                        self.recommendView!.likeButton.isSelected = qaObject!.isLike
                    }
                    
                case .failure:
                    print("Like Failed")
                    RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
                }
            }
        }
    }
    
    // MARK: - 代理
    func textFieldDidEndEditing(_ textField: UITextField) {
        if let text = questionInput.text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            self.question = text
            // 确保有选中的标签
            if let selectedTag = optionButtonSet.getSelectedValue() {
                self.tags = selectedTag
            }
        }
    }
}
