//  说是TableView，实际上还包含了搜索框，希望你能看到这句话，诶嘿～
//  本文件一定程度上参考了ActivityCollectionViewController.swift
//  想要看注释的话建议去那份文档里
//
//  QATableView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/19.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import MJRefresh
import JXSegmentedView

class QAViewController : UIViewController, UITableViewDelegate, UITableViewDataSource{
    
    var qaType : QAType = .all
    let qaModel = QAModel()
    let refreshNum = 10
    private var qaTypeString: String?
    private var tableViewCount : Int = 0
    private var cellHeight : CGFloat = 132
    private var cellWidth : CGFloat = 343
    
    init(qaType: QAType) {
        self.qaType = qaType
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#F5F7FB", dark: "#1A1A1A")
        switch qaType {
        case .all:
            qaTypeString = "全部"
        case .freshMen:
            qaTypeString = "新生"
        case .life:
            qaTypeString = "生活"
        case .study:
            qaTypeString = "学习"
        case .other:
            qaTypeString = "其他"
        }
        requestQA()
        view.addSubview(searchButton)
        view.addSubview(qaTableView)
        setPosition()
        self.addMJHeader()
    }
    
    @objc func requestQA(){
        qaModel.requestQACenterObjects(QATag: qaTypeString!, pageNum: nil, pageSize: nil){ qa in
            print("问答数量：\(qa.count)")
            self.tableViewCount = qa.count
            if(self.qaModel.qa.count < self.refreshNum) {
                self.tableViewCount = self.qaModel.qa.count
            }else{
                self.tableViewCount = self.refreshNum
            }
            self.qaTableView.mj_header?.endRefreshing()
            self.qaTableView.reloadData()
            if (self.tableViewCount != 0) {
                self.addMJFooter()
            }
        } failure: { error in
            ActivityHUD.shared.showNetworkError()
        }
    }
    
    func setPosition(){
        
        searchButton.snp.makeConstraints { make in
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(20)
            make.right.equalToSuperview().offset(-16)
            make.height.equalTo(38)
        }
        
        qaTableView.snp.makeConstraints{ make in
            make.left.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(74)
            make.right.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(20)
        }
        
    }
    
    
    // MARK: - 懒加载
    lazy var searchButton : UIButton = {
        let searchButton = UIButton()
        searchButton.titleLabel?.text = "搜索关键词"
        searchButton.titleLabel?.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#FFFFFF", alpha: 0.4))
        searchButton.titleLabel?.font = UIFont(name: PingFangSC, size: 16)
        searchButton.titleLabel?.textAlignment = .left
        searchButton.titleEdgeInsets = UIEdgeInsets(top: 7, left: 16, bottom: 7, right: 246)
        searchButton.backgroundColor = UIColor.ry(light: "#E8F0FC", dark: "1A1A1A")
        searchButton.layer.cornerRadius = 19
        return searchButton
    }()
    
    lazy var qaTableView : UITableView = {
        let qaTableView = UITableView()
        qaTableView.backgroundColor = .clear
        qaTableView.separatorColor = .clear
        qaTableView.delegate = self
        qaTableView.dataSource = self
        qaTableView.register(QATableViewCell.self, forCellReuseIdentifier: "QACell")
        return qaTableView
    }()
    
    // MARK: - TableView代理
    
    func addMJHeader() {
        let header = MJRefreshNormalHeader(refreshingTarget: self, refreshingAction: #selector(requestQA))
        qaTableView.mj_header = header
    }
    
    func addMJFooter() {
        if let footer = qaTableView.mj_footer {
            footer.endRefreshing()
        }else {
            let footer = MJRefreshAutoNormalFooter(refreshingTarget: self, refreshingAction: #selector(refreshTableView))
            qaTableView.mj_footer = footer
        }
    }
    
    @objc func refreshTableView() {
        if self.tableViewCount != self.qaModel.qa.count {
            self.tableViewCount = self.tableViewCount + self.refreshNum
            if self.tableViewCount > self.qaModel.qa.count {
                self.tableViewCount = self.qaModel.qa.count
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now()) {
                self.qaTableView.mj_footer?.endRefreshing()
                self.qaTableView.reloadData()
            }
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now()) {
                self.qaTableView.mj_footer?.endRefreshingWithNoMoreData()
                ActivityHUD.shared.showNoMoreData()
            }
        }
    }
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return cellHeight
    }
    
    func tableView(_ tableView: UITableView, heightForFooterInSection section: Int) -> CGFloat {
        return 16
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let detailVC = QADetailVC(objectId: qaModel.qa[indexPath.item].ID)
        self.navigationController?.pushViewController(detailVC, animated: true)
    }
    
    // MARK: - TableView数据源
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return tableViewCount
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = qaTableView.dequeueReusableCell(withIdentifier: "QACell", for: indexPath) as! QATableViewCell
        cell.questionLabel.text = qaModel.qa[indexPath.item].questionString
        cell.categoryLabel.text = "\(qaModel.qa[indexPath.item].tags)类"
        cell.ansPrevLabel.text = qaModel.qa[indexPath.item].answerString
        cell.dateLabel.text = qaModel.dateFormatter(dateString: qaModel.qa[indexPath.item].aTime)
        cell.likeCountLabel.text = "\(qaModel.qa[indexPath.item].likeCount)"
        cell.likeButton.isSelected = qaModel.qa[indexPath.item].isLike
        cell.likeButton.addTarget(self, action: #selector(like(_:)), for: .touchUpInside)
        return cell
    }
    
    @objc func like(_ sender: UIButton){
        
        // 用一个sender将cell传入这个函数，方便点赞按钮操作
        guard let cell = sender.superview?.superview as? QATableViewCell,
              let indexPath = qaTableView.indexPath(for: cell) else {
                return
            }
        
        var qaObject = qaModel.qa[indexPath.item]
        
        HttpManager.shared.magipoke_qa_like(id: qaObject.ID).ry_JSON{ response in
            switch response{
            case .success:
                if(qaObject.isLike){
                    print("Unlike Succeed")
                    qaObject.isLike = false
                    qaObject.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                }else{
                    print("Like Succeed")
                    qaObject.isLike = true
                    qaObject.likeCount += 1
                    RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
                }
                
                self.qaModel.qa[indexPath.item] = qaObject
                
                DispatchQueue.main.async {
                    cell.likeCountLabel.text = "\(qaObject.likeCount)"
                    cell.likeButton.isSelected = qaObject.isLike
                }
                
            case .failure:
                print("Like Failed")
                RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
            }
        }
    }
}

// MARK: - Segment返回containerView展示的视图
extension QAViewController: JXSegmentedListContainerViewListDelegate {
    func listView() -> UIView {
        return view
    }
}

