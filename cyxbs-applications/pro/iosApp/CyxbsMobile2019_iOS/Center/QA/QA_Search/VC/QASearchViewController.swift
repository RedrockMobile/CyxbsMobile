//
//  QASearchViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit
import MJRefresh
import JXSegmentedView
import SnapKit

class QASearchViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {
    
    // MARK: - 新增初始搜索词属性
    private var initialSearchKeyword: String?
    
    // MARK: - 变量定义
    var qaType: QAType = .all
    let qaModel = QAModel()
    private var qaTypeString: String?
    private var tableViewCount: Int = 0
    private var hasMoreData = true
    
    // 搜索相关属性
    private var isSearching = false
    private var searchKeyword = ""
    
    // 添加约束引用
    private var contentViewHeightConstraint: Constraint?
    private var tableViewHeightConstraint: Constraint?
    
    // 高度缓存
    private var heightCache: [Int: CGFloat] = [:]
    
    // MARK: - 修改初始化方法
    init(qaType: QAType, initialSearchKeyword: String? = nil) {
        self.qaType = qaType
        self.initialSearchKeyword = initialSearchKeyword
        
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
        
        // 初始状态下不加载任何数据
        updateContentViewHeight()
        
        // 如果有初始搜索词，自动执行搜索
        if let initialKeyword = initialSearchKeyword, !initialKeyword.isEmpty {
            // 延迟执行以确保视图加载完成
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self.searchTextField.text = initialKeyword
                self.searchQA(keyword: initialKeyword)
            }
        }
    }
    
    // MARK: - 重要方法
    
    // 初始化视图层次结构
    private func setupViews() {
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)
        contentView.addSubview(searchTextField)
        contentView.addSubview(qaTableView)
        contentView.addSubview(emptyStateLabel)
    }
    
    // 搜索QA项目
    private func searchQA(keyword: String) {
        guard !keyword.isEmpty else {
            // 如果关键词为空，清空搜索结果
            isSearching = false
            searchKeyword = ""
            qaModel.qa.removeAll()
            tableViewCount = 0
            qaTableView.reloadData()
            updateContentViewHeight()
            emptyStateLabel.isHidden = false
            emptyStateLabel.text = "请输入搜索关键词"
            return
        }
        
        isSearching = true
        searchKeyword = keyword
        emptyStateLabel.isHidden = true
        
        qaModel.requestSearchObjects(keyword: keyword) { [weak self] qa in
            guard let self = self else { return }
            
            // 清除旧数据
            self.qaModel.qa.removeAll()
            
            // 添加搜索结果
            self.qaModel.qa.append(contentsOf: qa)
            
            // 应用分类过滤
            self.filterQAItems()
            
            // 清除高度缓存
            self.clearHeightCache()
            
            DispatchQueue.main.async {
                self.qaTableView.reloadData()
                self.updateContentViewHeight()
                
                // 显示空状态提示
                if self.tableViewCount == 0 {
                    self.emptyStateLabel.isHidden = false
                    self.emptyStateLabel.text = "没有找到相关结果"
                } else {
                    self.emptyStateLabel.isHidden = true
                }
            }
        } failure: { error in
            ActivityHUD.shared.showNetworkError()
            
            // 显示错误状态
            self.emptyStateLabel.isHidden = false
            self.emptyStateLabel.text = "搜索失败，请重试"
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
        
        searchTextField.snp.makeConstraints { make in
            make.top.equalToSuperview().offset(20)
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.height.equalTo(38)
        }
        
        qaTableView.snp.makeConstraints { make in
            make.top.equalTo(searchTextField.snp.bottom).offset(16)
            make.left.equalToSuperview().offset(16)
            make.right.equalToSuperview().offset(-16)
            make.bottom.equalToSuperview().offset(-16)
            // 初始高度设置为0，后续会动态调整
            self.tableViewHeightConstraint = make.height.equalTo(0).constraint
        }
        
        emptyStateLabel.snp.makeConstraints { make in
            make.centerX.equalToSuperview()
            make.top.equalTo(searchTextField.snp.bottom).offset(60)
            make.left.right.equalToSuperview().inset(20)
        }
    }
    
    // 更新内容视图高度
    private func updateContentViewHeight() {
        var totalHeight: CGFloat = 0
        
        // 计算所有cell的总高度
        for i in 0..<tableViewCount {
            let indexPath = IndexPath(row: i, section: 0)
            totalHeight += self.tableView(qaTableView, heightForRowAt: indexPath)
        }
        
        // 加上section footer的高度
        totalHeight += 16
        
        let contentHeight = 20 + 38 + 16 + totalHeight + 16 // 搜索按钮上方间距 + 搜索按钮高度 + 间距 + 表格高度 + 底部间距
        
        // 更新约束
        self.contentViewHeightConstraint?.update(offset: contentHeight)
        self.tableViewHeightConstraint?.update(offset: totalHeight)
        
        // 立即布局
        self.view.layoutIfNeeded()
    }
    
    // 计算cell高度
    private func calculateCellHeight(for qaItem: QAObject) -> CGFloat {
        // 获取tableView的宽度（减去左右边距）
        let tableViewWidth = qaTableView.bounds.width
        let contentWidth = tableViewWidth - 32 // 左右各16的边距
        
        // 计算问题标签所需高度
        let questionFont = UIFont(name: "PingFangSC", size: 16) ?? UIFont.systemFont(ofSize: 15.5, weight: .regular)
        let questionHeight = calculateTextHeight(
            text: qaItem.questionString,
            font: questionFont,
            width: contentWidth - 136 // 左边48 + 右边48+32+8
        )
        
        // 计算回答预览所需高度（最多2行）
        let answerFont = UIFont(name: "PingFangSC", size: 16) ?? UIFont.systemFont(ofSize: 16, weight: .regular)
        let answerHeight = answerFont.lineHeight * 2
        
        // 固定部分的高度
        let fixedHeight: CGFloat = 14 + 5 + 33 + 16 // 顶部间距 + 问题与回答间距 + 底部区域 + 底部间距
        
        // 总高度
        let totalHeight = fixedHeight + questionHeight + answerHeight
        
        return totalHeight
    }
    
    // 计算文本高度的方法
    private func calculateTextHeight(text: String, font: UIFont, width: CGFloat, maxLines: Int? = nil) -> CGFloat {
        let constraintRect = CGSize(width: width, height: .greatestFiniteMagnitude)
        let boundingBox = text.boundingRect(
            with: constraintRect,
            options: .usesLineFragmentOrigin,
            attributes: [.font: font],
            context: nil
        )
        
        var height = ceil(boundingBox.height)
        
        // 如果指定了最大行数，则限制高度
        if let maxLines = maxLines {
            let maxHeight = font.lineHeight * CGFloat(maxLines)
            height = min(height, maxHeight)
        }
        
        return height
    }
    
    private func clearHeightCache() {
        heightCache.removeAll()
    }
    
    // 过滤项目
    private func filterQAItems() {
        if let qaTypeString = qaTypeString, !qaTypeString.isEmpty {
            // 过滤出匹配标签的QA项目
            qaModel.qa = qaModel.qa.filter { $0.tags == qaTypeString }
        }
        //过滤未回答项目
        qaModel.qa = qaModel.qa.filter { $0.status == 2 }
        // 如果qaTypeString为空，保持原样（显示所有项目）
        tableViewCount = qaModel.qa.count
    }
    
    // 高亮显示关键字
    private func highlightText(in string: String, with keyword: String) -> NSAttributedString {
        let attributedString = NSMutableAttributedString(string: string)
        let range = (string as NSString).range(of: keyword, options: .caseInsensitive)
        
        if range.location != NSNotFound {
            attributedString.addAttribute(.foregroundColor, value: UIColor(hexString: "#5E5ADF")!, range: range)
        }
        
        return attributedString
    }
    
    // MARK: - 懒加载
    
    lazy var searchTextField: UITextField = {
        let textField = UITextField()
        textField.placeholder = "搜索关键词"
        textField.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677"))
        textField.font = UIFont(name: PingFangSC, size: 16)
        
        textField.backgroundColor = UIColor.ry(light: "#E8F0FC", dark: "#1A1A1A")
        textField.layer.cornerRadius = 19
        textField.delegate = self
        
        // 添加左右内边距
        let paddingView = UIView(frame: CGRect(x: 0, y: 0, width: 20, height: textField.frame.height))
        textField.leftView = paddingView
        textField.leftViewMode = .always
        textField.rightView = paddingView
        textField.rightViewMode = .always
        
        // 添加清除按钮
        textField.clearButtonMode = .whileEditing
        
        // 添加键盘返回按钮类型
        textField.returnKeyType = .search
        
        // 添加编辑改变事件
        textField.addTarget(self, action: #selector(searchTextChanged(_:)), for: .editingChanged)
        
        return textField
    }()
    
    @objc private func searchTextChanged(_ textField: UITextField) {
        guard let keyword = textField.text, !keyword.isEmpty else {
            // 如果搜索框为空，清空搜索结果
            isSearching = false
            searchKeyword = ""
            qaModel.qa.removeAll()
            tableViewCount = 0
            qaTableView.reloadData()
            updateContentViewHeight()
            emptyStateLabel.isHidden = false
            emptyStateLabel.text = "请输入搜索关键词"
            return
        }
        
        // 延迟搜索，避免频繁请求
        NSObject.cancelPreviousPerformRequests(withTarget: self, selector: #selector(performSearch), object: nil)
        self.perform(#selector(performSearch), with: nil, afterDelay: 0.5)
    }
    
    @objc private func performSearch() {
        guard let keyword = searchTextField.text, !keyword.isEmpty else { return }
        searchQA(keyword: keyword)
    }
    
    lazy var qaTableView: UITableView = {
        let qaTableView = UITableView()
        qaTableView.backgroundColor = .clear
        qaTableView.separatorStyle = .none
        qaTableView.delegate = self
        qaTableView.dataSource = self
        qaTableView.layer.cornerRadius = 8
        qaTableView.layer.masksToBounds = true
        qaTableView.isScrollEnabled = false // 禁用表格自身的滚动
        qaTableView.register(QASearchTableViewCell.self, forCellReuseIdentifier: "QACell")
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
    
    lazy var emptyStateLabel: UILabel = {
        let label = UILabel()
        label.text = "请输入搜索关键词"
        label.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677"))
        label.font = UIFont(name: PingFangSC, size: 16)
        label.textAlignment = .center
        label.numberOfLines = 0
        return label
    }()
    
    // MARK: - TableView代理和数据源
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        if let cachedHeight = heightCache[indexPath.row] {
            return cachedHeight + 16 // 加上cell之间的间距
        }
        
        // 防止数组越界
        guard indexPath.item < qaModel.qa.count else {
            return 132 + 16 // 默认高度加上间距
        }
        
        // 获取对应数据
        let qaItem = qaModel.qa[indexPath.item]
        
        // 计算cell高度
        let cellHeight = calculateCellHeight(for: qaItem)
        
        // 缓存高度
        heightCache[indexPath.row] = cellHeight
        
        return cellHeight + 16 // 加上cell之间的间距
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
        let cell = qaTableView.dequeueReusableCell(withIdentifier: "QACell", for: indexPath) as! QASearchTableViewCell
        // 防止数组越界
        guard indexPath.item < qaModel.qa.count else {
            return cell
        }
        
        let qaItem = qaModel.qa[indexPath.item]
        
        // 设置问题文本，如果有搜索关键词则高亮显示
        if isSearching && !searchKeyword.isEmpty {
            // 修改：使用新的高亮方法（只改变文字颜色）
            cell.questionLabel.attributedText = highlightText(in: qaItem.questionString, with: searchKeyword)
        } else {
            cell.questionLabel.text = qaItem.questionString
            cell.questionLabel.textColor = UIColor.ry(light: "#15315B", dark: "#767677")
        }
        
        cell.categoryLabel.text = "\(qaItem.tags)类"
        
        // 设置答案预览文本，如果有搜索关键词则高亮显示
        if isSearching && !searchKeyword.isEmpty {
            // 修改：使用新的高亮方法（只改变文字颜色）
            cell.ansPrevLabel.attributedText = highlightText(in: qaItem.answerString, with: searchKeyword)
        } else {
            cell.ansPrevLabel.text = qaItem.answerString
            cell.ansPrevLabel.textColor = UIColor(light: UIColor(hexString: "#15315B", alpha: 0.4), dark: UIColor(hexString: "#767677", alpha: 1))
        }
        
        // 设置日期
        if let formattedDate = qaModel.dateFormatter(dateString: qaItem.aTime) {
            cell.dateLabel.text = formattedDate
        } else {
            cell.dateLabel.text = "日期格式错误"
        }
        
        cell.likeCountLabel.text = "\(qaItem.likeCount)"
        cell.likeButton.isSelected = qaItem.isLike
        cell.likeButton.tag = indexPath.item
        cell.likeButton.addTarget(self, action: #selector(like(_:)), for: .touchUpInside)
        
        return cell
    }
    
    @objc func like(_ sender: UIButton) {
        // 使用tag获取对应的indexPath
        let indexPath = IndexPath(item: sender.tag, section: 0)
        
        guard indexPath.item < qaModel.qa.count else {
            return
        }
        
        var qaObject = qaModel.qa[indexPath.item]
        
        if qaObject.isLike{
            HttpManager.shared.magipoke_qa_unlike(id: qaObject.ID).ry_JSON{ [weak self] response in
                guard let self = self else { return }
                
                switch response {
                case .success:
                    print("Unlike Succeed")
                    qaObject.isLike = false
                    qaObject.likeCount -= 1
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞成功")
                    
                    self.qaModel.qa[indexPath.item] = qaObject
                    
                    DispatchQueue.main.async {
                        // 直接更新对应的cell，而不是遍历视图层级
                        if let cell = self.qaTableView.cellForRow(at: indexPath) as? QASearchTableViewCell {
                            cell.likeCountLabel.text = "\(qaObject.likeCount)"
                            cell.likeButton.isSelected = qaObject.isLike
                        }
                    }
                case .failure:
                    print("Unlike Failed")
                    RemindHUD.shared().showDefaultHUD(withText: "取消点赞失败，请检查网络")
                }
            }
        }else{
            HttpManager.shared.magipoke_qa_like(id: qaObject.ID).ry_JSON { [weak self] response in
                guard let self = self else { return }
                
                switch response {
                case .success:
                    print("Like Succeed")
                    qaObject.isLike = true
                    qaObject.likeCount += 1
                    RemindHUD.shared().showDefaultHUD(withText: "点赞成功")
                    
                    self.qaModel.qa[indexPath.item] = qaObject
                    
                    DispatchQueue.main.async {
                        // 直接更新对应的cell，而不是遍历视图层级
                        if let cell = self.qaTableView.cellForRow(at: indexPath) as? QASearchTableViewCell {
                            cell.likeCountLabel.text = "\(qaObject.likeCount)"
                            cell.likeButton.isSelected = qaObject.isLike
                        }
                    }
                    
                case .failure:
                    print("Like Failed")
                    RemindHUD.shared().showDefaultHUD(withText: "点赞失败，请检查网络")
                }
            }
        }
    }
}

// MARK: - UITextFieldDelegate
extension QASearchViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        if let keyword = textField.text, !keyword.isEmpty {
            searchQA(keyword: keyword)
        }
        return true
    }
    
    func textFieldShouldClear(_ textField: UITextField) -> Bool {
        // 清除搜索状态，清空搜索结果
        isSearching = false
        searchKeyword = ""
        qaModel.qa.removeAll()
        tableViewCount = 0
        qaTableView.reloadData()
        updateContentViewHeight()
        emptyStateLabel.isHidden = false
        emptyStateLabel.text = "请输入搜索关键词"
        return true
    }
}

// MARK: - Segment返回containerView展示的视图
extension QASearchViewController: JXSegmentedListContainerViewListDelegate {
    func listView() -> UIView {
        return view
    }
}
