//
//  OptionButtonView.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/23.
//  Copyright © 2025 Redrock. All rights reserved.
//

import UIKit

// 委托协议，用于将选择结果传递回父视图控制器
protocol OptionButtonsViewDelegate: AnyObject {
    func optionSelected(_ option: String)
}

class OptionButtonsView: UIView {
    
    // 委托属性
    weak var delegate: OptionButtonsViewDelegate?
    
    // 使用闭包作为另一种传递数据的方式
    var onOptionSelected: ((String) -> Void)?
    
    // 选项按钮数组
    private var optionButtons: [UIButton] = []
    
    // 当前选中的按钮索引
    private var selectedIndex: Int? = nil
    
    // 选项数据
    var options: [(display: String, value: String)] = [
        ("选项A", "value_a"),
        ("选项B", "value_b"),
        ("选项C", "value_c"),
        ("选项D", "value_d")
    ]{
        didSet {
            setupButtons()
        }
    }
    
    // 自定义外观属性
    var normalColor: UIColor = UIColor.ry(light: "#E8F0FC", dark: "#5A5A5A") {
        didSet {
            updateButtonAppearance()
        }
    }
    
    var selectedColor: UIColor = UIColor(hexString: "#D4DAFF") {
        didSet {
            updateButtonAppearance()
        }
    }
    
    var textColor: UIColor = UIColor.ry(light: "#93A4BB", dark: "#D2D2D2") {
        didSet {
            updateButtonAppearance()
        }
    }
    
    var selectedTextColor: UIColor = UIColor(hexString: "#4A44E4") {
        didSet {
            updateButtonAppearance()
        }
    }
    
    let buttonWidth: CGFloat = 74
    let buttonHeight: CGFloat = 28
    
    var cornerRadius: CGFloat = 14 {
        didSet {
            updateButtonAppearance()
        }
    }
    
    // 初始化方法
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupButtons()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupButtons()
    }
    
    // 设置按钮
    private func setupButtons() {
        // 移除现有的按钮
        optionButtons.forEach { $0.removeFromSuperview() }
        optionButtons.removeAll()
        
        // 创建新按钮
        for (index, option) in options.enumerated() {
            let button = UIButton()
            button.setTitle(option.display, for: .normal)
            button.titleLabel?.font = UIFont(name: PingFangSC, size: 14)
            button.backgroundColor = normalColor
            button.setTitleColor(textColor, for: .normal)
            button.layer.cornerRadius = cornerRadius
            button.tag = index
            button.addTarget(self, action: #selector(optionSelected(_:)), for: .touchUpInside)
            
            addSubview(button)
            optionButtons.append(button)
        }
        
        setupConstraints()
    }
    
    // 设置布局约束
    private func setupConstraints() {
        // 移除所有现有约束
        removeConstraints(constraints)
        
        // 如果没有按钮，直接返回
        guard !optionButtons.isEmpty else { return }
        
        // 启用自动布局
        translatesAutoresizingMaskIntoConstraints = false
        
        // 创建水平排列的约束
        var previousButton: UIButton?
        
        for button in optionButtons {
            button.translatesAutoresizingMaskIntoConstraints = false
            
            // 高度约束
            button.heightAnchor.constraint(equalToConstant: buttonHeight).isActive = true
            button.widthAnchor.constraint(equalToConstant: buttonWidth).isActive = true
            
            // 垂直居中
            button.centerYAnchor.constraint(equalTo: centerYAnchor).isActive = true
            
            if let previous = previousButton {
                // 后续按钮与前一个按钮间隔
                button.leadingAnchor.constraint(equalTo: previous.trailingAnchor, constant: 20).isActive = true
            } else {
                // 第一个按钮靠左
                button.leadingAnchor.constraint(equalTo: leadingAnchor).isActive = true
            }
            
            previousButton = button
        }
        
        // 最后一个按钮靠右
        if let lastButton = optionButtons.last {
            lastButton.trailingAnchor.constraint(equalTo: trailingAnchor).isActive = true
        }
        
        // 设置自身高度约束
        heightAnchor.constraint(equalToConstant: buttonHeight).isActive = true
    }
    
    // 按钮点击事件
    @objc private func optionSelected(_ sender: UIButton) {
        let selectedTag = sender.tag
        let selectedValue = options[selectedTag].value
        
        // 如果点击已选中的按钮，则取消选中
        if selectedIndex == selectedTag {
            deselectButton(at: selectedTag)
            selectedIndex = nil
            return
        }
        
        // 取消之前选中的按钮
        if let previousSelected = selectedIndex {
            deselectButton(at: previousSelected)
        }
        
        // 选中新按钮
        selectButton(at: selectedTag)
        selectedIndex = selectedTag
        
        // 通过委托传递数据
        delegate?.optionSelected(selectedValue)
        
        // 通过闭包传递数据
        onOptionSelected?(selectedValue)
    }
    
    // 选中按钮的视觉反馈
    private func selectButton(at index: Int) {
        let button = optionButtons[index]
        
        UIView.animate(withDuration: 0.2) {
            button.backgroundColor = self.selectedColor
            button.setTitleColor(self.selectedTextColor, for: .normal)
            button.layer.borderColor = self.selectedColor.cgColor
            button.transform = CGAffineTransform(scaleX: 1.05, y: 1.05)
        }
    }
    
    // 取消选中按钮的视觉反馈
    private func deselectButton(at index: Int) {
        let button = optionButtons[index]
        
        UIView.animate(withDuration: 0.2) {
            button.backgroundColor = self.normalColor
            button.setTitleColor(self.textColor, for: .normal)
            button.layer.borderColor = UIColor.systemGray4.cgColor
            button.transform = .identity
        }
    }
    
    // 更新按钮外观
    private func updateButtonAppearance() {
        for (index, button) in optionButtons.enumerated() {
            if index == selectedIndex {
                button.backgroundColor = selectedColor
                button.setTitleColor(selectedTextColor, for: .normal)
                button.layer.borderColor = selectedColor.cgColor
            } else {
                button.backgroundColor = normalColor
                button.setTitleColor(textColor, for: .normal)
                button.layer.borderColor = UIColor.systemGray4.cgColor
            }
            button.layer.cornerRadius = cornerRadius
        }
    }
    
    // 外部方法：重置选择
    func resetSelection() {
        if let selectedIndex = selectedIndex {
            deselectButton(at: selectedIndex)
            self.selectedIndex = nil
        }
    }
    
    // 外部方法：获取当前选中的实际值
    func getSelectedValue() -> String? {
        guard let selectedIndex = selectedIndex else { return nil }
        return options[selectedIndex].value
    }
    
    // 外部方法：获取当前选中的显示文本
    func getSelectedDisplayText() -> String? {
        guard let selectedIndex = selectedIndex else { return nil }
        return options[selectedIndex].display
    }
    
    // 布局更新时重新设置约束
    override func layoutSubviews() {
        super.layoutSubviews()
        setupConstraints()
    }
}
