//
//  UserItemTool.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2019/10/24.
//  Copyright © 2019 Redrock. All rights reserved.
//

#import "UserItemTool.h"
#import "UserDefaultTool.h"
#import "掌上重邮-Swift.h"        // 将Swift中的类暴露给OC
@interface UserItemTool ()

@property (nonatomic, strong) UserItem *item;

@end

@implementation UserItemTool 

#pragma mark - 工具类方法
+ (NSString *)userItemPath {
    return [NSString stringWithFormat:@"%@/%@", NSTemporaryDirectory(), @"UserItem.data"];
}

+ (UserItem *)defaultItem {
    UserItem *item = [UserItem defaultItem];
    return item;
}

/// 退出登录（清除用户缓存）
+ (void)logout {
    RYLoginViewController *loginVC = [[RYLoginViewController alloc] init];
    loginVC.modalPresentationStyle = UIModalPresentationFullScreen;

    UITabBarController *tabBarVC = (UITabBarController *)[UIApplication sharedApplication].delegate.window.rootViewController;
    if (tabBarVC.presentedViewController) {
        [tabBarVC dismissViewControllerAnimated:YES completion:^{
            [tabBarVC presentViewController:loginVC animated:YES completion:nil];
        }];
    } else {
        [tabBarVC presentViewController:loginVC animated:YES completion:nil];
    }
    
    //假销毁单例
    [[UserItem defaultItem] attemptDealloc];
    
    NSString *filePath = [self userItemPath];
    
    // 删除偏好设置，删除时保留baseURL的偏好信息
    NSDictionary *dic = [NSUserDefaults.standardUserDefaults dictionaryRepresentation];
    for (id key in dic) {
        if (![key isEqual: @"baseURL"]) {
            [NSUserDefaults.standardUserDefaults removeObjectForKey:key];
        }
    }
    
    // 删除归档
    NSFileManager * fileManager = [NSFileManager defaultManager];
    if ([fileManager fileExistsAtPath:filePath]) {
        NSError *err;
        [fileManager removeItemAtPath:filePath error:&err];
    }
    
    NSLog(@"%@", NSHomeDirectory());
    
//    [UserDefaultTool removeALLData];
    
//    NSLog(@"%@",[UserItemTool defaultItem]);
    
//    [UserItem attemptDealloc];
    
//    [UserItem defaultItem].token = nil;
    
//    [ArchiveTool deleteFile];
//    [PostArchiveTool removePostModel];
//    [PostArchiveTool removeGroupModel];
//    [PostArchiveTool removeHotWordModel];
//    [PostArchiveTool removePostCellHeight];
    
    //清除课表数据和备忘数据
    [[NSFileManager defaultManager] removeItemAtPath:remAndLesDataDirectoryPath error:nil];
    
    //清除所有已有的本地通知
//    [[UNUserNotificationCenter currentNotificationCenter] removeAllDeliveredNotifications];
//    [[UNUserNotificationCenter currentNotificationCenter] removeAllPendingNotificationRequests];
    
    // 退出后停止umeng统计发送数据
//    [MobClick profileSignOff];
    
    // 退出后移除友盟推送别名
//    [UMessage removeAlias:[UserItemTool defaultItem].stuNum type:@"cyxbs" response:^(id  _Nonnull responseObject, NSError * _Nonnull error) {
//        
//    }];
    
}

+ (BOOL)supportsSecureCoding {
    return YES;
}

@end
