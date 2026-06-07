//
//  FixedGroupVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

class FixedGroupVC: UIViewController {
    
    /// 分组信息数组
    private var groupAry: [GroupItem] = []
    /// 搜索结果VC
    private var searchResultVC: SearchResultVC?
    
    // MARK: - Life Cycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        searchTextFieldBackView.addSubview(searchTextField)
        view.addSubview(searchTextFieldBackView)
        view.addSubview(tableView)
        view.addSubview(foundBtn)
        view.backgroundColor = .weDatePageBackground
    }
    
    override func viewWillAppear(_ animated: Bool) {
        receiveGroup()
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        view.endEditing(true)
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        
        searchTextFieldBackView.frame = CGRect(x: 16, y: 23, width: SCREEN_WIDTH - 16 * 2, height: 44)
        searchTextField.frame = CGRect(x: 17, y: 0, width: SCREEN_WIDTH - 33 * 2, height: 44)
        tableView.snp.makeConstraints { make in
            make.left.right.equalToSuperview()
            make.top.equalTo(searchTextFieldBackView.snp.bottom).offset(17)
            make.bottom.equalTo(view).inset(98)
        }
        foundBtn.frame = CGRect(x: (SCREEN_WIDTH - 120) / 2, y: tableView.bottom + 28, width: 120, height: 42)
        promptLab.frame = CGRect(x: (SCREEN_WIDTH - 173) / 2, y: foundBtn.top - 81, width: 173, height: 36)

        // 为按钮添加线性渐变
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = foundBtn.bounds
        foundBtn.layer.insertSublayer(gradientLayer, at: 0)
    }
    
    // MARK: - Method
    
    @objc private func clickDoneBtn() {
        // 搜索框中有文字且数据库存在此数据时跳转SearchResult,否则弹出弹窗
        if let text = searchTextField.text,
           !text.isEmpty {
            view.endEditing(true)
            searchTextField.text = ""
            
            SearchResultModel.requestWithKey(text) { searchResultModel in
                if searchResultModel.isExist,
                   searchResultModel.types == ["学生"] || searchResultModel.types == ["学生", "分组"] {
                    // 处理数据
                    self.searchResultVC = SearchResultVC(types: ["学生"], studentAry: searchResultModel.studentAry, classAry: [], groupAry: [])
                    self.searchResultVC!.delegate = self
                    self.searchResultVC!.modalPresentationStyle = .custom
                    self.navigationController?.present(self.searchResultVC!, animated: true, completion: nil)
                } else {
                    let vc = SearchAlertVC(alertStr: "信息有误，请重新输入")
                    vc.modalPresentationStyle = .overFullScreen
                    self.navigationController?.present(vc, animated: false, completion: nil)
                }
            } failure: { error in
                print(error)
            }
        } else {
            view.endEditing(true)
            let vc = SearchAlertVC(alertStr: "信息有误，请重新输入")
            vc.modalPresentationStyle = .overFullScreen
            present(vc, animated: false, completion: nil)
        }
    }
    
    @objc private func clickFoundBtn() {
        let vc = CreateGroupVC(studentIDAry: [UserItem.default().stuNum])
        vc.modalPresentationStyle = .custom
        vc.delegate = self
        present(vc, animated: true, completion: nil)
    }
    
    /// 得到所有分组
    private func receiveGroup() {
        FixedGroupModel.getGroup { groupModel in
            var array: [GroupItem] = []
            self.groupAry.removeAll()
            for group in groupModel.groupAry {
                if group.isTop {
                    self.groupAry.append(group)
                } else {
                    array.append(group)
                }
            }
            self.groupAry.append(contentsOf: array)
            self.tableView.reloadData()
        } failure: { error in
            print(error)
            self.view.addSubview(self.promptLab)
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                self.promptLab.isHidden = true
            }
        }
    }
    
    // MARK: - Lazy
    
    /// 搜索框所在视图
    private lazy var searchTextFieldBackView: UIView = {
        let searchTextFieldBackView = UIView()
        searchTextFieldBackView.layer.cornerRadius = 22
        searchTextFieldBackView.clipsToBounds = true
        searchTextFieldBackView.backgroundColor = .weDateSearchBackground
        return searchTextFieldBackView
    }()
    /// 搜索框
    private lazy var searchTextField: UITextField = {
        let searchTextField = UITextField()
        let attributes: [NSAttributedString.Key: Any] = [
            .foregroundColor: UIColor.weDatePlaceholderText,
            .font: UIFont.systemFont(ofSize: 18)
        ]
        let attributedPlaceholder = NSAttributedString(string: "添加同学", attributes: attributes)
        searchTextField.attributedPlaceholder = attributedPlaceholder
        searchTextField.textColor = .weDatePrimaryText
        searchTextField.tintColor = .weDatePrimaryText
        searchTextField.returnKeyType = .search
        searchTextField.delegate = self
        TemporaryGroupVC().addKeyBoardToolBarforTextField(searchTextField)
        return searchTextField
    }()
    /// 搜索框下方展示组别信息
    private lazy var tableView: UITableView = {
        let tableView = UITableView(frame: .zero, style: .plain)
        tableView.estimatedRowHeight = 0
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.separatorStyle = .none
        tableView.backgroundColor = .weDatePageBackground
        tableView.register(GroupTableViewCell.self, forCellReuseIdentifier: GroupTableViewCellReuseIdentifier)
        tableView.dataSource = self
        tableView.delegate = self
        return tableView
    }()
    /// 创建按钮
    private lazy var foundBtn: UIButton = {
        let foundBtn = UIButton()
        foundBtn.layer.cornerRadius = 22
        foundBtn.clipsToBounds = true
        foundBtn.setTitle("创建", for: .normal)
        foundBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        foundBtn.addTarget(self, action: #selector(clickFoundBtn), for: .touchUpInside)
        return foundBtn
    }()
    /// 网络异常的提示语
    private lazy var promptLab: UILabel = {
        let promptLab = UILabel()
        promptLab.text = "网络异常请检查网络"
        promptLab.font = .systemFont(ofSize: 13)
        promptLab.textAlignment = .center
        promptLab.layer.cornerRadius = 18
        promptLab.clipsToBounds = true
        promptLab.textColor = .white
        promptLab.backgroundColor = .weDateToastBackground
        return promptLab
    }()
}

