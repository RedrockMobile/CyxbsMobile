//
//  SearchResultVC.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/8/13.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

protocol SearchResultVCDelegate: AnyObject {
    func updateStudentData(name: [String], number: [String])
}

class SearchResultVC: UIViewController {
    
    weak var delegate: SearchResultVCDelegate?
    
    /// 搜索信息为何种类型
    private var types: [String] = []
    /// 学生信息数组
    private var studentAry: [StudentResultItem] = []
    /// 班级信息数组
    private var classAry: [ClassResultItem] = []
    /// 分组信息数组
    private var groupAry: [GroupResultItem] = []
    /// 已选学生学号数组
    private var studentIDAry: [String] = []
    /// 已选学生姓名数组
    private var studentNameAry: [String] = []
    
    // MARK: - Life Cycle
    
    init(types: [String], studentAry: [StudentResultItem], classAry: [ClassResultItem], groupAry: [GroupResultItem]) {
        super.init(nibName: nil, bundle: nil)
        self.types = types
        self.studentAry = studentAry
        self.classAry = classAry
        self.groupAry = groupAry
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func loadView() {
        super.loadView()
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        var top = 0.0
        var height = 0.0
        if types == ["分组"] {
            top = SCREEN_HEIGHT * 0.55
            height = SCREEN_HEIGHT * 0.45
        } else {
            top = SCREEN_HEIGHT * 0.7
            height = SCREEN_HEIGHT * 0.3
        }
        let containerView = UIView(frame: CGRect(x: 0, y: top, width: SCREEN_WIDTH, height: height))
        containerView.backgroundColor = .weDatePanelBackground
        containerView.addSubview(cancelBtn)
        containerView.addSubview(tableView)
        view.addSubview(containerView)
        // 左上和右上添加圆角
        let maskPath = UIBezierPath(roundedRect: containerView.bounds, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 12, height: 12))
        let maskLayer = CAShapeLayer()
        maskLayer.path = maskPath.cgPath
        containerView.layer.mask = maskLayer
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        UIView.animate(withDuration: 0.1) {
            self.view.backgroundColor = UIColor(hexString: "000000", alpha: 0.47)
        }
    }
        
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        view.backgroundColor = .clear
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        
        cancelBtn.frame = CGRect(x: SCREEN_WIDTH - 16 - 25, y: 16, width: 25, height: 17)
        tableView.snp.makeConstraints { make in
            make.left.right.bottom.equalToSuperview()
            make.top.equalTo(cancelBtn.snp.bottom).offset(7)
        }
    }
    
    // MARK: - Method

    @objc private func clickCancelBtn() {
        self.dismiss(animated: true, completion: nil)
    }
    
    @objc private func clickGroupHeaderBtn() {
        for student in groupAry[0].members {
            studentIDAry.append(student.studentID)
            studentNameAry.append(student.name)
        }
        groupAry.remove(at: 0)
        delegate?.updateStudentData(name: studentNameAry, number: studentIDAry)
        self.dismiss(animated: true, completion: nil)
    }
    
    // MARK: - Lazy
    
    /// 取消按钮
    private lazy var cancelBtn: UIButton = {
        let cancelBtn = UIButton(type: .system)
        cancelBtn.setTitle("取消", for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 12)
        cancelBtn.setTitleColor(.weDateLightMutedText, for: .normal)
        cancelBtn.addTarget(self, action: #selector(clickCancelBtn), for: .touchUpInside)
        return cancelBtn
    }()
    /// 搜索结果展示
    private lazy var tableView: UITableView = {
        let tableView = UITableView(frame: .zero, style: .plain)
        tableView.estimatedRowHeight = 0
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.separatorStyle = .none
        tableView.showsVerticalScrollIndicator = true
        tableView.backgroundColor = .weDatePanelBackground
        tableView.dataSource = self
        tableView.delegate = self
        if #available(iOS 15.0, *) {
            tableView.sectionHeaderTopPadding = 0.0
        }
        // cell
        tableView.register(StudentSearchResultTableViewCell.self, forCellReuseIdentifier: StudentSearchResultTableViewCellReuseIdentifier)
        tableView.register(ClassSearchResultTableViewCell.self, forCellReuseIdentifier: ClassSearchResultTableViewCellReuseIdentifier)
        tableView.register(GroupSearchResultTableViewCell.self, forCellReuseIdentifier: GroupSearchResultTableViewCellReuseIdentifier)
        // header
        tableView.register(SearchResultTableViewHeaderView.self, forHeaderFooterViewReuseIdentifier: SearchResultTableViewHeaderViewReuseIdentifier)
        return tableView
    }()
}

// MARK: - UITableViewDataSource

