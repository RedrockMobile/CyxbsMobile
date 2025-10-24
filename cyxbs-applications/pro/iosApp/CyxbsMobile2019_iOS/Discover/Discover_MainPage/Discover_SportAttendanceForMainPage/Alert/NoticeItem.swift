//
//  NoticeItem.swift
//  CyxbsMobile2019_iOS
//
//  Created by Max Xu on 2024/11/21.
//  Copyright © 2024 Redrock. All rights reserved.
//

import SwiftyJSON

struct NoticeItem {
    let title: String
    let content: String

    init(json: JSON) {
        self.title = json["title"].stringValue
        self.content = json["content"].stringValue
    }
    
    init(title: String, content: String) {
        self.title = title
        self.content = content
    }
}
