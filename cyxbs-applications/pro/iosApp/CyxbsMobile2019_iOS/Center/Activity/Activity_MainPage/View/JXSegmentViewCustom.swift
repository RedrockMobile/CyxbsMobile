//
//  JXSegmentViewCustom.swift
//  CyxbsMobile2019_iOS
//
//  Created by 许晋嘉 on 2023/10/29.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import JXSegmentedView

open class JXSegmentedActivityCustomDataSource: JXSegmentedTitleDataSource {
    
    open var backGroundSelectedColor: UIColor = .clear
    open var backGroundNormalColor: UIColor = .clear
    open var cornerRadius: CGFloat = 0.0
    /// title的颜色是否渐变过渡
    open var isBackGroundColorGradientEnabled: Bool = false
    
    open override func preferredItemModelInstance() -> JXSegmentedBaseItemModel {
        return JXSegmentedActivityCustomItemModel()
    }
    
    open override func preferredRefreshItemModel( _ itemModel: JXSegmentedBaseItemModel, at index: Int, selectedIndex: Int) {
        super.preferredRefreshItemModel(itemModel, at: index, selectedIndex: selectedIndex)

        guard let myItemModel = itemModel as? JXSegmentedActivityCustomItemModel else {
            return
        }

        myItemModel.isBackGroundColorGradientEnabled = isBackGroundColorGradientEnabled
        myItemModel.backGroundNormalColor = backGroundNormalColor
        myItemModel.backGroundSelectedColor = backGroundSelectedColor
        myItemModel.cornerRadius = cornerRadius
        if index == selectedIndex {
            myItemModel.backGroundCurrentColor = backGroundSelectedColor
        }else {
            myItemModel.backGroundCurrentColor = backGroundNormalColor
        }
    }
    
    open override func refreshItemModel(_ segmentedView: JXSegmentedView, leftItemModel: JXSegmentedBaseItemModel, rightItemModel: JXSegmentedBaseItemModel, percent: CGFloat) {
        super.refreshItemModel(segmentedView, leftItemModel: leftItemModel, rightItemModel: rightItemModel, percent: percent)
        
        guard let leftModel = leftItemModel as? JXSegmentedActivityCustomItemModel, let rightModel = rightItemModel as? JXSegmentedActivityCustomItemModel else {
            return
        }
        
        if isBackGroundColorGradientEnabled && isItemTransitionEnabled {
            leftModel.backGroundCurrentColor = JXSegmentedViewTool.interpolateThemeColor(from: leftModel.backGroundSelectedColor, to: leftModel.backGroundNormalColor, percent: percent)
            rightModel.backGroundCurrentColor = JXSegmentedViewTool.interpolateThemeColor(from:rightModel.backGroundNormalColor , to:rightModel.backGroundSelectedColor, percent: percent)
        }
    }
    
    open override func refreshItemModel(_ segmentedView: JXSegmentedView, currentSelectedItemModel: JXSegmentedBaseItemModel, willSelectedItemModel: JXSegmentedBaseItemModel, selectedType: JXSegmentedViewItemSelectedType) {
        super.refreshItemModel(segmentedView, currentSelectedItemModel: currentSelectedItemModel, willSelectedItemModel: willSelectedItemModel, selectedType: selectedType)

        guard let myCurrentSelectedItemModel = currentSelectedItemModel as? JXSegmentedActivityCustomItemModel, let myWillSelectedItemModel = willSelectedItemModel as? JXSegmentedActivityCustomItemModel else {
            return
        }

        myCurrentSelectedItemModel.backGroundCurrentColor = myCurrentSelectedItemModel.backGroundNormalColor

        myWillSelectedItemModel.backGroundCurrentColor = myWillSelectedItemModel.backGroundSelectedColor
    }
    
    //MARK: - JXSegmentedViewDataSource
    open override func registerCellClass(in segmentedView: JXSegmentedView) {
        segmentedView.collectionView.register(JXSegmentedActivityCustomCell.self, forCellWithReuseIdentifier: "cell")
    }

    open override func segmentedView(_ segmentedView: JXSegmentedView, cellForItemAt index: Int) -> JXSegmentedBaseCell {
        let cell = segmentedView.dequeueReusableCell(withReuseIdentifier: "cell", at: index)
        return cell
    }
}

class JXSegmentedActivityCustomItemModel: JXSegmentedTitleItemModel {
    open var isBackGroundColorGradientEnabled: Bool = false
    open var backGroundNormalColor: UIColor = .clear
    open var backGroundSelectedColor: UIColor = .clear
    open var backGroundCurrentColor: UIColor = .clear
    open var cornerRadius: CGFloat = 0.0
}

class JXSegmentedActivityCustomCell: JXSegmentedTitleCell {
    open override func reloadData(itemModel: JXSegmentedBaseItemModel, selectedType: JXSegmentedViewItemSelectedType) {
        super.reloadData(itemModel: itemModel, selectedType: selectedType)

        guard let myItemModel = itemModel as? JXSegmentedActivityCustomItemModel else {
            return
        }

//        contentView.backgroundColor = myItemModel.backGroundNormalColor
        contentView.layer.cornerRadius = myItemModel.cornerRadius

        
        
        if myItemModel.isBackGroundColorGradientEnabled {
            if myItemModel.isTitleMaskEnabled {
                //允许mask，maskTitleLabel在titleLabel上面，maskTitleLabel设置为titleSelectedColor。titleLabel设置为titleNormalColor
                //为了显示效果，使用了双遮罩。即titleMaskLayer遮罩titleLabel，maskTitleMaskLayer遮罩maskTitleLabel
                titleLabel.backgroundColor = myItemModel.backGroundNormalColor
                maskTitleLabel.backgroundColor = myItemModel.backGroundSelectedColor
            }else {
                if myItemModel.isSelectedAnimable && canStartSelectedAnimation(itemModel: itemModel, selectedType: selectedType) {
                    //允许动画且当前是点击的
                    let backGroundColorClosure = preferredBackGroundColorAnimateClosure(itemModel: myItemModel)
                    appendSelectedAnimationClosure(closure: backGroundColorClosure)
                }else {
                    contentView.backgroundColor = myItemModel.backGroundCurrentColor
                }
            }
        }
        
        startSelectedAnimationIfNeeded(itemModel: itemModel, selectedType: selectedType)
        setNeedsLayout()
    }
    
    open func preferredBackGroundColorAnimateClosure(itemModel: JXSegmentedActivityCustomItemModel) -> JXSegmentedCellSelectedAnimationClosure {
        return {[weak self] (percent) in
            if itemModel.isSelected {
                //将要选中，backGroundColor从backGroundNormalColor到backGroundSelectedColor插值渐变
                itemModel.backGroundCurrentColor = JXSegmentedViewTool.interpolateThemeColor(from: itemModel.backGroundNormalColor, to: itemModel.backGroundSelectedColor, percent: percent)
            } else {
                //将要取消选中，backGroundColor从backGroundSelectedColor到backGroundNormalColor插值渐变
                itemModel.backGroundCurrentColor = JXSegmentedViewTool.interpolateThemeColor(from: itemModel.backGroundSelectedColor, to: itemModel.backGroundNormalColor, percent: percent)
            }
            self?.contentView.backgroundColor = itemModel.backGroundCurrentColor
        }
    }
}
