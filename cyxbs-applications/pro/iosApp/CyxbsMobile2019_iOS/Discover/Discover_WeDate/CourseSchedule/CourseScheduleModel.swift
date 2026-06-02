//
//  CourseScheduleModel.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/9/21.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import SwiftyJSON

class CourseScheduleModel {
    
    private static func maskedStuNum(_ stuNum: String) -> String {
        let suffix = stuNum.suffix(4)
        return "****\(suffix)"
    }
    
    private static func debugLog(_ message: String, stuNum: String) {
        #if DEBUG
        print("[WeDateCourseSchedule] \(message), stuNum=\(maskedStuNum(stuNum))")
        #endif
    }
    
    private static func fallbackStudent(stuNum: String) -> StudentResultItem {
        StudentResultItem(dictionary: ["name": stuNum, "stunum": stuNum])
    }
    
    /// 现在周数
    var nowWeek: Int = 0
    /// 日期版本
    var dateVersion: String = ""
    /// 课程信息数组
    var courseAry: [CourseItem] = []
    /// 学生信息
    var student = StudentResultItem(dictionary: [:])
    
    static func requestWithStuNum(_ stuNum: String, success: ((_ courseScheduleModel: CourseScheduleModel) -> Void)?, failure: ((_ error: Error) -> Void)?) {
        debugLog("request start", stuNum: stuNum)
        let courseScheduleModel = CourseScheduleModel()
        courseScheduleModel.student = fallbackStudent(stuNum: stuNum)
        var scheduleSucceeded = false
        var scheduleError: Error?
        let group = DispatchGroup()
        group.enter()
        HttpTool.share().request(Discover_POST_courseSchedule_API,
                                 type: .post,
                                 serializer: .JSON,
                                 bodyParameters: ["stu_num": stuNum],
                                 progress: nil,
                                 success: { task, object in
            let json = JSON(object!)
            courseScheduleModel.nowWeek = json["nowWeek"].intValue
            courseScheduleModel.dateVersion = json["version"].stringValue
            if let courses = json["data"].arrayObject as? [[String: Any]] {
                for dic in courses {
                    let course = CourseItem(dictionary: dic)
                    courseScheduleModel.courseAry.append(course)
                }
            }
            scheduleSucceeded = true
            debugLog("schedule success courseCount=\(courseScheduleModel.courseAry.count)", stuNum: stuNum)
            group.leave()
        },
                                 failure: { task, error in
            scheduleError = error
            debugLog("schedule failure error=\(error.localizedDescription)", stuNum: stuNum)
            group.leave()
        })
        
        group.enter()
        HttpTool.share().request(Discover_GET_searchStudent_API,
                                 type: .get,
                                 serializer: .HTTP,
                                 bodyParameters: ["stu": stuNum],
                                 progress: nil,
                                 success: { task, object in
            let json = JSON(object!)
            if let dic = json["data"][0].dictionaryObject {
                let student = StudentResultItem(dictionary: dic)
                courseScheduleModel.student = student
            }
            debugLog("student success resolved=\(!courseScheduleModel.student.studentID.isEmpty)", stuNum: stuNum)
            group.leave()
        },
                                 failure: { task, error in
            debugLog("student failure error=\(error.localizedDescription), use fallback student", stuNum: stuNum)
            group.leave()
        })
        group.notify(queue: .main) {
            if !scheduleSucceeded {
                let error = scheduleError ?? NSError(domain: "CourseScheduleModel", code: -1, userInfo: [NSLocalizedDescriptionKey: "Course schedule request failed"])
                debugLog("request failed error=\(error.localizedDescription)", stuNum: stuNum)
                failure?(error)
                return
            }
            debugLog("request finished courseCount=\(courseScheduleModel.courseAry.count), resolvedStudent=\(!courseScheduleModel.student.studentID.isEmpty)", stuNum: stuNum)
            success?(courseScheduleModel)
        }
    }
}
