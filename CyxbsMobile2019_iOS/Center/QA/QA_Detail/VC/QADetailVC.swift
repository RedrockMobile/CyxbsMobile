//
//  QADetailVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/14.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import Alamofire
import MBProgressHUD

class QADetailVC: UIViewController {
    
    init(objectId: Int) {
        self.objectId = objectId
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public var objectId: Int
    let qaModel = QAModel()
    var qaObject: QAObject!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "1A1A1A")
        
        // 添加滚动视图和内容视图
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)
        
        // 将详细视图添加到内容视图
        contentView.addSubview(detailView)
        
        // 添加顶部按钮（保持在视图层级顶部）
        view.addSubview(backButton)
        view.addSubview(publishButton)
        
        setPosition()
        requestDetail()
    }
    
    func setPosition() {
        
        backButton.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalTo(view.safeAreaLayoutGuide.snp.top).offset(13)
            make.width.equalTo(9)
            make.height.equalTo(18)
        }
        
        publishButton.snp.makeConstraints { make in
            make.right.equalToSuperview().offset(-16)
            make.top.equalTo(view.safeAreaLayoutGuide.snp.top).offset(13)
            make.width.equalTo(60)
            make.height.equalTo(28)
        }
        
        // 滚动视图约束
        scrollView.snp.makeConstraints { make in
            make.top.equalTo(backButton.snp.bottom).offset(20)
            make.left.right.bottom.equalToSuperview()
        }
        
        // 内容视图约束
        contentView.snp.makeConstraints { make in
            make.edges.equalToSuperview()
            make.width.equalTo(scrollView)
            // 高度将在数据加载后更新
        }
        
        // 详细视图约束
        detailView.snp.makeConstraints { make in
            make.top.left.right.equalToSuperview()
            make.bottom.equalToSuperview().priority(.low)
        }
    }
    
    // 获取问题详情
    @objc func requestDetail() {
        // 显示加载指示器
        MBProgressHUD.showAdded(to: self.view, animated: true)
        
        qaModel.requestDetailObject(id: self.objectId) { [weak self] qaObject in
            guard let self = self else { return }
            
            // 隐藏加载指示器
            MBProgressHUD.hide(for: self.view, animated: true)
            
            print("获取问题详情成功")
            self.qaObject = qaObject
            self.loadDataToView() // 在获取到数据后再加载到视图
        } failure: { [weak self] error in
            guard let self = self else { return }
            
            // 隐藏加载指示器
            MBProgressHUD.hide(for: self.view, animated: true)
            
            print("获取问题详情失败\(error)")
            // 可以添加错误处理，比如显示错误提示
        }
    }
    
    func loadDataToView() {
        // 添加安全解包
        guard let qaObject = self.qaObject else {
            print("qaObject is nil")
            return
        }
        
        let dateString = qaModel.dateFormatter(dateString: qaObject.aTime)
        detailView.questionLabel.text = qaObject.questionString
        detailView.categoryLabel.text = "\(qaObject.tags)类"
        detailView.dateLabel.text = dateString
        detailView.answerDetailView.contentLabel.text = qaObject.answerString
        self.detailView.answerDetailView.likeButton.isSelected = qaObject.isLike
        detailView.answerDetailView.viewCountLabel.text = "\(qaObject.viewCount)"
        detailView.answerDetailView.likeCountLabel.text = "\(qaObject.likeCount)"
        detailView.answerDetailView.likeButton.addTarget(self, action: #selector(like), for: .touchUpInside)
        
        // 重新布局detailView
        detailView.commonInit()
        
        // 计算内容高度并更新约束
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            // 强制布局更新
            self.detailView.setNeedsLayout()
            self.detailView.layoutIfNeeded()
            
            // 计算内容总高度
            let contentHeight = self.detailView.systemLayoutSizeFitting(
                UIView.layoutFittingCompressedSize
            ).height
            
            print("Calculated content height: \(contentHeight)")
            print("Screen height: \(UIScreen.main.bounds.height)")
            
            // 更新内容视图的高度约束
            self.contentView.snp.remakeConstraints { make in
                make.edges.equalToSuperview()
                make.width.equalTo(self.scrollView)
                make.height.equalTo(contentHeight)
            }
            
            // 更新滚动视图的内容大小
            self.scrollView.contentSize = CGSize(
                width: self.view.frame.width,
                height: contentHeight
            )
            
            // 打印调试信息
            print("ScrollView contentSize: \(self.scrollView.contentSize)")
            print("ScrollView frame: \(self.scrollView.frame)")
        }
    }
    
    /// 懒加载
    lazy var scrollView: UIScrollView = {
        let scrollView = UIScrollView()
        scrollView.showsVerticalScrollIndicator = true
        scrollView.alwaysBounceVertical = true
        scrollView.backgroundColor = .clear
        scrollView.isScrollEnabled = true
        return scrollView
    }()
    
    lazy var contentView: UIView = {
        let view = UIView()
        view.backgroundColor = .clear
        return view
    }()
    
    lazy var detailView: QADetailView = {
        let detailView = QADetailView()
        return detailView
    }()
    
    lazy var backButton: UIButton = {
        let backButton = UIButton()
        backButton.setImage(UIImage(named: "Back"), for: .normal)
        backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        return backButton
    }()
    
    lazy var publishButton: UIButton = {
        let publishButton = UIButton()
        publishButton.setTitle("发布", for: .normal)
        publishButton.setTitleColor(.white, for: .normal)
        publishButton.titleLabel!.font = UIFont(name: PingFangSC, size: 14)
        publishButton.backgroundColor = UIColor(hexString: "#4841E2")
        publishButton.layer.cornerRadius = 14
        publishButton.addTarget(self, action: #selector(publish), for: .touchUpInside)
        return publishButton
    }()
    
    /// 按钮点击事件
    @objc func popVC() {
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func like() {
        HttpManager.shared.magipoke_qa_like(id: qaObject.ID).ry_JSON { response in
            switch response {
            case .success:
                if self.qaObject.isLike {
                    print("Unlike Succeed")
                    self.qaObject.isLike = false
                    self.qaObject.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                } else {
                    print("Like Succeed")
                    self.qaObject.isLike = true
                    self.qaObject.likeCount += 1
                    RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
                }
            case .failure:
                print("Like Failed")
                RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
            }
            self.detailView.answerDetailView.likeCountLabel.text = "\(self.qaObject.likeCount)"
            self.detailView.answerDetailView.likeButton.isSelected = !self.detailView.answerDetailView.likeButton.isSelected
        }
    }
    
    @objc func publish() {
        let newQuestionPage = NewQuestionVC()
        self.navigationController?.pushViewController(newQuestionPage, animated: true)
    }
}
