//
//  RYScheduleInteractionFact.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/14.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import MJRefresh
import JXSegmentedView
import RYTransitioningDelegateSwift

class RYScheduleInteractionFact: RYScheduleFact {
    
    weak var viewController: UIViewController?
    
    private(set) var collectionView: UICollectionView!
    
    weak var headerView: RYScheduleHeaderView?
    
    var isCustomEditEnable: Bool = false
    
    var uuidToPriority: [ScheduleModel.UniqueID: RYScheduleMaping.Priority] = [:]
    
    override func createCollectionView() -> UICollectionView {
        let collectionView = super.createCollectionView()
        
        MJRefreshGifHeader {
            self.cleanAndReload()
        }
        .autoChangeTransparency(true)
        .set_refresh_sports()
        .ignoredScrollView(contentInsetTop: -58)
        .link(to: collectionView)
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(collectionViewEmpty(tap:)))
        tap.delegate = self
        collectionView.addGestureRecognizer(tap)
        
        self.collectionView = collectionView
        return collectionView
    }
}

extension RYScheduleInteractionFact {
    
    var currentPage: Int {
        Int(collectionView.contentOffset.x / collectionView.bounds.width / CGFloat(collectionView.ry_layout?.pageShows ?? 1) + 0.5)
    }
    
    func scroll(to section: Int, animated: Bool = false) {
        let visibleSection = min(max(0, section), 23)
        let pageWidth = collectionView.bounds.width / CGFloat(collectionView.ry_layout?.pageShows ?? 1) * CGFloat(visibleSection)
        collectionView.setContentOffset(CGPoint(x: pageWidth, y: collectionView.contentOffset.y), animated: animated)
    }
    
    func scrollToNowWeek() {
        self.scroll(to: self.mappy.nowWeek)
    }
}

// MARK: request

extension RYScheduleInteractionFact {
    
    func cleanAndReload() {
        let priorities = self.mappy.scheduleModelMap.map { $0.value }
        self.mappy.clean()
        self.request(priorities: Set(priorities))
    }
    
    func request(priorities: Set<RYScheduleMaping.Priority>, complition: ((RYScheduleInteractionFact) -> ())? = nil) {
        
        let requestSnos = Set(uuidToPriority.compactMap {
            ($0.value != .custom && priorities.contains($0.value)) ?
            $0.key.sno : nil
        })
        
        var models = [ScheduleModel]()
        
        let que = DispatchQueue(label: "ScheduleModel.request.priorities", qos: .unspecified, attributes: .concurrent)
        
        let semaphore = DispatchSemaphore(value: 0)
        
        que.async {
            
            ScheduleModel.request(snos: requestSnos) { response in
                models += response
                semaphore.signal()
            }
            
            if priorities.contains(.custom) {
                
                ScheduleModel.requestCustom { response in
                    if case .success(let model) = response {
                        models += [model]
                    }
                    semaphore.signal()
                }
                
                semaphore.wait()
            }
            
            semaphore.wait()
        }
        
        que.async(flags: .barrier) {
            for model in models {
                let priority = self.uuidToPriority[model.uuid] ?? .mainly
                self.mappy.maping(model, priority: priority)
            }
            DispatchQueue.main.async {
                let reloadAction = {
                    self.collectionView.mj_header?.transform = .identity
                    self.collectionView.reloadData()
                    complition?(self)
                }
                
                if let header = self.collectionView.mj_header, header.isRefreshing {
                    header.endRefreshing(completionBlock: reloadAction)
                } else {
                    reloadAction()
                }
            }
        }
    }
}

// MARK: handle headerView

extension RYScheduleInteractionFact {
    
    func handle(headerView: RYScheduleHeaderView) {
        headerView.titleTapAction = { _ in
            
            guard let headerView = self.headerView else { return }
            let selectView = RYScheduleSelectSectionHeaderView(frame: headerView.frame)
            selectView.segmentView.defaultSelectedIndex = self.currentPage
            selectView.segmentView.listContainer = self
            selectView.backTapAction = { view in
                UIView.transition(from: view, to: headerView, duration: 0.3, options: .transitionCrossDissolve)
            }
            UIView.transition(from: headerView, to: selectView, duration: 0.3, options: .transitionCrossDissolve)
        }
        headerView.backBtnAction = { _ in
            self.scrollToNowWeek()
        }
        self.headerView = headerView
        self.headerView?.updateData(section: 0, isNowSection: false)
    }
    
    func reloadHeaderView() {
        let page = currentPage
        headerView?.updateData(section: page, isNowSection: (mappy.nowWeek == page))
    }
}

// MARK: interactive

extension RYScheduleInteractionFact {
    
    @objc
    func collectionViewEmpty(tap: UITapGestureRecognizer) {
        presentScheduleEdit(tap: tap)
    }
    
    func presentScheduleDetails(cals: [ScheduleCalModel]) {
        let transisionDelegate = RYTransitioningDelegate()
        transisionDelegate.present = { transition in
            transition.heightForPresented = 260
        }
        
        let vc = ScheduleDetailsViewController(cals: cals)
        vc.delegate = self
        vc.modalPresentationStyle = .custom
        vc.transitioningDelegate = transisionDelegate
        
        viewController?.present(vc, animated: true)
    }
    
