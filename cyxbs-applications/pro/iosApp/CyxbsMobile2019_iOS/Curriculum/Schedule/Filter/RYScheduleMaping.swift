//
//  RYScheduleMaping.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/12.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation

// MARK: ~.Priority

extension RYScheduleMaping {
    
    enum Priority: Int, Hashable{
        
        case mainly = 0
        
        case custom = 1
        
        case others = 2
        
        static func < (lhs: RYScheduleMaping.Priority, rhs: RYScheduleMaping.Priority) -> Bool {
            lhs.rawValue < rhs.rawValue
        }
    }
}

// MARK: ~.Collection

extension RYScheduleMaping {
    
    struct Collection: Equatable {
        
        let cal: ScheduleCalModel
        
        let location: Int
        
        var lenth: Int = 1
        
        let priority: Priority
        
        var count: Int = 1
        
        init(cal: ScheduleCalModel, location: Int, priority: Priority) {
            self.cal = cal
            self.location = location
            self.priority = priority
        }
        
        static func == (lhs: RYScheduleMaping.Collection, rhs: RYScheduleMaping.Collection) -> Bool {
            lhs.cal === rhs.cal
        }
    }
}

// MARK: ScheduleMaping

class RYScheduleMaping {
    
    var name: String? = nil
    
    var start: Date? = nil
    
    var nowWeek: Int = 0
    
    // if you don't want to have a diffirent views, set it to false
    var checkPriority: Bool = true
    
    // {2021215154, .system} -> .mainly
    private(set) var scheduleModelMap: [ScheduleModel: Priority] = [:]
    
    // (section, week) -> {ScheduleCalModel, ScheduleCalModel, nil, ...}
    private var mapTable: [IndexPath: [Collection?]] = [:]
    
    private var oldValues: [Collection] = []
    
    // the final data to show on view
    private var finalData: [[Collection]] = [[]]
    var datas: [[Collection]] {
        if !didFinished { finish() }
        return finalData
    }
    
    // if didFinished, mapTable is available
    private var didFinished: Bool = false
}

extension RYScheduleMaping {
    
    func findCals(from collection: Collection) -> [ScheduleCalModel] {
        var cals = [collection.cal]
        for oldValue in self.oldValues {
            if oldValue.cal.inSection == collection.cal.inSection,
            oldValue.cal.curriculum.inWeek == collection.cal.curriculum.inWeek,
            (oldValue.location ..< (oldValue.location + oldValue.lenth)).contains(collection.location) {
                cals.append(oldValue.cal)
            }
        }
        return cals
    }
}

// MARK: mapping

extension RYScheduleMaping {
    
    func maping(_ model: ScheduleModel, prepare cals: [ScheduleCalModel]? = nil, priority: Priority = .mainly) {
        // 线程安全检查（可选）
        #if DEBUG
        if !Thread.isMainThread {
            print("RYScheduleMaping建议在主线程调用，当前线程: \(Thread.current)")
        }
        #endif
        
        if model.customType == .system {
            start = model.start
            nowWeek = model.nowWeek
        }
        
        guard scheduleModelMap[model] == nil else { return }
        
        didFinished = false
        scheduleModelMap[model] = priority
        
        // 安全获取cals数组
        let safeCals = cals ?? model.calModels
        
        // 安全检查数组
        guard !safeCals.isEmpty else {
            print("课程数据为空数组，model: \(model)")
            return
        }
        
        // 安全遍历和处理
        for (index, cal) in safeCals.enumerated() {
            // 验证cal数据完整性
            guard !cal.curriculum.period.isEmpty else {
                print("跳过第\(index)个课程: 周期为空")
                continue
            }
            
            // 验证section和week的有效性
            guard cal.inSection >= 0 && cal.curriculum.inWeek >= 0 else {
                print("跳过第\(index)个课程: 无效的section(\(cal.inSection))或week(\(cal.curriculum.inWeek))")
                continue
            }
            
            for idx in cal.curriculum.period {
                // 验证idx有效性
                guard idx >= 0 else {
                    print("跳过无效索引: \(idx), 课程位置: \(index)")
                    continue
                }
                
                // 防止location值过大
                guard idx < 50 else {
                    print("课程索引过大: \(idx), 课程位置: \(index)")
                    continue
                }
                
                let pointCal = Collection(cal: cal, location: idx, priority: priority)
                map(pCal: pointCal)
            }
        }
    }
    
