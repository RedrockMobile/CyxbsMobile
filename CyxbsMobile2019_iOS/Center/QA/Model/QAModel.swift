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
    func requestQACenterObjects(QATag: String, pageNum: Int? = nil, pageSize: Int? = nil,
                                success: @escaping ([QAObject]) -> Void, failure: @escaping (Error) -> Void) {
        qa = []
        
        print("开始请求QA列表，标签: \(QATag), 页码: \(pageNum ?? 0), 页大小: \(pageSize ?? 0)")
        
        HttpManager.shared.magipoke_qa_listQuestion(tags: QATag, page: pageNum, page_size: pageSize).ry_JSON { response in
            switch response {
            case .success(let jsonData):
                print("QA列表响应: \(jsonData)")
                
                // 检查响应结构
                guard jsonData["status"].stringValue == "200" || jsonData["status"].stringValue == "10000" else {
                    let error = NSError(domain: "API错误", code: -1,
                                        userInfo: [NSLocalizedDescriptionKey: jsonData["info"].stringValue])
                    failure(error)
                    return
                }
                
                let allQAResponse = AllQAResponse(from: jsonData)
                self.qa = allQAResponse.data.items
                success(self.qa)
                
            case .failure(let error):
                print("QA列表请求失败，错误: \(error)")
                failure(error)
            }
        }
    }
    
    /// 请求搜索的QA项目
    func requestSearchObjects(keyword: String,
                              success: @escaping ([QAObject]) -> Void,
                              failure: @escaping (Error) -> Void) {
        qa = []
        
        print("开始搜索QA，关键词: \(keyword)")
        
        HttpManager.shared.magipoke_qa_search(q: keyword).ry_JSON { response in
            switch response {
            case .success(let jsonData):
                print("QA搜索响应: \(jsonData)")
                
                // 检查响应结构
                guard jsonData["status"].stringValue == "200" || jsonData["status"].stringValue == "10000" else {
                    let error = NSError(domain: "API错误", code: -1,
                                        userInfo: [NSLocalizedDescriptionKey: jsonData["info"].stringValue])
                    failure(error)
                    return
                }
                
                // 尝试不同的响应结构
                if jsonData["data"]["items"].exists() {
                    let searchQAResponse = SearchQAResponse(from: jsonData)
                    self.qa = searchQAResponse.data.items
                    success(self.qa)
                } else if jsonData["data"].array != nil {
                    // 如果data直接是数组
                    let items = jsonData["data"].arrayValue.map { QAObject(from: $0) }
                    self.qa = items
                    success(self.qa)
                } else {
                    let error = NSError(domain: "解析错误", code: -2,
                                        userInfo: [NSLocalizedDescriptionKey: "无法解析响应数据"])
                    failure(error)
                }
                
            case .failure(let error):
                print("QA搜索请求失败，错误: \(error)")
                failure(error)
            }
        }
    }
    
    func requestDetailObject(id: Int,
                             success: @escaping (QAObject) -> Void,
                             failure: @escaping (Error) -> Void) {
        print("开始请求QA详情，ID: \(id)")
        
        HttpManager.shared.magipoke_qa_getDetail(identifier: id).ry_JSON { response in
            switch response {
            case .success(let jsonData):
                print("QA详情响应: \(jsonData)")
                
                // 检查响应结构
                guard jsonData["status"].stringValue == "200" || jsonData["status"].stringValue == "10000" else {
                    let error = NSError(domain: "API错误", code: -1,
                                        userInfo: [NSLocalizedDescriptionKey: jsonData["info"].stringValue])
                    failure(error)
                    return
                }
                
                let detailQAResponse = DetailQAResponse(from: jsonData)
                success(detailQAResponse.data.item)
                
            case .failure(let error):
                print("QA详情请求失败，错误: \(error)")
                failure(error)
            }
        }
    }
    
    /// 将从服务器获取的日期格式化
    func dateFormatter(dateString: String) -> String? {
        // 检查是否为无效日期（未回答时服务器返回的默认值）
        if dateString.isEmpty || dateString == "0001-01-01T00:00:00Z" {
            return nil
        }
        
        if #available(iOS 11.0, *) {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            
            guard let date = formatter.date(from: dateString) else {
                return nil
            }
            
            let outputFormatter = DateFormatter()
            outputFormatter.dateFormat = "yyyy.MM.dd"
            return outputFormatter.string(from: date)
        } else {
            // Fallback on earlier versions
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ"
            
            guard let date = formatter.date(from: dateString) else {
                return nil
            }
            
            let outputFormatter = DateFormatter()
            outputFormatter.dateFormat = "yyyy.MM.dd"
            return outputFormatter.string(from: date)
        }
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

struct DetailQAResponse : Codable{
    var info : String
    var status : String
    var data : DetailQAData
}

extension DetailQAResponse{
    init(from json : JSON){
        info = json["info"].stringValue
        status = json["status"].stringValue
        data = DetailQAData(from: json["data"])
    }
}

struct DetailQAData : Codable{
    var item : QAObject
}

extension DetailQAData{
    init(from json : JSON){
        item = QAObject(from: json["item"])
    }
}

enum QAType {
    case all
    case freshMen
    case life
    case study
    case other
}
