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
import SnapKit

class QAViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {
    
    // MARK: - 变量定义
    var qaType: QAType = .all
    let qaModel = QAModel()
    let refreshNum = 10
    private var qaTypeString: String?
    private var tableViewCount: Int = 0
    private var cellHeight: CGFloat = 132
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMoreData = true
    
    // 添加约束引用
    private var contentViewHeightConstraint: Constraint?
    private var tableViewHeightConstraint: Constraint?
    
    // 添加时间戳记录最后刷新时间
    private var lastRefreshTime: Date?
    // 添加已加载所有数据的标记
    private var hasLoadedAllData = false
    
    // MARK: - 初始化与生命周期
    
    init(qaType: QAType) {
        self.qaType = qaType
        switch qaType {
        case .all:
            qaTypeString = ""
        case .freshMen:
            qaTypeString = "新生"
        case .life:
            qaTypeString = "生活"
        case .study:
            qaTypeString = "学习"
        case .other:
            qaTypeString = "其他"
        }
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.ry(light: "#F5F7FB", dark: "#1A1A1A")
        
        setupViews()
        setPosition()
        self.addMJHeader()
        requestQA()
    }
    
    // MARK: - 重要方法
    
    // 初始化视图层次结构
    private func setupViews() {
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)
        contentView.addSubview(searchButton)
        contentView.addSubview(qaTableView)
    }
    
    @objc func requestQA() {
        // 只有当hasLoadedAllData为false时才执行刷新
        if hasLoadedAllData {
            self.scrollView.mj_header?.endRefreshing()
            return
        }
        
        currentPage = 1
        hasMoreData = true
        isLoadingMore = false
        
        qaModel.requestQACenterObjects(QATag: "", pageNum: currentPage, pageSize: refreshNum) { [weak self] qa in
            guard let self = self else { return }
            
            print("问答数量：\(qa.count)")
            if !qa.isEmpty {
                print("请求标签: \(qa[0].tags)")
            }
            
            // 清除旧数据，如果是刷新操作
            self.qaModel.qa.removeAll()
            
            // 添加新数据
            self.qaModel.qa.append(contentsOf: qa)
            
            // 应用过滤
            self.filterQAItems()
            
            // 检查是否还有更多数据
            self.hasMoreData = qa.count >= self.refreshNum
            
            // 如果没有更多数据，标记为已加载所有数据
            if !self.hasMoreData {
                self.hasLoadedAllData = true
            }
            
            // 更新最后刷新时间
            self.lastRefreshTime = Date()
            
            DispatchQueue.main.async {
                self.qaTableView.reloadData()
                self.updateContentViewHeight()
                self.scrollView.mj_header?.endRefreshing()
                self.scrollView.mj_footer?.endRefreshing()
                
                // 只有在有数据且可能有更多数据时才添加footer
                if self.tableViewCount > 0 && self.hasMoreData {
                    self.addMJFooter()
                } else if let footer = self.scrollView.mj_footer {
                    footer.endRefreshingWithNoMoreData()
                    // 如果没有更多数据，移除footer
                    self.scrollView.mj_footer = nil
                }
            }
        } failure: { error in
            ActivityHUD.shared.showNetworkError()
            self.scrollView.mj_header?.endRefreshing()
            self.scrollView.mj_footer?.endRefreshing()
            self.isLoadingMore = false
        }
    }
    
    private func setPosition() {
        scrollView.snp.makeConstraints { make in
            make.edges.equalTo(view.safeAreaLayoutGuide)
        }
        
        contentView.snp.makeConstraints { make in
            make.edges.equalToSuperview()
            make.width.equalTo(scrollView)
            // 初始高度设置为屏幕高度，后续会动态调整
            self.contentViewHeightConstraint = make.height.equalTo(UIScreen.main.bounds.height).constraint
        }
        
        searchButton.snp.makeConstraints { make in
            make.top.equalToSuperview().offset(20)
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.height.equalTo(38)
        }
        
        qaTableView.snp.makeConstraints { make in
            make.top.equalTo(searchButton.snp.bottom).offset(16)
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-16)
            // 初始高度设置为0，后续会动态调整
            self.tableViewHeightConstraint = make.height.equalTo(0).constraint
        }
    }
    
    // 更新内容视图高度
    private func updateContentViewHeight() {
        let tableViewHeight = CGFloat(tableViewCount) * (cellHeight + 16) + 32 // 32是section footer的高度
        let contentHeight = 20 + 38 + 16 + tableViewHeight + 16 // 搜索按钮上方间距 + 搜索按钮高度 + 间距 + 表格高度 + 底部间距
        
        // 更新约束
        self.contentViewHeightConstraint?.update(offset: contentHeight)
        self.tableViewHeightConstraint?.update(offset: tableViewHeight)
        
        // 立即布局
        self.view.layoutIfNeeded()
    }
    
    // 过滤项目
    private func filterQAItems() {
        if let qaTypeString = qaTypeString, !qaTypeString.isEmpty {
            // 过滤出匹配标签的QA项目
            qaModel.qa = qaModel.qa.filter { $0.tags == qaTypeString }
        }
        // 如果qaTypeString为空，保持原样（显示所有项目）
        tableViewCount = qaModel.qa.count
    }
    
    // MARK: - 懒加载
    
    lazy var searchButton: UIButton = {
        let searchButton = UIButton()
        searchButton.setTitle("搜索关键词", for: .normal)
        searchButton.setTitleColor(UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677")), for: .normal)
        searchButton.titleLabel?.font = UIFont(name: PingFangSC, size: 16)
        searchButton.setImage(UIImage(named: "search_icon"), for: .normal)
        searchButton.imageEdgeInsets = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 8)
        searchButton.semanticContentAttribute = .forceLeftToRight
        searchButton.backgroundColor = UIColor.ry(light: "#E8F0FC", dark: "#1A1A1A")
        searchButton.layer.cornerRadius = 19
        return searchButton
    }()
    
    lazy var qaTableView: UITableView = {
        let qaTableView = UITableView()
        qaTableView.backgroundColor = .clear
        qaTableView.separatorStyle = .none
        qaTableView.delegate = self
        qaTableView.dataSource = self
        qaTableView.layer.cornerRadius = 8
        qaTableView.layer.masksToBounds = true
        qaTableView.isScrollEnabled = false // 禁用表格自身的滚动
        qaTableView.register(QATableViewCell.self, forCellReuseIdentifier: "QACell")
        return qaTableView
    }()
    
    lazy var scrollView: UIScrollView = {
        let scrollView = UIScrollView()
        scrollView.backgroundColor = .clear
        scrollView.alwaysBounceVertical = true
        return scrollView
    }()
    
    lazy var contentView: UIView = {
        let view = UIView()
        view.backgroundColor = .clear
        return view
    }()
    
    // MARK: - TableView代理和数据源
    
    func addMJHeader() {
        let header = MJRefreshNormalHeader(refreshingTarget: self, refreshingAction: #selector(requestQA))
        scrollView.mj_header = header
    }
    
    func addMJFooter() {
        if let _ = scrollView.mj_footer {
            // 已经存在footer，不需要重复添加
            return
        }
        
        let footer = MJRefreshAutoNormalFooter(refreshingTarget: self, refreshingAction: #selector(refreshTableView))
        footer.setTitle("上拉加载更多", for: .idle)
        footer.setTitle("正在加载...", for: .refreshing)
        footer.setTitle("没有更多数据了", for: .noMoreData)
        scrollView.mj_footer = footer
    }
    
    @objc func refreshTableView() {
        // 防止重复加载或已加载所有数据
        if isLoadingMore || hasLoadedAllData {
            if hasLoadedAllData {
                self.scrollView.mj_footer?.endRefreshingWithNoMoreData()
            } else {
                self.scrollView.mj_footer?.endRefreshing()
            }
            return
        }
        
        isLoadingMore = true
        currentPage += 1
        
        qaModel.requestQACenterObjects(QATag: "", pageNum: currentPage, pageSize: refreshNum) { [weak self] newQA in
            guard let self = self else { return }
            
            self.isLoadingMore = false
            
            // 添加新数据
            self.qaModel.qa.append(contentsOf: newQA)
            
            // 应用过滤
            self.filterQAItems()
            
            // 关键修改：检查是否还有更多数据
            self.hasMoreData = newQA.count >= self.refreshNum
            self.hasLoadedAllData = !self.hasMoreData
            
            DispatchQueue.main.async {
                self.qaTableView.reloadData()
                self.updateContentViewHeight()
                
                if self.hasMoreData {
                    self.scrollView.mj_footer?.endRefreshing()
                } else {
                    self.scrollView.mj_footer?.endRefreshingWithNoMoreData()
                    // 如果没有更多数据，移除footer
                    self.scrollView.mj_footer = nil
                }
            }
        } failure: { error in
            self.isLoadingMore = false
            self.scrollView.mj_footer?.endRefreshing()
            ActivityHUD.shared.showNetworkError()
        }
    }
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return cellHeight + 16
    }
    
    func tableView(_ tableView: UITableView, heightForFooterInSection section: Int) -> CGFloat {
        return 16
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let detailVC = QADetailVC(objectId: qaModel.qa[indexPath.item].ID)
        self.navigationController?.pushViewController(detailVC, animated: true)
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return tableViewCount
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = qaTableView.dequeueReusableCell(withIdentifier: "QACell", for: indexPath) as! QATableViewCell
        // 防止数组越界
        guard indexPath.item < qaModel.qa.count else {
            return cell
        }
        
        let qaItem = qaModel.qa[indexPath.item]
        cell.questionLabel.text = qaItem.questionString
        cell.categoryLabel.text = "\(qaItem.tags)类"
        cell.ansPrevLabel.text = qaItem.answerString
        
        // 调试信息 - 打印原始日期字符串
        print("原始日期字符串: \(qaItem.aTime)")
        
        // 设置日期
        if let formattedDate = qaModel.dateFormatter(dateString: qaItem.aTime) {
            cell.dateLabel.text = formattedDate
            print("格式化后的日期: \(formattedDate)")
        } else {
            cell.dateLabel.text = "日期格式错误"
            print("日期格式化失败")
        }
        
        cell.likeCountLabel.text = "\(qaItem.likeCount)"
        cell.likeButton.isSelected = qaItem.isLike
        cell.likeButton.addTarget(self, action: #selector(like(_:)), for: .touchUpInside)
        
        return cell
    }
    
    @objc func like(_ sender: UIButton) {
        // 用sender获取对应的cell和indexPath
        guard let cell = sender.superview?.superview as? QATableViewCell,
              let indexPath = qaTableView.indexPath(for: cell),
              indexPath.item < qaModel.qa.count else {
            return
        }
        
        var qaObject = qaModel.qa[indexPath.item]
        
        HttpManager.shared.magipoke_qa_like(id: qaObject.ID).ry_JSON{ [weak self] response in
            guard let self = self else { return }
            
            switch response {
            case .success:
                if qaObject.isLike {
                    print("Unlike Succeed")
                    qaObject.isLike = false
                    qaObject.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                } else {
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