    private func map(pCal: Collection) {
        // 输入验证
        guard pCal.location >= 0 else {
            print("无效的location: \(pCal.location)")
            return
        }
        
        guard pCal.cal.inSection >= 0 && pCal.cal.curriculum.inWeek >= 0 else {
            print("无效的section(\(pCal.cal.inSection))或week(\(pCal.cal.curriculum.inWeek))")
            return
        }
        
        let indexPath = IndexPath(indexes: [pCal.cal.inSection, pCal.cal.curriculum.inWeek])
        var ary = mapTable[indexPath] ?? []
        
        // 安全扩展数组
        if ary.count <= pCal.location {
            // 限制最大扩展数量，防止内存爆炸
            let maxReasonableSize = 50
            guard pCal.location < maxReasonableSize else {
                print("location值过大: \(pCal.location)")
                return
            }
            
            for _ in ary.count ... pCal.location {
                ary.append(nil)
            }
        }
        
        // 原有逻辑保持不变
        if !checkPriority {
            abandon_the_old_for_the_new()
            return
        }
        
        guard let old = ary[pCal.location] else {
            abandon_the_old_for_the_new()
            return
        }
        
        if pCal.priority < old.priority {
            abandon_the_old_for_the_new(old: old)
            return
        }
        
        if pCal.priority == old.priority {
            if pCal.cal.curriculum.period.count >= old.cal.curriculum.period.count {
                abandon_the_old_for_the_new(old: old)
                return
            }
        }
        
        firm_and_unshakable(old: &ary[pCal.location]!)
        
        func abandon_the_old_for_the_new(old: Collection? = nil) {
            var new = pCal
            if let old {
                oldValues.append(old)
                new.count += 1
            }
            ary[pCal.location] = new
            mapTable[indexPath] = ary
        }
        
        func firm_and_unshakable(old: inout Collection) {
            oldValues.append(pCal)
            old.count += 1
        }
    }
}

// MARK: delete

extension RYScheduleMaping {
    
    func delete(pCal: Collection) {
        didFinished = false
        var pCal = pCal
        pCal.count = 1
        let indexPath = IndexPath(indexes: [pCal.cal.inSection, pCal.cal.curriculum.inWeek])
        guard var ary = mapTable[indexPath] else { return }
        ary[pCal.location] = nil
        
        for i in 0 ..< oldValues.count {
            let oldValue = oldValues[i]
            if oldValue.cal.inSection == pCal.cal.inSection &&
                oldValue.cal.curriculum.inWeek == pCal.cal.curriculum.inWeek &&
                oldValue.location == pCal.location {
                
                oldValues.remove(at: i)
                map(pCal: oldValue)
            }
        }
    }
}

// MARK: finish

extension RYScheduleMaping {
    
    // finished mapTable to finalData
    func finish() {
        if didFinished { return }
        didFinished = true
        finalData = [[]]
        
        for each in mapTable {
            if finalData.count <= each.key[0] {
                for _ in finalData.count ... each.key[0] {
                    finalData.append([])
                }
            }
            
            if each.value.count <= 1 { continue }
            
            var oldValue = each.value[0]
            
            for newIndex in 1 ..< each.value.count {
                let newValue = each.value[newIndex]
                if oldValue != newValue {
                    if let newValue {
                        var collection = Collection(cal: newValue.cal, location: newIndex, priority: newValue.priority)
                        collection.count = newValue.count
                        finalData[each.key[0]].append(collection)
                    }
                } else {
                    if newValue != nil, finalData[each.key[0]].count > 0 {
                        finalData[each.key[0]][finalData[each.key[0]].count - 1].lenth += 1
                    }
                }
                oldValue = newValue
            }
        }
    }
}

// MARK: clean

extension RYScheduleMaping {
    
    func clean() {
        scheduleModelMap = [:]
        mapTable = [:]
        oldValues = []
        finalData = [[]]
        didFinished = false
    }
}

// MARK: Validate

extension RYScheduleMaping {
    func validateScheduleData(_ model: ScheduleModel) -> (isValid: Bool, issues: [String]) {
        var issues: [String] = []
        
        let cals = model.calModels
        
        if cals.isEmpty {
            issues.append("课程数据为空")
        }
        
        for (index, cal) in cals.enumerated() {
            if cal.inSection < 0 {
                issues.append("第\(index)个课程section为负数: \(cal.inSection)")
            }
            
            if cal.curriculum.inWeek < 0 {
                issues.append("第\(index)个课程week为负数: \(cal.curriculum.inWeek)")
            }
            
            if cal.curriculum.period.isEmpty {
                issues.append("第\(index)个课程周期为空")
            }
            
            for period in cal.curriculum.period {
                if period < 0 {
                    issues.append("第\(index)个课程有负数的周期: \(period)")
                }
                if period >= 50 {
                    issues.append("第\(index)个课程周期值过大: \(period)")
                }
            }
        }
        
        return (issues.isEmpty, issues)
    }
}
