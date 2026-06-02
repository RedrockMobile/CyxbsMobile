//
//  WeekMaping.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/21.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit

/// 数组映射
class WeekMaping {
    
    private static let cacheQueue = DispatchQueue(label: "com.cyxbs.wedate.courseSchedule.cache")
    private static let requestQueue: OperationQueue = {
        let queue = OperationQueue()
        queue.name = "com.cyxbs.wedate.courseSchedule.request"
        queue.maxConcurrentOperationCount = 3
        return queue
    }()
    private static var courseScheduleCache: [String: CourseScheduleModel] = [:]
    private static var pendingCourseScheduleCompletions: [String: [(CourseScheduleModel?) -> Void]] = [:]
    
    /// 返回一个映射某人某周课程安排的二维数组
    /// - Parameters:
    ///   - stuNum: 学号
    ///   - weekNum: 周数（展示整学期则传入0
    /// - Returns: 二维数组
    private static func mapPersonWeekToAry(stuNum: String, weekNum: Int, completion: @escaping ([[StudentResultItem]]) -> Void) {
        requestCourseSchedule(stuNum: stuNum) { courseScheduleModel in
            var perHeadAry = [[StudentResultItem]](repeating: [StudentResultItem](repeating: StudentResultItem(dictionary: [:]), count: 12), count: 7)
            guard let courseScheduleModel = courseScheduleModel else {
                completion(perHeadAry)
                return
            }
            
            let student = courseScheduleModel.student
            for course in courseScheduleModel.courseAry where weekNum == 0 || course.inWeeks.contains(weekNum) {
                guard (1...7).contains(course.dayNum), (1...12).contains(course.beginLesson), course.period > 0 else { continue }
                let dayIndex = course.dayNum - 1
                let beginIndex = course.beginLesson - 1
                let endIndex = min(beginIndex + course.period, perHeadAry[dayIndex].count)
                for lessonIndex in beginIndex..<endIndex {
                    perHeadAry[dayIndex][lessonIndex] = student
                }
            }
            completion(perHeadAry)
        }
    }
    
    static func cacheCourseSchedule(_ courseScheduleModel: CourseScheduleModel, for stuNum: String) {
        cacheQueue.async {
            courseScheduleCache[stuNum] = courseScheduleModel
        }
    }
    
    private static func requestCourseSchedule(stuNum: String, completion: @escaping (CourseScheduleModel?) -> Void) {
        cacheQueue.async {
            if let cachedModel = courseScheduleCache[stuNum] {
                completion(cachedModel)
                return
            }
            
            if pendingCourseScheduleCompletions[stuNum] != nil {
                pendingCourseScheduleCompletions[stuNum]?.append(completion)
                return
            }
            
            pendingCourseScheduleCompletions[stuNum] = [completion]
            enqueueCourseScheduleRequest(stuNum: stuNum)
        }
    }
    
    private static func enqueueCourseScheduleRequest(stuNum: String) {
        requestQueue.addOperation {
            let semaphore = DispatchSemaphore(value: 0)
            CourseScheduleModel.requestWithStuNum(stuNum) { courseScheduleModel in
                cacheQueue.async {
                    courseScheduleCache[stuNum] = courseScheduleModel
                    let completions = pendingCourseScheduleCompletions.removeValue(forKey: stuNum) ?? []
                    completions.forEach { $0(courseScheduleModel) }
                    semaphore.signal()
                }
            } failure: { error in
                print(error)
                cacheQueue.async {
                    let completions = pendingCourseScheduleCompletions.removeValue(forKey: stuNum) ?? []
                    completions.forEach { $0(nil) }
                    semaphore.signal()
                }
            }
            semaphore.wait()
            Thread.sleep(forTimeInterval: 0.12)
        }
    }
    
