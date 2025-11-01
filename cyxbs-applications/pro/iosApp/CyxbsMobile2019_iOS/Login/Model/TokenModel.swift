//
//  TokenModel.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/27.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation
import SwiftyJSON

struct TokenModel: Codable {
    
    var stuNum: String
    
    var gender: String
    
    var domain: String = "magipoke"
    
    var redid: String
    
    var exp: TimeInterval
    
    var iat: TimeInterval
    
    var sub: String = "web"
    
    var token: String
}

extension TokenModel {
    
    init(token: String) {
        self.token = token
        let payload = token.components(separatedBy: ".").first ?? ""
        let data = Data(base64Encoded: payload) ?? Data()
        let dic = JSON(data)
        stuNum = dic["Data"]["stu_num"].stringValue
        gender = dic["Data"]["gender"].stringValue
        domain = dic["Domain"].stringValue
        redid = dic["Redid"].stringValue
        exp = TimeInterval(dic["exp"].stringValue) ?? 0
        iat = TimeInterval(dic["iat"].stringValue) ?? 0
        sub = dic["sub"].stringValue
    }
}

extension TokenModel {
    
    var isTokenExpired: Bool {
        return exp - Date().timeIntervalSince1970 <= 0
    }
}
