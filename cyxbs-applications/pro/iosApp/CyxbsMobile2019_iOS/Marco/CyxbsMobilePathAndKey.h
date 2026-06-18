//
//  CyxbsMobilePathAndKey.h
//  CyxbsMobile2019_iOS
//
//  Created by Stove on 2021/3/20.
//  Copyright © 2021 Redrock. All rights reserved.
//这里面放一些文件路径的宏 以及缓存的key的宏

#ifndef CyxbsMobilePathAndKey_h
#define CyxbsMobilePathAndKey_h

#define USER_DEFAULT NSUserDefaults.standardUserDefaults

//课表、备忘数据文件的文件夹
#define remAndLesDataDirectoryPath [NSHomeDirectory() stringByAppendingPathComponent:@"Documents/rem&les"]

//未解析的课表数据文件路径
#define rowDataArrPath [NSHomeDirectory() stringByAppendingPathComponent:@"Documents/rem&les/rowLessonDataArr"]

//已解析的课表数据文件路径,假设取出的数组是orderlySchedulArray，那么：
//orderlySchedulArray[i][j][k]代表（第i周）的（星期j+1）的（第k+1节大课）
//ijk的范围：i[0, 25], j[0, 6], k[0, 5]
#define parsedDataArrPath [NSHomeDirectory() stringByAppendingPathComponent:@"Documents/rem&les/parsedLessonDataArr"]


//备忘数据文件夹路径
#define remDataDirectory [NSHomeDirectory() stringByAppendingPathComponent:@"Documents/remDataDirectory"]

//备忘数据文件路径，取出来是一个数组，数组内放着 初始化备忘模型时传入的字典
#define remDataArrPath [remDataDirectory stringByAppendingPathComponent:[UserItem defaultItem].stuNum]

//#define remDataArrPath [NSHomeDirectory() stringByAppendingPathComponent:@"Documents/rem&les/remindDataArr"]

//可以通过NSUserDefaults取出当前周数，类型是NSString
#define nowWeekKey_NSString @"nowWeekKey_NSString"
//个人页的获赞数的缓存的key
#define MinePraiseCntStrKey @"MinePraiseCntStrKey"
//个人页的动态数的缓存的key
#define MineDynamicCntStrKey @"MineDynamicCntStrKey"
//个人页的评论数的缓存的key
#define MineCommentCntStrKey @"MineCommentCntStrKey"

//个人页面最后一次签到的签到时间缓存，取出来是NSDate类型
#define MineLastCheckInTime_NSDate @"MineLastCheckInTime_NSDate"

//可以用userDefault取出开学日期，格式是@"yyyy-MM-dd"
#define DateStartKey_NSString @"DateStartKey_NSString"

//记录最后一次登录的时间戳，类型是Double，用来避免后端出问题后的强制退出登录
#define LastLogInTimeKey_double @"LastLogInTimeKey_TimeInterval"

//缓存的键，用来确定刷新token的接口是否出问题了，ToolMacro里面有定义了相关block，1代表error，-1正常
#define IS_TOKEN_URL_ERROR_INTEGER @"IS_TOKEN_URL_ERROR_INTEGER"

#pragma mark - UserDefault From Module

#import "MineMessageHeader.h"

#import "SchoolCalendarHeader.h"

#import "ADHeader.h"

#import "ScorePageHeader.h"

#import "TestArrangeHeader.h"

#import "ToDoHeader.h"

#import "MapHeader.h"

#import "MineHeader.h"

#import "ChangePasswordHeader.h"

#import "StampCenterHeader.h"

#import "FeedBackCenterHeader.h"

#import "ClassScheduleHeader.h"

#import "NewQAHeader.h"

#import "RisingScheduleHeader.h"

#import "AttitudeHeader.h"

#endif /* CyxbsMobilePathAndKey_h */