    func presentScheduleEdit(tap: UITapGestureRecognizer) {
        guard let layout = collectionView.ry_layout else { return }
        let idxPath = layout.indexPath(at: tap.location(in: collectionView))
        
        let transisionDelegate = RYTransitioningDelegate()
        transisionDelegate.supportedTapOutsideBackWhenPresent = false
        
        let vc = ScheduleEditViewController(sections: [idxPath[0]], inWeek: idxPath[1], location: idxPath[2])
        vc.modalPresentationStyle = .custom
        vc.transitioningDelegate = transisionDelegate
        vc.dismissAction = { aVC in
            self.cleanAndReload()
        }
        
        viewController?.present(vc, animated: true)
    }
}

// MARK: UICollectionViewDelegate

extension RYScheduleInteractionFact {
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let data = data(of: indexPath)
        let cals = mappy.findCals(from: data)
        
        presentScheduleDetails(cals: cals)
    }
}

// MARK: UIScrollViewDelegate

extension RYScheduleInteractionFact {
    
    override func scrollViewDidScroll(_ scrollView: UIScrollView) {
        super.scrollViewDidScroll(scrollView)
        scrollView.mj_header?.frame.origin.x = scrollView.contentOffset.x
        reloadHeaderView()
    }
    
    override func scrollViewWillEndDragging(_ scrollView: UIScrollView, withVelocity velocity: CGPoint, targetContentOffset: UnsafeMutablePointer<CGPoint>) {
        super.scrollViewWillEndDragging(scrollView, withVelocity: velocity, targetContentOffset: targetContentOffset)
        // 当滑动速度过大，且不满足翻页条件时，使用setContentOffset
        let targetPage = Int(targetContentOffset.pointee.x / scrollView.width)
        if targetPage == currentPage && abs(velocity.x) >= 0.2 {
            targetContentOffset.pointee.x = scrollView.contentOffset.x + 50 * velocity.x
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                self.scroll(to: targetPage, animated: true)
            }
        }
    }
}

// MARK: ScheduleDetailTableHeaderViewDelegate

extension RYScheduleInteractionFact: ScheduleDetailCollectionViewCellDelegate {
    
    func collectionViewCell(_ collectionViewCell: ScheduleDetailCollectionViewCell, responseEditBtn: UIButton) {
        
        collectionViewCell.latestViewController?.dismiss(animated: true)
        
        guard let curriculum = collectionViewCell.cal?.curriculum else { return }
        
        let transisionDelegate = RYTransitioningDelegate()
        transisionDelegate.supportedTapOutsideBackWhenPresent = false
        
        let vc = ScheduleEditViewController(curriculum: curriculum)
        vc.modalPresentationStyle = .custom
        vc.transitioningDelegate = transisionDelegate
        vc.dismissAction = { aVC in
            self.cleanAndReload()
        }
        
        viewController?.present(vc, animated: true)
    }
    
    func collectionViewCell(_ collectionViewCell: ScheduleDetailCollectionViewCell, responsePlaceTap: UITapGestureRecognizer) {
        guard let placeName = collectionViewCell.cal?.curriculum.classRoom, !placeName.isEmpty else { return }
        
        let detailViewController = collectionViewCell.latestViewController
        ClassDetailModel.requestPlaceID(withPlaceName: placeName) { [weak self] responseObject in
            guard let self else { return }
            guard let data = responseObject["data"] as? [String: Any] else { return }
            
            let placeID: String
            if let number = data["place_id"] as? NSNumber {
                placeID = number.stringValue
            } else if let string = data["place_id"] as? String {
                placeID = string
            } else {
                return
            }
            
            DispatchQueue.main.async {
                let mapVC = CQUPTMapViewController(initialPlace: placeID)
                mapVC.isPresent = true
                
                let navigationController = UINavigationController(rootViewController: mapVC)
                navigationController.modalPresentationStyle = .formSheet
                mapVC.modalPresentationStyle = .formSheet
                
                let presentMap = {
                    if let viewController = self.viewController {
                        viewController.present(navigationController, animated: true)
                    }
                }
                if let detailViewController {
                    detailViewController.dismiss(animated: true, completion: presentMap)
                } else {
                    presentMap()
                }
            }
        }
    }
}

// MARK: UIGestureRecognizerDelegate

extension RYScheduleInteractionFact: UIGestureRecognizerDelegate {
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        touch.view is UICollectionView
    }
}

// MARK: JXSegmentedViewListContainer

extension RYScheduleInteractionFact: JXSegmentedViewListContainer {
    
    var defaultSelectedIndex: Int {
        get { currentPage }
        set(newValue) {
            scroll(to: newValue, animated: false)
        }
    }
    
    func contentScrollView() -> UIScrollView {
        collectionView
    }
    
    func reloadData() { }
    
    func didClickSelectedItem(at index: Int) {
        scroll(to: index)
    }
}
