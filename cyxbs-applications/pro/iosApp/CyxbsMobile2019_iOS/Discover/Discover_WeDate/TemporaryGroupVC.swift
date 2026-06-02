//
//  TemporaryGroupVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

class TemporaryGroupVC: UIViewController {
    
    private var dataDictionary: [String: [String]] = [:]
    
    private var scheduleVC: WeDateCourseScheduleVC?
    
    // MARK: - Life Cycle

    override func viewDidLoad() {
        super.viewDidLoad()
        
        let user = UserItem.default()
        dataDictionary["name"] = [user.realName]
        dataDictionary["studentID"] = [user.stuNum]
        
        searchTextFieldBackView.addSubview(searchTextField)
        view.addSubview(searchTextFieldBackView)
        view.addSubview(tableView)
        view.addSubview(promptLab)
        view.addSubview(inquireBtn)
        view.backgroundColor = .weDatePageBackground
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            self.promptLab.isHidden = true
        }
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
        promptLab.frame = CGRect(x: (SCREEN_WIDTH - 160) / 2, y: view.height - 151, width: 160, height: 36)
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
    
    /// 为UITextField自定义键盘上的toolBar
    func addKeyBoardToolBarforTextField(_ textField: UITextField) {
        let toolBar = UIToolbar(frame: CGRect(x: 0, y: 0, width: SCREEN_WIDTH, height: 44))
        
        let button = UIButton(frame: CGRect(x: SCREEN_WIDTH - 60, y: 0, width: 50, height: 44))
        toolBar.addSubview(button)
        button.setTitle("完成", for: .normal)
        button.titleLabel?.font = .boldSystemFont(ofSize: 16)
        button.setTitleColor(.weDateAccent, for: .normal)
        button.addTarget(self, action: #selector(clickDoneBtn), for: .touchUpInside)
        
        let label = UILabel()
        toolBar.addSubview(label)
        label.snp.makeConstraints { make in
            make.centerX.top.bottom.equalTo(toolBar)
        }
        label.text = textField.placeholder
        label.font = .systemFont(ofSize: 13)
        label.alpha = 0.8
        label.textColor = .weDateLightMutedText
        
        textField.inputAccessoryView = toolBar
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
        TaskManager.shared.uploadTaskProgress(title: "使用一次没课约", stampCount: 10, remindText: "今日已使用没课约1次，获得10张邮票")
        let studentIDAry = dataDictionary["studentID"] ?? []
        let nameAry = dataDictionary["name"] ?? []
        var studentNamesByStuNum: [String: String] = [:]
        for (index, stuNum) in studentIDAry.enumerated() where index < nameAry.count {
            studentNamesByStuNum[stuNum] = nameAry[index]
        }
        let vc = WeDateCourseScheduleVC(stuNumAry: studentIDAry, studentNamesByStuNum: studentNamesByStuNum)
        scheduleVC = vc
        self.navigationController?.present(vc, animated: true, completion: {
            if !UserDefaults.standard.bool(forKey: "noMoreReminders") {
                let alertVC = CreateGroupAlertVC()
                alertVC.modalPresentationStyle = .overFullScreen
                alertVC.delegate = self
                vc.present(alertVC, animated: false, completion: nil)
            }
        })
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
        let attributedPlaceholder = NSAttributedString(string: "添加同学、分组或班级", attributes: attributes)
        searchTextField.attributedPlaceholder = attributedPlaceholder
        searchTextField.textColor = .weDatePrimaryText
        searchTextField.tintColor = .weDatePrimaryText
        searchTextField.returnKeyType = .search
        searchTextField.delegate = self
        addKeyBoardToolBarforTextField(searchTextField)
        return searchTextField
    }()
    /// 搜索框下方展示学生学号姓名信息
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
    /// 进入没课约后的提示语
    private lazy var promptLab: UILabel = {
        let promptLab = UILabel()
        promptLab.text = "试试左滑删除列表"
        promptLab.font = .systemFont(ofSize: 13)
        promptLab.textAlignment = .center
        promptLab.layer.cornerRadius = 18
        promptLab.clipsToBounds = true
        promptLab.textColor = .white
        promptLab.backgroundColor = .weDateToastBackground
        return promptLab
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

// MARK: - JXSegmentedListContainerViewListDelegate

extension TemporaryGroupVC: JXSegmentedListContainerViewListDelegate {
    
    func listView() -> UIView {
        return view
    }
}

// MARK: - UITableViewDataSource

extension TemporaryGroupVC: UITableViewDataSource {
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        var count = 0
        if let name = dataDictionary["name"] {
            count = name.count
        }
        return count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: StudentTableViewCellReuseIdentifier, for: indexPath) as! StudentTableViewCell
        cell.selectionStyle = .none
        cell.nameLab.text = dataDictionary["name"]![indexPath.row]
        cell.stuNumLab.text = dataDictionary["studentID"]![indexPath.row]
        return cell
    }
}

// MARK: - UITableViewDelegate

extension TemporaryGroupVC: UITableViewDelegate {
    
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 54
    }
    
    func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        let deleteAction = UIContextualAction(style: .destructive, title: "删除") { action, sourceView, completionHandler in
            self.dataDictionary["name"]!.remove(at: indexPath.row)
            self.dataDictionary["studentID"]!.remove(at: indexPath.row)
            tableView.reloadData()
        }
        deleteAction.backgroundColor = .hex("#ED535C")
        let configuration = UISwipeActionsConfiguration(actions: [deleteAction])
        configuration.performsFirstActionWithFullSwipe = false
        return configuration
    }
}

// MARK: - SearchResultVCDelegate

extension TemporaryGroupVC: SearchResultVCDelegate {
    
    // 点击搜索结果的添加后调用
    func updateStudentData(name: [String], number: [String]) {
        // 添加学生到临时分组（避免重复添加
        var count = 0
        for item in number {
            if !(dataDictionary["studentID"]!.contains(where: {$0 == item})) {
                dataDictionary["name"]!.append(name[count])
                dataDictionary["studentID"]!.append(item)
            }
            count += 1
        }
        tableView.reloadData()
    }
}

// MARK: - UITextFieldDelegate

extension TemporaryGroupVC: UITextFieldDelegate {
    
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if textField == searchTextField {
            clickDoneBtn()
        }
        return true
    }
}

// MARK: - CreateGroupAlertVCDelegate

extension TemporaryGroupVC: CreateGroupAlertVCDelegate {
    
    func createGroupAlertVCDidDismiss() {
        let vc = CreateGroupVC(studentIDAry: dataDictionary["studentID"]!)
        vc.modalPresentationStyle = .custom
        scheduleVC?.present(vc, animated: true, completion: nil)
    }
}
