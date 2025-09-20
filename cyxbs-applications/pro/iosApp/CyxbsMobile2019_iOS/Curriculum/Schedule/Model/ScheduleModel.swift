//
//  ScheduleModel.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/6.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation
import SwiftyJSON

// MARK: ~.CustomType

extension ScheduleModel {
    
    enum CustomType: Codable, Hashable {
        
        case system
        
        case custom
    }
    
    struct UniqueID: Hashable {
        
        let sno: String
        
        let customType: CustomType
    }
}

// MARK: ScheduleModel

struct ScheduleModel: Codable {
    
    // 学号
    var sno: String
    
    var customType: CustomType = .system
    
    var uuid: UniqueID {
        UniqueID(sno: sno, customType: customType)
    }
    
    var nowWeek: Int = 0 {
        didSet {
            let calculateWeek = nowWeek
            
            if customType == .custom { return }
            let calendar = Calendar(identifier: .gregorian)
            let currentDate = Date()
            
            var todayWeekDay = calendar.dateComponents([.weekday], from: currentDate).weekday ?? 2
            if todayWeekDay == 1 { todayWeekDay = 8 }
            todayWeekDay -= 1
            
            let start = calendar.date(byAdding: .day, value: -((calculateWeek - 1) * 7 + todayWeekDay - 1), to: currentDate) ?? currentDate
            
            self.start = start
            UserModel.default.start = start
        }
    }
    
    private(set) var start: Date?
    
    var student: SearchStudentModel? = nil
    
    var curriculum: [CurriculumModel] = []
    
    init(sno: String, customType: CustomType = .system) {
        self.sno = sno
        self.customType = customType
    }
}

extension ScheduleModel: Hashable {
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(sno)
        hasher.combine(customType)
    }
}

// MARK: request

extension ScheduleModel {
    
    // MARK: snos
    
    static func request(snos: Set<String>, handle: @escaping ([ScheduleModel]) -> Void) {
        
        var modelAry = [ScheduleModel]()
        
        let que = DispatchQueue(label: "ScheduleModel.magipoke_jwzx_kebiao.snos", qos: .userInteractive, attributes: .concurrent)
        
        let semaphore = DispatchSemaphore(value: 0)
        
        for sno in snos {
            
            que.async {
                
                request(sno: sno) { scheduleModel in
                    if let scheduleModel {
                        modelAry.append(scheduleModel)
                    }
                    
                    semaphore.signal()
                }
            }
        }
        
        que.async(flags: .barrier) {
            
            for _ in snos {
                
                semaphore.wait()
            }
        }
        
        que.async {
            
            DispatchQueue.main.async {
                
                handle(modelAry)
            }
        }
    }
    
    // MARK: sno
    
    static func request(sno: String, handle: @escaping (ScheduleModel?) -> Void) {
        
        var scheduleModel = ScheduleModel(sno: sno)
        var requsetKebiao = false
        
        let que = DispatchQueue(label: "ScheduleModel.magipoke_jwzx_kebiao", qos: .default, attributes: .concurrent)
        
        let semaphore = DispatchSemaphore(value: 0)
        
        que.async {
            
            HttpManager.shared.magipoke_text_search_people(stu: sno).ry_JSON { response in
                if case let .success(model) = response, let ary = model["data"].array?.map(SearchStudentModel.init) {
                    scheduleModel.student = ary.first
                }
                semaphore.signal()
            }
            
            HttpManager.shared.magipoke_jwzx_kebiao(stu_num: sno).ry_JSON { response in
                if case .success(let json) = response, json["status"].intValue == 200 {
                    requsetKebiao = true
                    scheduleModel.sno = json["stuNum"].stringValue
                    scheduleModel.nowWeek = json["nowWeek"].intValue
                    if let ary = json["data"].array?.map(CurriculumModel.init(json:)) {
                        scheduleModel.curriculum = ary
                    }
                }
                semaphore.signal()
            }
            
            semaphore.wait()
            semaphore.wait()
        }
        
        que.async(flags: .barrier) {
            
            DispatchQueue.main.async {
                
                if !requsetKebiao {
                    if let codable = CacheManager.shared.getCodable(ScheduleModel.self, in: .schedule(sno: sno)) {
                        scheduleModel = codable
                        requsetKebiao = true
                    }
                }
                
                if requsetKebiao {
                    if scheduleModel.student == nil {
                        scheduleModel.student = CacheManager.shared.getCodable(SearchStudentModel.self, in: .searchStudent(sno: sno))
                    }
                    CacheManager.shared.cache(codable: scheduleModel, in: .schedule(sno: sno))
                    handle(scheduleModel)
                } else {
                    handle(nil)
                }
            }
        }
    }
    
    // MARK: custom
    
    static func requestCustom(handle: @escaping (NetResponse<ScheduleModel>) -> Void) {
        
        var scheduleModel = UserModel.default.customSchedule
        
        let que = DispatchQueue(label: "ScheduleModel.magipoke_reminder_Person_getTransaction", qos: .unspecified, attributes: .concurrent)
        
        let semaphore = DispatchSemaphore(value: 0)
        
        que.async {
            // 搜索当前学生信息
            HttpManager.shared.magipoke_text_search_people(stu: UserModel.default.token?.stuNum ?? "").ry_JSON { response in
                if case let .success(model) = response, let ary = model["data"].array?.map(SearchStudentModel.init) {
                    scheduleModel.student = ary.first
                }
                semaphore.signal()
            }
            
            // 请求事务
            HttpManager.shared.magipoke_reminder_Person_getTransaction().ry_JSON { response in
                if case .success(let model) = response {
                    scheduleModel.sno = model["stuNum"].stringValue
                    scheduleModel.nowWeek = UserModel.default.nowWeek() ?? 0
                    if let ary = model["data"].array?.map(CurriculumModel.init(cus:)) {
                        scheduleModel.curriculum = ary
                    }
                }
                
                semaphore.signal()
            }
            
            semaphore.wait()
            semaphore.wait()
        }
        
        que.async(flags: .barrier) {
            scheduleModel.customType = .custom
            scheduleModel.sno = UserModel.default.token?.stuNum ?? scheduleModel.sno
            UserModel.default.customSchedule = scheduleModel
            DispatchQueue.main.async {
                handle(.success(scheduleModel))
            }
        }
    }
}

// MARK: cals

extension ScheduleModel {
    
    var calModels: [ScheduleCalModel] { ScheduleCalModel.create(with: self) }
    
    static func calCourseWillBeTaking(with calModels: [ScheduleCalModel]) -> ScheduleCalModel? {
        let currentDate = Date()
        var cal: ScheduleCalModel? = nil
        for calModel in calModels {
            if let startDate = calModel.startCal, let endDate = calModel.endCal {
                if Calendar.current.isDateInToday(startDate) {
                    if startDate <= currentDate && currentDate <= endDate {
                        return calModel
                    }
                    if currentDate <= startDate {
                        guard let old = cal else {
                            cal = calModel
                            continue
                        }
                        if let calStart = old.start, startDate < calStart {
                            cal = calModel
                        }
                    }
                }
            }
        }
        return cal
    }
}
