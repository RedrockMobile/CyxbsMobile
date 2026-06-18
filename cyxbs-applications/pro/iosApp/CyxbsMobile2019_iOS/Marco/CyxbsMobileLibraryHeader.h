//
//  CyxbsMobileLibraryHeader.h
//  CyxbsMobile2019_iOS
//
//  Created by Stove on 2021/3/20.
//  Copyright © 2021 Redrock. All rights reserved.
//这里面放导入的第三方库的头文件

#ifndef CyxbsMobileLibraryHeader_h
#define CyxbsMobileLibraryHeader_h

#pragma mark - 头文件

#import <AFNetworking.h>
#import <UIImageView+WebCache.h>
#import <YYKit.h>
#import <Masonry.h>
#import <MBProgressHUD.h>
#import <MJExtension.h>
#import "HttpClient.h"
#import "UITextView+Placeholder.h"
//#import "UserDefaultTool.h"
#import "UserItemTool.h"
#import "BaseViewController.h"
#import "NSDate+Timestamp.h"
#import "URLController.h"
#import "SDMask.h"
#import "AESCipher.h"           // AES加密算法
#import <SDWebImage/SDWebImage.h>

#import <FluentDarkModeKit.h> // 基于iOS11.0的黑暗适配

// Category
#import "UIView+Frame.h"
#import "NSDate+Day.h"
#import "UIColor+Color.h"
#import "HttpTool.h"
#import "UIImage+Helper.h"

//自定义hud
#import "NewQAHud.h"


#pragma mark - Group共享
#define kAPPGroupID @"group.com.redrock.mobile"
#define kAppGroupShareNowDay @"nowDay"
#define kAppGroupShareThisWeekArray @"thisWeekArray"
#define kAPPUserDefaultLoginName @"name"
#define kAPPUserDefaultStudentID @"stuNum"



#pragma mark - 友盟
// 友盟DeciveToken
#define kUMDeviceToken @"kUMDeviceToken"


#endif /* CyxbsMobileLibraryHeader_h */
