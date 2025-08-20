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
        view.addSubview(detailView)
        view.addSubview(backButton)
        view.addSubview(publishButton)
        requestDetail()
        loadDataToView()
    }
    
    //获取问题详情
    @objc func requestDetail() {
        qaModel.requestDetailObject(id: self.objectId) { qaObject in
            print("获取问题详情成功")
            
        } failure: { error in
            print("获取问题详情失败\(error)")
        }
    }
    
    func loadDataToView() {
        //let dateString = qaModel.dateFormatter(dateString: qaObject.aTime)
        detailView.questionLabel.text = qaObject.questionString
        detailView.categoryLabel.text = "\(qaObject.tags)类"
        //detailView.dateLabel.text = dateString
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
        detailView.frame = CGRectMake(0, 0, UIScreen.main.bounds.width, UIScreen.main.bounds.height)
        return detailView
    }()
    
    lazy var backButton : UIButton = {
        let backButton = UIButton()
        backButton.setImage(UIImage(named: "Back"), for: .normal)
        backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        backButton.frame = CGRectMake(18, 59, 7, 16)
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
        publishButton.frame = CGRectMake(299, 53, 60, 28)
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
        //打开发布页面，TBD
    }
    ///DarkMode和LightMode样式TBD
}
