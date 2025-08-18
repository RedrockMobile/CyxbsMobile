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
    
    let qaModel = QAModel()
    var qaObject : QAObject!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(detailView)
        view.addSubview(backButton)
        view.addSubview(publishButton)
        loadDataToView()
    }
    
    func loadDataToView() {
        let dateString = qaModel.dateFormatter(dateString: qaObject.updateTime)
        detailView.questionLabel.text = qaObject.questionString
        detailView.categoryLabel.text = "\(qaObject.tags)类"
        detailView.dateLabel.text = dateString
        detailView.answerDetailView.contentLabel.text = qaObject.answerString
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
        backButton.addTarget(self, action: #selector(popVC), for: .touchUpInside)
        return backButton
    }()
    
    lazy var publishButton : UIButton = {
        let publishButton = UIButton()
        publishButton.titleLabel!.text = "发布"
        publishButton.titleLabel!.textColor = .white
        publishButton.titleLabel!.textAlignment = .center
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
                print("Like Succeed")
                self.qaObject.isLike = true
                self.qaObject.likeCount += 1
                self.loadDataToView()
                RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
            case .failure:
                print("Like Failed")
                RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
            }
        }
    }
    
    @objc func publish(){
        //打开发布页面，TBD
    }
    ///DarkMode和LightMode样式TBD
}
