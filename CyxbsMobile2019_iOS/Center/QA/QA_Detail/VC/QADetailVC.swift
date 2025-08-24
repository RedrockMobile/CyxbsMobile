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

class QADetailVC : UIViewController {
    
    init(objectId: Int){
        self.objectId = objectId
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public var objectId : Int
    let qaModel = QAModel()
    var qaObject : QAObject!
    
    
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#FFFFFF", dark: "1A1A1A")
        view.addSubview(detailView)
        view.addSubview(backButton)
        view.addSubview(publishButton)
        
        setPosition()
        requestDetail()
    }
    
    func setPosition() {
        
        backButton.snp.makeConstraints{ make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 13)
            make.width.equalTo(9)
            make.height.equalTo(18)
        }
        
        publishButton.snp.makeConstraints{ make in
            make.right.equalToSuperview().offset(-16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 13)
            make.width.equalTo(60)
            make.height.equalTo(28)
        }
        
        detailView.snp.makeConstraints{ make in
            make.left.equalToSuperview()
            make.right.equalToSuperview()
            make.height.equalToSuperview()
            make.width.equalToSuperview()
        }
        
    }
    
    //获取问题详情
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
        detailView.commonInit()
    }
    
    ///懒加载
    lazy var detailView : QADetailView = {
        let detailView = QADetailView()
        return detailView
    }()
    
    lazy var backButton : UIButton = {
        let backButton = UIButton()
        backButton.setImage(UIImage(named: "Back"), for: .normal)
        backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        return backButton
    }()
    
    lazy var publishButton : UIButton = {
        let publishButton = UIButton()
        publishButton.setTitle("发布", for: .normal)
        publishButton.setTitleColor(.white, for: .normal)
        publishButton.titleLabel!.font = UIFont(name: PingFangSC, size: 14)
        publishButton.backgroundColor = UIColor(hexString: "#4841E2")
        publishButton.layer.cornerRadius = 14
        publishButton.addTarget(self, action: #selector(publish), for: .touchUpInside)
        return publishButton
    }()
    
    ///按钮点击事件
    @objc func popVC(){
        self.navigationController?.popViewController(animated: true)
    }
    
    @objc func like(){
        HttpManager.shared.magipoke_qa_like(id: qaObject.ID).ry_JSON{ response in
            switch response{
            case .success:
                if(self.qaObject.isLike){
                    print("Unlike Succeed")
                    self.qaObject.isLike = false
                    self.qaObject.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                }else{
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
    
    @objc func publish(){
        let newQuestionPage = NewQuestionVC()
        self.navigationController?.pushViewController(newQuestionPage, animated: true)
    }
}
