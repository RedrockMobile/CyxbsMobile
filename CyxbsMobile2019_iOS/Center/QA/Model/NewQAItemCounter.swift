//
//  NewQAItemCounter.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/24.
//  Copyright © 2025 Redrock. All rights reserved.
//

import Foundation

class NewQAItemCounter {
    
    let qaModel = QAModel()
    
    var counts: [(category: String, value: Int)] = [
        ("新生", 0),
        ("生活", 0),
        ("学习", 0),
        ("其他", 0)
    ]
    
    var qaObjects: [QAObject] = []
    
    /// 初始化最新请求时间
    @available(iOS 15, *)
    func initDate() {
        let currentDate = Date.now
        UserDefaultsManager.shared.latestRequestQA = currentDate
        print("初始化最新请求时间: \(currentDate)")
    }
    
    /// 请求数据并处理新项目计数
    func requestData() {
        // 重置计数
        resetCounts()
        
        // 检查是否有存储的最新请求时间
        guard let latestRequestDate = UserDefaultsManager.shared.latestRequestQA else {
            // 如果没有存储时间，初始化并返回
            if #available(iOS 15, *) {
                initDate()
            } else {
                // Fallback on earlier versions
                let currentDate = Date()
                UserDefaultsManager.shared.latestRequestQA = currentDate
                print("初始化最新请求时间: \(currentDate)")
            }
            return
        }
        
        qaModel.requestQACenterObjects(QATag: "") { [weak self] qa in
            guard let self = self else { return }
            self.qaObjects = qa
            self.filterAndCountNewItems(since: latestRequestDate)
            self.updateLatestRequestDate()
        } failure: { error in
            print("Network Error: \(error.localizedDescription)")
        }
    }
    
    /// 过滤QA项目并计数新项目
    private func filterAndCountNewItems(since date: Date) {
        // 首先过滤出已审核的项目(status == 2)
        let filteredQA = qaModel.qa.filter { $0.status == 2 }
        
        // 创建日期格式化器
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        // 过滤出在指定日期之后创建或更新的项目
        let newItems = filteredQA.filter { qaItem in
            if let updateDate = dateFormatter.date(from: qaItem.updateTime) {
                return updateDate > date
            }
            // 如果日期解析失败，使用创建时间作为备选
            if let createDate = dateFormatter.date(from: qaItem.createTime) {
                return createDate > date
            }
            return false
        }
        
        // 按分类计数
        for item in newItems {
            incrementCountForCategory(item.tags)
        }
        
        print("发现 \(newItems.count) 个新QA项目")
        print("分类计数: \(counts)")
    }
    
    /// 根据分类增加计数
    private func incrementCountForCategory(_ category: String) {
        for i in 0..<counts.count {
            if counts[i].category == category {
                counts[i].value += 1
                return
            }
        }
        // 如果分类不匹配已知分类，增加到"其他"类别
        for i in 0..<counts.count {
            if counts[i].category == "其他" {
                counts[i].value += 1
                return
            }
        }
    }
    
    /// 重置计数
    private func resetCounts() {
        for i in 0..<counts.count {
            counts[i].value = 0
        }
    }
    
    /// 更新最新请求时间
    private func updateLatestRequestDate() {
        if #available(iOS 15, *) {
            UserDefaultsManager.shared.latestRequestQA = Date.now
        } else {
            UserDefaultsManager.shared.latestRequestQA = Date()
        }
        print("更新最新请求时间完成")
    }
    
    /// 获取指定分类的计数
    func getCountForCategory(_ category: String) -> Int {
        return counts.first { $0.category == category }?.value ?? 0
    }
}