    /// 返回一个映射所有人某周课程安排的三维数组
    /// - Parameters:
    ///   - stuNumAry: 学号数组
    ///   - weekNum: 周数（展示整学期则传入0
    ///   - completion: 三维数组
    static func mapWeekToAry(stuNumAry: [String], weekNum: Int, completion: @escaping ([[[StudentResultItem]]]) -> Void) {
        /// 一个三维数组，映射所有人某周或整学期的课程安排
        var weekAry = [[[StudentResultItem]]](repeating: [[StudentResultItem]](repeating: [StudentResultItem](), count: 12), count: 7)
        let group = DispatchGroup()
        let mergeQueue = DispatchQueue(label: "com.cyxbs.wedate.courseSchedule.merge")

        for stuNum in stuNumAry {
            group.enter()
            mapPersonWeekToAry(stuNum: stuNum, weekNum: weekNum) { personWeekAry in
                mergeQueue.async {
                    for i in 0..<personWeekAry.count {
                        for j in 0..<personWeekAry[i].count {
                            if !personWeekAry[i][j].studentID.isEmpty {
                                weekAry[i][j].append(personWeekAry[i][j])
                            }
                        }
                    }
                    group.leave()
                }
            }
        }

        group.notify(queue: .main) {
            #if DEBUG
            let loadedStudentIDs = Set(weekAry.flatMap { $0 }.flatMap { $0 }.map { $0.studentID }.filter { !$0.isEmpty })
            print("[WeDateCourseSchedule] week map finished week=\(weekNum), expected=\(stuNumAry.count), loaded=\(loadedStudentIDs.count)")
            #endif
            completion(weekAry)
        }
    }
    
    /// 返回一个映射所有人所有周课程安排的三维数组
    /// - Parameters:
    ///   - stuNumAry: 学号数组
    ///   - completion: 三维数组（内为字典
    static func mapAry(stuNumAry: [String], completion: @escaping ([[[String: Any]]]) -> Void) {
        /// 一个三维数组，映射所有人所有周的课程安排
        var array: [[[String: Any]]] = Array(repeating: [[String: Any]](), count: 26)
        let group = DispatchGroup()
        let queue = DispatchQueue.global(qos: .userInitiated) // 使用全局并发队列

        for weekNum in 0...25 {
            group.enter()
            queue.async {
                mapWeekToAry(stuNumAry: stuNumAry, weekNum: weekNum) { weekAry in
                    let processedWeekAry = processWeekArray(weekAry: weekAry, weekNum: weekNum)
                    array[weekNum] = processedWeekAry
                    group.leave()
                }
            }
        }

        group.notify(queue: .main) {
            completion(array)
        }
    }
    
    static func processWeekArray(weekAry: [[[StudentResultItem]]], weekNum: Int) -> ([[String: Any]]) {
        /// 标识时间数组
        let timeAry = [
            "8:00", "8:45", "8:55", "9:40", "10:15", "11:00", "11:10", "11:55", "14:00", "14:45", "14:55", "15:40", "16:15", "17:00", "17:10", "17:55", "19:00", "19:45", "19:55", "20:40", "20:50", "21:35", "21:45", "22:30"
        ]
        var array: [[String: Any]] = []
        for i in 0..<weekAry.count {
            var j = 0
            while j < weekAry[i].count {
                if !weekAry[i][j].isEmpty {
                    let beginLesson = j + 1
                    let student = weekAry[i][j]
                    var count = 1
                    var endLesson: Int = 0
                    
                    if beginLesson >= 1 && beginLesson <= 4 {
                        endLesson = 4
                    } else if beginLesson >= 5 && beginLesson <= 8 {
                        endLesson = 8
                    } else {
                        endLesson = 12
                    }
                    
                    while j + 1 < endLesson, weekAry[i][j + 1] == weekAry[i][j] {
                        j += 1
                        count += 1
                    }
                    
                    let startTime = timeAry[beginLesson * 2 - 1 - 1]
                    let endTime = timeAry[(beginLesson * 2 - 1) + (count * 2 - 1) - 1]
                    
                    let element: [String: Any] = [
                        "beginLesson": beginLesson,
                        "student": student,
                        "length": count,
                        "dayNum": i + 1,
                        "timePeriod": "\(startTime)-\(endTime)"
                    ]
                    
                    array.append(element)
                } else {
                    let beginLesson = j + 1
                    var count = 1
                    
                    if j + 1 < weekAry[i].count, weekAry[i][j + 1].isEmpty, beginLesson % 2 != 0 {
                        j += 1
                        count += 1
                    }
                    
                    let startTime = timeAry[beginLesson * 2 - 1 - 1]
                    let endTime = timeAry[(beginLesson * 2 - 1) + (count * 2 - 1) - 1]
                    
                    let element: [String: Any] = [
                        "beginLesson": beginLesson,
                        "student": [],
                        "length": count,
                        "dayNum": i + 1,
                        "timePeriod": "\(startTime)-\(endTime)"
                    ]
                    
                    array.append(element)
                }
                
                j += 1
            }
        }
        return array
    }
}