extension SearchResultVC: UITableViewDataSource {
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        var count: Int
        switch types {
        case ["学生"]: count = studentAry.count
        case ["班级"]: count = classAry.count
        case ["分组"]: count = groupAry[0].members.count
        case ["学生", "分组"]: count = studentAry.count + groupAry.count
        default: count = 0
        }
        return count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        
        if types == ["学生"] {
            let cell = tableView.dequeueReusableCell(withIdentifier: StudentSearchResultTableViewCellReuseIdentifier, for: indexPath) as! StudentSearchResultTableViewCell
            cell.selectionStyle = .none
            cell.nameLab.text = studentAry[indexPath.row].name
            cell.studentIDLab.text = studentAry[indexPath.row].studentID
            cell.departLab.text = studentAry[indexPath.row].depart
            cell.needPicture = false
            cell.delegate = self
            cell.addBtn.tag = indexPath.row
            return cell

        } else if types == ["班级"] {
            let cell = tableView.dequeueReusableCell(withIdentifier: ClassSearchResultTableViewCellReuseIdentifier, for: indexPath) as! ClassSearchResultTableViewCell
            cell.selectionStyle = .none
            cell.classIDLab.text = classAry[indexPath.row].classID
            cell.addBtn.tag = indexPath.row
            cell.delegate = self
            cell.addBtn.tag = indexPath.row
            return cell

        } else if types == ["分组"] {
            let cell = tableView.dequeueReusableCell(withIdentifier: StudentSearchResultTableViewCellReuseIdentifier, for: indexPath) as! StudentSearchResultTableViewCell
            cell.selectionStyle = .none
            cell.nameLab.text = groupAry[0].members[indexPath.row].name
            cell.studentIDLab.text = groupAry[0].members[indexPath.row].studentID
            cell.departLab.text = groupAry[0].members[indexPath.row].depart
            cell.needPicture = false
            cell.delegate = self
            cell.addBtn.tag = indexPath.row
            return cell

        } else if types == ["学生", "分组"] {
            if indexPath.row < groupAry.count {
                let cell = tableView.dequeueReusableCell(withIdentifier: GroupSearchResultTableViewCellReuseIdentifier, for: indexPath) as! GroupSearchResultTableViewCell
                cell.selectionStyle = .none
                cell.nameLab.text = groupAry[indexPath.row].name
                cell.delegate = self
                cell.addBtn.tag = indexPath.row
                return cell
            } else {
                let cell = tableView.dequeueReusableCell(withIdentifier: StudentSearchResultTableViewCellReuseIdentifier, for: indexPath) as! StudentSearchResultTableViewCell
                cell.selectionStyle = .none
                cell.nameLab.text = studentAry[indexPath.row - groupAry.count].name
                cell.studentIDLab.text = studentAry[indexPath.row - groupAry.count].studentID
                cell.departLab.text = studentAry[indexPath.row - groupAry.count].depart
                cell.needPicture = true
                cell.delegate = self
                cell.addBtn.tag = indexPath.row
                return cell
            }
        }
        return UITableViewCell()
    }
}

// MARK: - UITableViewDelegate

extension SearchResultVC: UITableViewDelegate {
    
    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        var height = 0.0
        if types == ["分组"] {
            height = 63
        }
        return height
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 56
    }

    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        if types == ["分组"] {
            let headerView = tableView.dequeueReusableHeaderFooterView(withIdentifier: SearchResultTableViewHeaderViewReuseIdentifier) as! SearchResultTableViewHeaderView
            headerView.nameLab.text = groupAry[0].name
            headerView.addBtn.addTarget(self, action: #selector(clickGroupHeaderBtn), for: .touchUpInside)
            return headerView
        } else {
            return nil
        }
    }
    
    func tableView(_ tableView: UITableView, viewForFooterInSection section: Int) -> UIView? {
        return UIView(frame: .zero)
    }
    
}

// MARK: - SearchResultTableViewCellDelegate

extension SearchResultVC: SearchResultTableViewCellDelegate {
    
    func addDataAt(_ index: Int) {
        if types == ["学生"] {
            let item = studentAry[index]
            studentAry.remove(at: index)
            studentIDAry.append(item.studentID)
            studentNameAry.append(item.name)
            delegate?.updateStudentData(name: studentNameAry, number: studentIDAry)
            self.dismiss(animated: true, completion: nil)
        } else if types == ["班级"] {
            for student in classAry[0].members {
                studentIDAry.append(student.studentID)
                studentNameAry.append(student.name)
            }
            classAry.remove(at: index)
            delegate?.updateStudentData(name: studentNameAry, number: studentIDAry)
            self.dismiss(animated: true, completion: nil)
        } else if types == ["分组"] {
            let item = groupAry[0].members[index]
            groupAry[0].members.remove(at: index)
            studentIDAry.append(item.studentID)
            studentNameAry.append(item.name)
            delegate?.updateStudentData(name: studentNameAry, number: studentIDAry)
            if groupAry[0].members.count == 0 {
                self.dismiss(animated: true, completion: nil)
            }
            
        } else if types == ["学生", "分组"] {
            // 分组cell
            if index < groupAry.count {
                for student in groupAry[0].members {
                    studentIDAry.append(student.studentID)
                    studentNameAry.append(student.name)
                }
                groupAry.remove(at: index)
            // 学生cell
            } else {
                let item = studentAry[index - groupAry.count]
                studentAry.remove(at: index - groupAry.count)
                studentIDAry.append(item.studentID)
                studentNameAry.append(item.name)
            }
            delegate?.updateStudentData(name: studentNameAry, number: studentIDAry)
            if studentAry.count + groupAry.count == 0 {
                self.dismiss(animated: true, completion: nil)
            }
        }
        tableView.reloadData()
    }
}