// MARK: - JXSegmentedListContainerViewListDelegate

extension FixedGroupVC: JXSegmentedListContainerViewListDelegate {
    
    func listView() -> UIView {
        return view
    }
}

// MARK: - UITextFieldDelegate

extension FixedGroupVC: UITextFieldDelegate {
    
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if textField == searchTextField {
            clickDoneBtn()
        }
        return true
    }
}

// MARK: - UITableViewDataSource

extension FixedGroupVC: UITableViewDataSource {
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return groupAry.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: GroupTableViewCellReuseIdentifier, for: indexPath) as! GroupTableViewCell
        cell.nameLab.text = groupAry[indexPath.row].name
        cell.selectionStyle = .none
        return cell
    }
}

// MARK: - UITableViewDelegate

extension FixedGroupVC: UITableViewDelegate {
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 46
    }
    
    func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        let deleteAction = UIContextualAction(style: .destructive, title: "删除") { action, sourceView, completionHandler in
            let groupID = self.groupAry[indexPath.row].groupID
            FixedGroupModel.deleteGroupWithGroupID(groupID)
            self.groupAry.remove(at: indexPath.row)
            self.tableView.reloadData()
        }
        deleteAction.backgroundColor = .hex("#ED535C")
        let stickyAction = UIContextualAction(style: .destructive, title: "置顶") { action, sourceView, completionHandler in
            let name = self.groupAry[indexPath.row].name
            let groupID = self.groupAry[indexPath.row].groupID
            FixedGroupModel.stickyGroup(name: name, groupID: groupID)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                self.receiveGroup()
            }
        }
        stickyAction.backgroundColor = .weDateGradientStart
        
        let configuration = UISwipeActionsConfiguration(actions: [deleteAction, stickyAction])
        configuration.performsFirstActionWithFullSwipe = false
        return configuration
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let group = groupAry[indexPath.row]
        let vc = GroupManageVC(groupID: group.groupID, groupName: group.name, memberAry: group.members)
        navigationController?.pushViewController(vc, animated: true)
    }
}

// MARK: - CreateGroupVCDelegate

extension FixedGroupVC: CreateGroupVCDelegate {
    
    func updateGroupData() {
        receiveGroup()
    }
}

// MARK: - SearchResultVCDelegate

extension FixedGroupVC: SearchResultVCDelegate {
    
    func updateStudentData(name: [String], number: [String]) {
        searchResultVC?.dismiss(animated: true, completion: {
            let studentID = number.first
            let vc = ChooseGroupVC(studentID: studentID!)
            vc.modalPresentationStyle = .custom
            self.navigationController?.present(vc, animated: true, completion: nil)
        })
    }
}
