//
//  UserModel.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/10/7.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation

struct UserModel: Codable {
    
    static var `default` = UserModel()
    
    private init() { }
    
    
    // token
    lazy var token: TokenModel? = {
        CacheManager.shared.getCodable(TokenModel.self, in: .token)
    }() {
        didSet {
            if let token {
                CacheManager.shared.cache(codable: token, in: .token)
            }
        }
    }
    
    // person
    lazy var person: PersonModel? = {
        CacheManager.shared.getCodable(PersonModel.self, in: .currentPerson)
    }() {
        didSet {
            if let person {
                CacheManager.shared.cache(codable: person, in: .currentPerson)
            }
        }
    }
    
    // custom
    lazy var customSchedule: ScheduleModel = {
        CacheManager.shared.getCodable(ScheduleModel.self, in: .customSchedule)
        ?? .init(sno: token?.stuNum ?? "临时学生", customType: .custom)
    }() {
        didSet {
            CacheManager.shared.cache(codable: customSchedule, in: .customSchedule)
        }
    }
     
    
    lazy var start: Date? = {
        UserDefaultsManager.widget.dateForSemester
    }() {
        didSet {
            if let start {
                UserDefaultsManager.widget.dateForSemester = start
            }
        }
    }
    
    mutating func nowWeek() -> Int? {
        
        guard let start else { return nil }
        
        let calendar = Calendar(identifier: .gregorian)
        
        let days = calendar.dateComponents([.day], from: start, to: Date()).day ?? 0
        
        return days / 7 + 1
    }
    
    mutating func logout() {
        token = nil
        // 删缓存即可，SessionManager 下次发请求读缓存自然取不到 token（同上，不向其同步）。
        CacheManager.shared.delete(path: .token)
    }
}
