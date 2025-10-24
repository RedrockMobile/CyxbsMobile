//
//  ClassScheduleHeader.h
//  CyxbsMobile2019_iOS
//
//  Created by 小艾同学 on 2022/7/26.
//  Copyright © 2022 Redrock. All rights reserved.
//

#ifndef ClassScheduleHeader_h
#define ClassScheduleHeader_h

// “课表”、“备忘”接口

//课表缓存
#define AllowCaching YES

/// 空教室接口
#define ClassSchedule_POST_emptyClass_API [CyxbsMobileBaseURL_1 stringByAppendingString:@"magipoke-jwzx/roomEmpty"]

/// 同学课表之查找同学，GET，参数：@{@"stu"]: 用来搜索的数据}
#define ClassSchedule_GET_searchPeople_API  [CyxbsMobileBaseURL_1 stringByAppendingString:@"magipoke-jwzx/search/people"]

/// 查找老师，POST，参数：@{@"teaName"]: 用来搜索的数据}
#define ClassSchedule_POST_searchTeacher_API [CyxbsMobileBaseURL_1 stringByAppendingString:@"magipoke-teakb/api/teaSearch"]

//获取Store上的掌邮的版本id
//@"http://itunes.apple.com/cn/lookup?id=974026615"
#define ClassSchedule_GET_getNewVersionID_API @"http://itunes.apple.com/cn/lookup?id=974026615"

#endif /* ClassScheduleHeader_h */
