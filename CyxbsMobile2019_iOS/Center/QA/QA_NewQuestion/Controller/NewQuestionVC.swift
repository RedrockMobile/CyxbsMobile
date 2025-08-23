//
//  NewQuestionVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

class NewQuestionVC : UIViewController, UITextFieldDelegate {
    
    // MARK: - 必要变量及生命周期
    
    var question : String?
    var tags : String?
    var qaModel : QAModel?
    var recommendObject : QAObject?
    // 添加防抖相关属性
    private var searchTimer: Timer?
    private let searchDebounceInterval: TimeInterval = 0.5 // 500毫秒防抖
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        qaModel = QAModel()
        
        view.addSubview(topView)
        topView.commonInit()
        view.addSubview(questionInput)
        view.addSubview(optionButtonSet)
        setPosition()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        print("Top view frame: \(topView.frame)")
        print("Option button set frame: \(optionButtonSet.frame)")
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
        
        optionButtonSet.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-64)
            make.height.equalTo(28)
        }
        
    }
    
    func loadRecommendData() {
        
        guard let model = qaModel, let questionText = question else {
            print("qaModel or question is nil")
            return
        }
        
        if recommendObject != nil {
            print("Already have recommend data, skipping request")
            DispatchQueue.main.async {
                self.setRecommendToView()
            }
            return
        }
        
        qaModel!.requestSearchObjects(keyword: question!){ [weak self] qa in
            guard let self = self else { return }
            
            self.recommendObject = qa[0]
            
            DispatchQueue.main.async {
                self.setRecommendToView()
            }
            
        }failure: { error in
            ActivityHUD.shared.showNetworkError()
        }
    }
    
    func setRecommendToView() {
        
        guard let object = recommendObject else {
            print("Recommend object is nil")
            return
        }
        
        recommended.questionLabel.text = recommendObject?.questionString
        recommended.categoryLabel.text = "\(recommendObject!.tags)类"
        recommended.ansPrevLabel.text = recommendObject?.answerString
        
        if let model = qaModel {
            let formattedDate = model.dateFormatter(dateString: object.aTime)
            recommended.dateLabel.text = formattedDate
        } else {
            recommended.dateLabel.text = object.aTime // 或者显示原始日期
        }
        
        recommended.likeCountLabel.text = "\(recommendObject!.likeCount)"
        recommended.likeButton.isSelected = recommendObject!.isLike
        recommended.likeButton.addTarget(self, action: #selector(like(_:)), for: .touchUpInside)
        
        self.view.addSubview(recommended)
        
        recommended.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.top.equalTo(questionInput.snp.bottom)
            make.height.equalTo(183)
        }
        
        recommended.onTap = { [weak self] in
            self?.recommendDetail()
        }
        
    }
    
    // MARK: - 懒加载
    
    lazy var topView : NewQuestionTopView = {
        let topView = NewQuestionTopView()
        topView.publishButton.addTarget(self, action: #selector(publish), for: .touchUpInside)
        return topView
    }()
    
    lazy var questionInput : UITextField = {
        let questionInput = UITextField()
        questionInput.borderStyle = .none
        questionInput.placeholder = "请输入你的问题~"
        questionInput.font = UIFont(name: PingFangSC, size: 16)
        questionInput.delegate = self
        return questionInput
    }()
    
    lazy var recommended : RecommendedView = {
        let recommended = RecommendedView()
        return recommended
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
    
    func popVC(){
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func publish(){
        
        guard let question = self.question, !question.isEmpty,
              let tags = self.tags, !tags.isEmpty else {
            RemindHUD.shared().showDefaultHUD(withText: "发布失败，请填写问题和选择标签")
            return
        }
        
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
    
    func recommendDetail(){
        let detailPage = QADetailVC(objectId: self.recommendObject!.ID)
        self.navigationController?.pushViewController(detailPage, animated: true)
    }
    
    @objc func like(_ sender: UIButton){
        HttpManager.shared.magipoke_qa_like(id: recommendObject!.ID).ry_JSON { [weak self] response in
            guard let self = self else { return }
            
            switch response{
            case .success:
                if recommendObject!.isLike{
                    print("Unlike Succeed")
                    recommendObject!.isLike = false
                    recommendObject!.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                }else{
                    print("Like Succeed")
                    recommendObject!.isLike = true
                    recommendObject!.likeCount += 1
                    RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
                }
            case .failure:
                print("Like Failed")
                RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
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
                } else {
                    // 如果没有选中标签，可以设置一个默认值或者提示用户选择
                    self.tags = "其他" // 或者保持为nil，让publish方法处理
                }
                
                // 只有在有有效问题时才加载推荐
                loadRecommendData()
            }
    }
    
    @objc func textFieldDidChange(_ textField: UITextField) {
        // 取消之前的定时器
        searchTimer?.invalidate()
        
        // 设置新的定时器
        searchTimer = Timer.scheduledTimer(withTimeInterval: searchDebounceInterval, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            
            if let text = textField.text, !text.isEmpty {
                self.question = text
                self.loadRecommendData()
            }
        }
    }
    
    // 确保在视图销毁时取消定时器
    deinit {
        searchTimer?.invalidate()
    }
    
}
