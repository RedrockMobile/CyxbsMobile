//
//  ScheduleCollectionViewLayout.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/17.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

// MARK: ScheduleCollectionViewLayoutDataSource

@objc public
protocol ScheduleCollectionViewLayoutDataSource {
    
    func collectionView(_ collectionView: UICollectionView, layout: ScheduleCollectionViewLayout, columnOfItemAtIndexPath indexPath: IndexPath) -> Int
    
    func collectionView(_ collectionView: UICollectionView, layout: ScheduleCollectionViewLayout, lineOfItemAtIndexPath indexPath: IndexPath) -> Int
    
    func collectionView(_ collectionView: UICollectionView, layout: ScheduleCollectionViewLayout, lenthOfItemAtIndexPath indexPath: IndexPath) -> Int
    
    func collectionView(_ collectionView: UICollectionView, layout: ScheduleCollectionViewLayout, numberOfSupplementaryOfKind kind: String, inSection section: Int) -> Int
    
    func collectionView(_ collectionView: UICollectionView, layout: ScheduleCollectionViewLayout, persentOfPointAtIndexPath indexPath: IndexPath) -> CGFloat
}

@objc public
protocol ScheduleCollectionViewLayoutDelegate {
    func pageWillScrollTo(page: Int)
}

// MARK: ScheduleCollectionViewLayout

open class ScheduleCollectionViewLayout: UICollectionViewLayout {
    
    // MARK: Property
    
    open weak var dataSource: ScheduleCollectionViewLayoutDataSource?
    
    open weak var delegate: ScheduleCollectionViewLayoutDelegate?
    
    open var lineSpacing: CGFloat = 2
    
    open var columnSpacing: CGFloat = 2
    
    open var widthForLeadingSupplementaryView: CGFloat = 30
    
    private(set) var heightForHeaderSupplementaryView: CGFloat = 0

    open var aspectRatio: CGFloat = 46.0 / 50.0
    
    open var heightForHeader: CGFloat = 50
    
    open var pageCalculation: Int = 0
    
    public var numberOfPages: Int = 0
    
    private(set) var pageWillShow: Int = 0 {
        didSet {
            delegate?.pageWillScrollTo(page: pageWillShow)
        }
    }
    
    private(set) open var itemSize: CGSize = .zero
    
    open var pageShows: Int = 1 {
        didSet {
            if pageShows < 1 {
                pageShows = 1
            }
        }
    }
    
    // MARK: Private
    
    private var itemAttributes: [IndexPath: UICollectionViewLayoutAttributes] = [:]
    
    private var supplementaryAttributes: [String: [IndexPath: UICollectionViewLayoutAttributes]] = [:]
    
    // MARK: Method - middle
    
    open func register(_ viewClass: AnyClass?, forSupplementaryViewOfKind elementKind: String) {
        if supplementaryAttributes[elementKind] == nil {
            supplementaryAttributes[elementKind] = [:]
        }
    }
    
    // MARK: Method - layoutAttributes
    
    open override func layoutAttributesForElements(in rect: CGRect) -> [UICollectionViewLayoutAttributes]? {
        let numberOfSections = collectionView?.dataSource?.numberOfSections?(in: collectionView!) ?? 0
        if numberOfSections <= 0 { return nil }
        
        var result = [UICollectionViewLayoutAttributes]()
        for section in 0 ..< numberOfSections {
            for elementKind in supplementaryAttributes.keys {
                let numberOfSupplementaryOfKind = dataSource?.collectionView(collectionView!, layout: self, numberOfSupplementaryOfKind: elementKind, inSection: section) ?? 0
                if numberOfSupplementaryOfKind <= 0 { continue }
                for item in 0..<numberOfSupplementaryOfKind {
                    let indexPath = IndexPath(item: item, section: section)
                    if let attributes = layoutAttributesForSupplementaryView(ofKind: elementKind, at: indexPath) {
                        if rect.intersects(attributes.frame) {
                            result.append(attributes)
                        }
                    }
                }
            }
            let itemCount = collectionView?.dataSource?.collectionView(collectionView!, numberOfItemsInSection: section) ?? 0
            if itemCount <= 0 { continue }
            for item in 0 ..< itemCount {
                let indexPath = IndexPath(item: item, section: section)
                if let attributes = layoutAttributesForItem(at: indexPath) {
                    if rect.intersects(attributes.frame) {
                        result.append(attributes)
                    }
                }
            }
        }
        return result
    }
    
    open override func layoutAttributesForItem(at indexPath: IndexPath) -> UICollectionViewLayoutAttributes? {
        guard let dataSource = dataSource else { return nil }
        let columnOfItem = dataSource.collectionView(collectionView!, layout: self, columnOfItemAtIndexPath: indexPath)
        let lineOfItem = dataSource.collectionView(collectionView!, layout: self, lineOfItemAtIndexPath: indexPath)
        let lenthOfItem = dataSource.collectionView(collectionView!, layout: self, lenthOfItemAtIndexPath: indexPath)
        
        let attribute = itemAttributes[indexPath] ?? UICollectionViewLayoutAttributes(forCellWith: indexPath)
        attribute.frame = CGRect(
            x: CGFloat(indexPath.section) * collectionView!.bounds.width + widthForLeadingSupplementaryView + CGFloat(columnOfItem - 1) * (itemSize.width + columnSpacing),
            y: heightForHeaderSupplementaryView + CGFloat(lineOfItem - 1) * (itemSize.height + lineSpacing) + lineSpacing,
            width: itemSize.width,
            height: CGFloat(lenthOfItem) * itemSize.height + CGFloat(lenthOfItem - 1) * columnSpacing)
        itemAttributes[indexPath] = attribute
        
        return attribute
    }
    
