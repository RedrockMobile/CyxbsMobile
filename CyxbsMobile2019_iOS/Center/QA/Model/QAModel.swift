//
//  QAModel.swift
//  CyxbsMobile2019_iOS
//
//  Created by Holeon on 2025/8/14.
//  Copyright © 2025 Redrock. All rights reserved.
//

import Foundation
import SwiftyJSON

class QAModel{
    
    var qa : [QAObject] = []
    
    ///请求所有或限定页数的QA项目
    func requestQACenterObjects(QATag : String, pageNum: Int?, pageSize: Int?,success: @escaping ([QAObject]) -> Void, failure: @escaping (Error) -> Void){
        HttpManager.shared.magipoke_qa_listQuestion(tags: QATag, page: pageNum, page_size: pageSize).ry_JSON { response in
            switch response{
            case .success(let jsonData):
                let allQAResponse = AllQAResponse(from: jsonData)
                self.qa = allQAResponse.data.items
                success(self.qa)
            case .failure(let error):
                print("请求失败，错误：\(error)")
                failure(error)
            }
        }
    }
    
    
    ///请求搜索的QA项目
    func requestSearchObjects(keyword : String, success: @escaping ([QAObject]) -> Void, failure: @escaping (Error) -> Void){
        HttpManager.shared.magipoke_qa_search(q: keyword).ry_JSON { response in
            switch response{
            case .success(let jsonData):
                let searchQAResponse = SearchQAResponse(from: jsonData)
                self.qa = searchQAResponse.data.items
                success(self.qa)
            case .failure(let error):
                print("请求失败，错误：\(error)")
                failure(error)
            }
        }
    }
    
    ///将从服务器获取的日期格式化
    func dateFormatter(dateString:String) -> String?{
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        guard let date = formatter.date(from: dateString)else{
            return nil
        }
        let outputFormatter = DateFormatter()
        outputFormatter.dateFormat = "yyyy.MM.dd"
        outputFormatter.timeZone = TimeZone.current
        return outputFormatter.string(from: date)
    }
    
}

struct QAObject : Codable{
    
    var ID : Int
    var createTime : String
    var updateTime : String
    var questionString : String
    var answerString : String
    var studentNum : String
    var likeCount : Int
    var viewCount : Int
    var status : Int
    var tags : String
    var aTime : String
    var isLike : Bool
    
    private enum CodingKeys: String, CodingKey {
        case ID = "ID"
        case createTime = "CreatedAt"
        case updateTime = "UpdatedAt"
        case questionString = "q"
        case answerString = "a"
        case studentNum = "stu_num"
        case likeCount = "like_count"
        case viewCount = "view_count"
        case status = "status"
        case tags = "tags"
        case aTime = "a_time"
        case isLike = "is_like"
    }
    
}

extension QAObject {
    init(from json : JSON){
        ID = json["ID"].intValue
        createTime = json["CreatedAt"].stringValue
        updateTime = json["UpdatedAt"].stringValue
        questionString = json["q"].stringValue
        answerString = json["a"].stringValue
        studentNum = json["stu_num"].stringValue
        likeCount = json["like_count"].intValue
        viewCount = json["view_count"].intValue
        status = json["status"].intValue
        tags = json["tags"].stringValue
        aTime = json["a_time"].stringValue
        isLike = json["is_like"].boolValue
    }
}

struct AllQAResponse : Codable{
    var info : String
    var status : String
    var data : AllQAData
}

extension AllQAResponse {
    init(from json : JSON){
        info = json["info"].stringValue
        status = json["status"].stringValue
        data = AllQAData(from: json["data"])
    }
}

struct AllQAData : Codable{
    var items : [QAObject]
}

extension AllQAData{
    init(from json: JSON){
        items = json["items"].arrayValue.map { QAObject(from: $0)}
    }
}

struct SearchQAResponse : Codable{
    var info : String
    var status : String
    var data : AllQAData
}

extension SearchQAResponse{
    init(from json : JSON){
        info = json["info"].stringValue
        status = json["status"].stringValue
        data = AllQAData(from: json["data"])
    }
}

struct SearchQAData : Codable{
    var items : [QAObject]
}

extension SearchQAData{
    init(from json: JSON){
        items = json["items"].arrayValue.map { QAObject(from: $0)}
    }
}
