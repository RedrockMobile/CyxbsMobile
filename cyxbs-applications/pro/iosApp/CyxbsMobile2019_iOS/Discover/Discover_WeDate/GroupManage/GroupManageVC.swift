//
//  GroupManageVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/3.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

class GroupManageVC: UIViewController {
    /// 数据数组
    private var memberAry: [StudentItem] = []
    /// 组名
    private var groupName: String = ""
    /// 组id
    private var groupID: Int = 0
    
    // MARK: - Life Cycle
    
    init(groupID: Int, groupName: String, memberAry: [StudentItem]) {
        super.init(nibName: nil, bundle: nil)
        self.groupName = groupName
        self.memberAry = memberAry
        self.groupID = groupID
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        searchTextFieldBackView.addSubview(searchTextField)
        view.addSubview(searchTextFieldBackView)
        view.addSubview(returnBtn)
        view.addSubview(groupNameLab)
        view.addSubview(tableView)
        view.addSubview(inquireBtn)
        
        view.backgroundColor = .weDatePageBackground
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        
        searchTextFieldBackView.frame = CGRect(x: 16, y: groupNameLab.bottom + 18, width: SCREEN_WIDTH - 16 * 2, height: 44)
        searchTextField.frame = CGRect(x: 17, y: 0, width: SCREEN_WIDTH - 33 * 2, height: 44)
        returnBtn.frame = CGRect(x: 16, y: statusBarHeight + 13, width: 7, height: 16)
        groupNameLab.frame = CGRect(x: returnBtn.right + 13 , y: returnBtn.top - 7, width: SCREEN_WIDTH - 90, height: 31)
        tableView.snp.makeConstraints { make in
            make.left.right.equalToSuperview()
            make.top.equalTo(searchTextFieldBackView.snp.bottom).offset(17)
            make.bottom.equalTo(view).inset(98)
        }
        inquireBtn.frame = CGRect(x: (SCREEN_WIDTH - 120) / 2, y: tableView.bottom + 28, width: 120, height: 42)
        
        // 为按钮添加线性渐变
        let gradientLayer = CAGradientLayer()
        gradientLayer.colors = [
            UIColor.weDateGradientStart.cgColor,
            UIColor.weDateGradientEnd.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0)
        gradientLayer.endPoint = CGPoint(x: 1, y: 1)
        gradientLayer.frame = inquireBtn.bounds
        inquireBtn.layer.insertSublayer(gradientLayer, at: 0)
    }
    
    // MARK: - Method
    
    @objc private func clickReturnBtn() {
        navigationController?.popViewController(animated: true)
    }
    