    open override func layoutAttributesForSupplementaryView(ofKind elementKind: String, at indexPath: IndexPath) -> UICollectionViewLayoutAttributes? {
        guard let elementKindSection = UICollectionView.ElementKindSection(rawValue: elementKind) else { return nil }
        guard let elementKindAttributes = supplementaryAttributes[elementKind] else { return nil }
        let attributes = elementKindAttributes[indexPath] ?? UICollectionViewLayoutAttributes(forSupplementaryViewOfKind: elementKind, with: indexPath)
        setupSupplementaryAttributes(attributes, elementKindSection: elementKindSection)
        supplementaryAttributes[elementKind]?[indexPath] = attributes
        return attributes
    }
    
    func setupSupplementaryAttributes(_ attributes :UICollectionViewLayoutAttributes, elementKindSection: UICollectionView.ElementKindSection) {
        
        let indexPath = attributes.indexPath
        let pageWidth = collectionView!.bounds.width / CGFloat(pageShows)
        
        switch elementKindSection {
        case .header:
            attributes.zIndex = 10
            
            if indexPath.item == 0 {
                attributes.frame = CGRect(
                    x: CGFloat(indexPath.section) * pageWidth,
                    y: collectionView!.contentOffset.y,
                    width: widthForLeadingSupplementaryView,
                    height: heightForHeaderSupplementaryView)
            } else {
                attributes.frame = CGRect(
                    x: CGFloat(indexPath.section) * collectionView!.frame.width + widthForLeadingSupplementaryView + CGFloat(indexPath.item - 1) * (columnSpacing + itemSize.width),
                    y: collectionView!.contentOffset.y,
                    width: itemSize.width,
                    height: heightForHeaderSupplementaryView)
            }
            
        case .leading:
            attributes.frame = CGRect(
                x: CGFloat(indexPath.section) * collectionView!.frame.width,
                y: heightForHeaderSupplementaryView + CGFloat(indexPath.item) * (lineSpacing + itemSize.height),
                width: widthForLeadingSupplementaryView,
                height: itemSize.height)
            
        default:
            break
            
//        case .pointHolder:
////            let persent = dataSource?.collectionView(ry_collectionView, layout: self, persentOfPointAtIndexPath: indexPath) ?? 0
//            print("todo")
        }
    }
    
    // MARK: others
    
    open override func prepare() {
        calculateLayoutIfNeeded()
    }
    
    open override func shouldInvalidateLayout(forBoundsChange newBounds: CGRect) -> Bool {
        true
    }
    
    open override func invalidateLayout(with context: UICollectionViewLayoutInvalidationContext) {
        if context.invalidateDataSourceCounts {
            itemAttributes.removeAll(keepingCapacity: true)
            for key in supplementaryAttributes {
                supplementaryAttributes[key.key]?.removeAll(keepingCapacity: true)
            }
        }
        supplementaryAttributes[UICollectionView.ElementKindSection.header.rawValue]?.forEach { entry in
            self.setupSupplementaryAttributes(entry.value, elementKindSection: .header)
        }
    }
    
    open override var collectionViewContentSize: CGSize {
        let itemCount = 13
        let sections = collectionView?.dataSource?.numberOfSections?(in: collectionView!) ?? 0
        return CGSize(
            width: CGFloat(sections) * collectionView!.bounds.width,
            height: heightForHeaderSupplementaryView + CGFloat(itemCount) * (itemSize.height + lineSpacing))
    }
    
    open override func targetContentOffset(forProposedContentOffset proposedContentOffset: CGPoint, withScrollingVelocity velocity: CGPoint) -> CGPoint {
        
        // 加锁
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }
        
        let toTime = Int(collectionView!.contentOffset.y / (itemSize.height + lineSpacing) + 0.5)
        let toY = (itemSize.height + lineSpacing) * CGFloat(toTime)
        
        var index = Int(proposedContentOffset.x / collectionView!.bounds.width + 0.5)
        let remainder = proposedContentOffset.x - CGFloat(index) * collectionView!.bounds.width
        
        if velocity.x > 0.6 || (velocity.x > 0.3 && remainder > collectionView!.bounds.width / 3) {
            index += 1
        }
        if velocity.x < -0.6 || (velocity.x < -0.3 && remainder < collectionView!.bounds.width / 3) {
            index -= 1
        }
        index = max(index, pageCalculation - 1)
        index = min(index, pageCalculation + 1)
        index = max(index, 0)
        index = min(index, max(numberOfPages - 1, 0))
        
        let toX = collectionView!.bounds.width * CGFloat(index)
        
        if pageWillShow != index {
            pageWillShow = index
        }
        
        return CGPoint(x: toX, y: toY)
    }
    
    // MARK: private
    
    private func calculateLayoutIfNeeded() {
        let width = (collectionView!.bounds.width - widthForLeadingSupplementaryView) / 7 - columnSpacing
        numberOfPages = collectionView?.dataSource?.numberOfSections?(in: collectionView!) ?? 0
        itemSize = CGSize(width: width, height: width / aspectRatio)
        heightForHeaderSupplementaryView = width / aspectRatio
    }
}
