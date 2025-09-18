//
//  WeDateHeader.swift
//  CyxbsMobile2019_iOS
//
//  Created by coin on 2023/10/28.
//  Copyright © 2023 Redrock. All rights reserved.
//

/// 屏幕宽度
let SCREEN_WIDTH = UIScreen.main.bounds.size.width
/// 屏幕高度
let SCREEN_HEIGHT = UIScreen.main.bounds.size.height
/// 状态栏高度
let statusBarHeight = getStatusBarHeight()
func getStatusBarHeight() -> CGFloat {
    var height: CGFloat = 0.0
    if #available(iOS 13.0, *) {
        let window = UIApplication.shared.windows.first
        height = window?.windowScene?.statusBarManager?.statusBarFrame.height ?? 0.0
    } else {
        height = UIApplication.shared.statusBarFrame.height
    }
    return height
}

let CyxbsMobileBaseURL_1 = UserDefaults.standard.object(forKey: "baseURL") as! String
/// 获取所有分组
let Discover_GET_allGroup_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/group/all"
/// 删除分组
let Discover_DELETE_deleteGroup_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/group"
/// 更新分组
let Discover_PUT_updateGroup_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/group"
/// 获取行程标题热词
let Reminder_GET_titleHotWord = CyxbsMobileBaseURL_1  + "magipoke-reminder/Person/getHotWord"
/// 获取行程地点热词
let Reminder_GET_placeHotWord = CyxbsMobileBaseURL_1  + "magipoke-jwzx/itinerary/hotLocation"
/// 发送通知
let Discover_POST_sendNotification_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/itinerary"
/// 查询课表
let Discover_POST_courseSchedule_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/kebiao"
/// 查询同学
let Discover_GET_searchStudent_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/search/people"
/// 批量添加信息验证
let Discover_POST_checkData_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/member/check"
/// 分组删除成员
let Discover_POST_deleteMember_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/member/delete"
/// 分组添加成员
let Discover_POST_addMember_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/member"
/// 上传分组
let Discover_POST_createGroup_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/group"
/// 临时分组搜索
let Discover_GET_temporaryGroup_API = CyxbsMobileBaseURL_1  + "magipoke-jwzx/no_class/group/search/temporary"