    @objc private func clickDoneBtn() {
        // 搜索框中有文字且数据库存在此数据时跳转SearchResult,否则弹出弹窗
        if let text = searchTextField.text,
           !text.isEmpty {
            view.endEditing(true)
            searchTextField.text = ""
            
            SearchResultModel.requestWithKey(text) { searchResultModel in
                if searchResultModel.isExist {
                    // 处理数据
                    let searchResultVC = SearchResultVC(types: searchResultModel.types, studentAry: searchResultModel.studentAry, classAry: searchResultModel.classAry, groupAry: searchResultModel.groupAry)
                    searchResultVC.delegate = self
                    searchResultVC.modalPresentationStyle = .custom

                    self.navigationController?.present(searchResultVC, animated: true, completion: nil)
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
    
    @objc private func clickInquireBtn() {
        var studentIDAry: [String] = []
        var studentNamesByStuNum: [String: String] = [:]
        for student in memberAry {
            studentIDAry.append(student.studentID)
            studentNamesByStuNum[student.studentID] = student.name
        }
        #if DEBUG
        print("[WeDateCourseSchedule] fixed group inquire memberCount=\(memberAry.count), stuNumCount=\(studentIDAry.count)")
        #endif
        TaskManager.shared.uploadTaskProgress(title: "使用一次没课约", stampCount: 10, remindText: "今日已使用没课约1次，获得10张邮票")
        let vc = WeDateCourseScheduleVC(stuNumAry: studentIDAry, studentNamesByStuNum: studentNamesByStuNum)
        self.navigationController?.present(vc, animated: true, completion: nil)
    }
    
    /// 网络请求删除成员
    static func deleteMemberWith(groupID: Int, studentID: String) {
        let parameters = ["group_id": groupID, "stu_nums": studentID] as [String : Any]
        HttpTool.share().request(Discover_POST_deleteMember_API,
                                 type: .post,
                                 serializer: .HTTP,
                                 bodyParameters: parameters,
                                 progress: nil,
                                 success: { task, object in
        },
                                 failure: { task, error in
            print(error)
        })
    }
    
    /// 网络请求添加成员
    static func addMemberWith(groupID: Int, studentID: [String]) {
        let studentIDStr = studentID.joined(separator: ",")
        let parameters = ["group_id": groupID, "stu_nums": studentIDStr] as [String : Any]
        HttpTool.share().request(Discover_POST_addMember_API,
                                 type: .post,
                                 serializer: .HTTP,
                                 bodyParameters: parameters,
                                 progress: nil,
                                 success: { task, object in
        },
                                 failure: { task, error in
            print(error)
        })
    }
    
    // MARK: - Lazy
    
    /// 返回按钮
    private lazy var returnBtn: UIButton = {
        let returnBtn = UIButton()
        returnBtn.setImage(UIImage(named: "空教室返回"), for: .normal)
        returnBtn.addTarget(self, action: #selector(clickReturnBtn), for: .touchUpInside)
        return returnBtn
    }()
    /// 组名文本
    private lazy var groupNameLab: UILabel = {
        let groupNameLab = UILabel()
        groupNameLab.text = groupName
        groupNameLab.font = .systemFont(ofSize: 22, weight: .medium)
        groupNameLab.textColor = .weDateTitleText
        return groupNameLab
    }()
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
        let attributedPlaceholder = NSAttributedString(string: "添加同学、分组或班级", attributes: attributes)
        searchTextField.attributedPlaceholder = attributedPlaceholder
        searchTextField.textColor = .weDatePrimaryText
        searchTextField.tintColor = .weDatePrimaryText
        searchTextField.returnKeyType = .search
        searchTextField.delegate = self
        TemporaryGroupVC().addKeyBoardToolBarforTextField(searchTextField)
        return searchTextField
    }()
    /// 搜索框下方展示组内人员信息
    private lazy var tableView: UITableView = {
        let tableView = UITableView(frame: .zero, style: .plain)
        tableView.estimatedRowHeight = 0
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.separatorStyle = .none
        tableView.backgroundColor = .weDatePageBackground
        tableView.register(StudentTableViewCell.self, forCellReuseIdentifier: StudentTableViewCellReuseIdentifier)
        tableView.dataSource = self
        tableView.delegate = self
        return tableView
    }()
    /// 查询按钮
    private lazy var inquireBtn: UIButton = {
        let inquireBtn = UIButton()
        inquireBtn.layer.cornerRadius = 22
        inquireBtn.clipsToBounds = true
        inquireBtn.setTitle("查询", for: .normal)
        inquireBtn.titleLabel?.font = .boldSystemFont(ofSize: 18)
        inquireBtn.addTarget(self, action: #selector(clickInquireBtn), for: .touchUpInside)
        return inquireBtn
    }()
}

// MARK: - UITextFieldDelegate

extension GroupManageVC: UITextFieldDelegate {
    
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if textField == searchTextField {
            clickDoneBtn()
        }
        return true
    }
}

// MARK: - UITableViewDataSource

extension GroupManageVC: UITableViewDataSource {
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return memberAry.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: StudentTableViewCellReuseIdentifier, for: indexPath) as! StudentTableViewCell
        cell.selectionStyle = .none
        cell.nameLab.text = memberAry[indexPath.row].name
        cell.stuNumLab.text = memberAry[indexPath.row].studentID
        return cell
    }
}

// MARK: - UITableViewDelegate

extension GroupManageVC: UITableViewDelegate {
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 54
    }
    
    func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        let deleteAction = UIContextualAction(style: .destructive, title: "删除") { action, sourceView, completionHandler in
            let studentID = self.memberAry[indexPath.row].studentID
            GroupManageVC.deleteMemberWith(groupID: self.groupID, studentID: studentID)
            self.memberAry.remove(at: indexPath.row)
            self.tableView.reloadData()
        }
        deleteAction.backgroundColor = .hex("#ED535C")
        
        let configuration = UISwipeActionsConfiguration(actions: [deleteAction])
        configuration.performsFirstActionWithFullSwipe = false
        return configuration
    }
}

// MARK: - SearchResultVCDelegate

extension GroupManageVC: SearchResultVCDelegate {
    
    // 点击搜索结果的添加后调用
    func updateStudentData(name: [String], number: [String]) {
        GroupManageVC.addMemberWith(groupID: groupID, studentID: number)
        // 添加学生到此分组（避免重复添加
        for i in 0..<name.count {
            /// 分组是否已有此成员
            var isRepeat = false
            for j in 0..<memberAry.count {
                if memberAry[j].name == name[i] {
                    isRepeat = true
                    break
                }
            }
            
            if !isRepeat {
                let dic: [String: String] = ["stu_name": name[i], "stu_num": number[i]]
                memberAry.append(StudentItem(dictionary: dic))
            }
        }
        tableView.reloadData()
    }
}
