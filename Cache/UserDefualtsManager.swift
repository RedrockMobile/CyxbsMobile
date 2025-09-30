//
//  UserDefualtsManager.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/6.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation

class UserDefaultsManager {
    
    static let shared = UserDefaultsManager()
    
    static let widget = UserDefaultsManager(Constants.widgetGroupID)
    
    private init(_ suiteName: String? = nil) {
        if let suiteName {
            userDefaults = UserDefaults(suiteName: suiteName) ?? .standard
        } else {
            userDefaults = UserDefaults.standard
        }
    }
    
    private let userDefaults: UserDefaults
}

// MARK: common

extension UserDefaultsManager {
    
    public func set(_ obj: Any?, forKey key: String) {
        userDefaults.set(obj, forKey: key)
    }
    
    public func get(key: String) -> Any? {
        userDefaults.object(forKey: key)
    }
}

/* 模版
 
 /// <#这个计算属性的作用#>
 var <#计算属性#>: <#返回值类型#><#?#> {
    set { set(newValue, forKey: "<#存储的Key值#>") }
    get { get(key: "<#存储的Key值#>") as<#?#> <#返回值类型#> }
 }
 
 */

// MARK: userDefaults

extension UserDefaultsManager {
    
    /// 获取当前包版本（用于App软更新时判断）
    var bundleShortVersion: String? {
        set { set(newValue, forKey: "BUNDLE_SHORT_VERSION") }
        get { get(key: "BUNDLE_SHORT_VERSION") as? String }
    }
    
    /// 上次打开App时间（用于不同天打开App时判断）
    var latestOpenApp: Date? {
        set { set(newValue, forKey: "LATEST_OPEN_APP") }
        get { get(key: "LATEST_OPEN_APP") as? Date }
    }
    
    /// 是否已经读过UserAgreement（如果为nil表示最新版本）
    var didReadUserAgreementBefore: Bool? {
       set { set(newValue, forKey: "DID_READ_USERAGREEMENT_BEFOR") }
       get { get(key: "DID_READ_USERAGREEMENT_BEFOR") as? Bool }
    }
    
    /// 获取token信息
    var token: String? {
        set { set(newValue, forKey: "TOKEN") }
        get { get(key: "TOKEN") as? String }
    }
    
    /// 获取refresh的token
    var refreshToken: String? {
        set { set(newValue, forKey: "REFRESH_TOKEN") }
        get { get(key: "REFRESH_TOKEN") as? String }
    }
    
    /// 在打开app的时候，是否弹起schedule
    var presentScheduleWhenOpenApp: Bool {
       set { set(newValue, forKey: "PRSENT_SCHEDULE_WHEN_OPEN_APP") }
       get { get(key: "PRSENT_SCHEDULE_WHEN_OPEN_APP") as? Bool ?? true }
    }
    
    /// 主学生学号（用于一级缓存）
    var mainStudentSno: String? {
        set { set(newValue, forKey: "MAIN_STUDENT_SNO") }
        get { get(key: "MAIN_STUDENT_SNO") as? String }
    }
    
    /// 上一次请求主学号时间
    var latestRequestDate: Date? {
        set { set(newValue, forKey: "LATEST_REQUEST_DATE") }
        get { get(key: "LATEST_REQUEST_DATE") as? Date }
    }
    
    /// 进入邮乐场的天数
    var daysOfEntryCarnie: Int {
       set { set(newValue, forKey: "DAYS_OF_ENTRY_CARNIE") }
       get { get(key: "DAYS_OF_ENTRY_CARNIE") as? Int ?? 1 }
    }
    
    /// 开学的时间
    var dateForSemester: Date? {
        set { set(newValue, forKey: "DATE_FOR_SEMESTER") }
        get { get(key: "DATE_FOR_SEMESTER") as? Date }
    }
    
    /// 上一次成功请求/刷新Token时间
    var latestRequestToken: Date? {
        set { set(newValue, forKey: "LATEST_REQUEST_TOKEN") }
        get { get(key: "LATEST_REQUEST_TOKEN") as? Date }
    }
    
    /// 上一次进入QA时间
    var latestRequestQA: Date? {
        set { set(newValue, forKey: "LATEST_REQUEST_QA")}
        get { get(key: "LATEST_REQUEST_QA") as? Date}
    }
    
    /// 是否已读体育打卡信息说明
    var hasReadSportsCheckInInfo: Bool? {
       set { set(newValue, forKey: "HAS_READ_SPORTS_CHECKIN_INFO") }
       get { get(key: "HAS_READ_SPORTS_CHECKIN_INFO") as? Bool }
    }
}
