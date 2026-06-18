//
//  UICollectionView+ElementKindSection.swift
//  CyxbsMobile2019_iOS
//
//  抽取自原 Curriculum/Schedule/ViewController/RYScheduleCollectionViewLayout.swift
//  RY 旧课表已删除，但 WeDate 等模块仍依赖这个项目内自定义的 supplementary kind 命名空间，
//  因此抽到通用扩展位置继续提供。
//

import UIKit

extension UICollectionView {

    public enum ElementKindSection: String {

        case header = "Redrock.UICollectionView.ElementKindSection.header"

        case leading = "Redrock.UICollectionView.ElementKindSection.leading"

        case placeHolder = "Redrock.UICollectionView.ElementKindSection.placeHolder"

        case pointHolder = "Redrock.UICollectionView.ElementKindSection.pointHolder"
    }

    public func register(
        _ viewClass: AnyClass?,
        forElementKindSection elementKind: ElementKindSection,
        withReuseIdentifier identifier: String
    ) {
        register(viewClass, forSupplementaryViewOfKind: elementKind.rawValue, withReuseIdentifier: identifier)
    }
}
