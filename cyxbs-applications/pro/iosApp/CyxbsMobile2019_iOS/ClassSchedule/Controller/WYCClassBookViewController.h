//
//  WYCClassBookViewController.h
//  MoblieCQUPT_iOS
//
//  Created by 王一成 on 2018/9/21.
//  Copyright © 2018年 Orange-W. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "DLReminderViewController.h"
#import "WYCClassAndRemindDataModel.h"
#import "DateModle.h"
//#import "LoginViewController.h"
#import "FakeTabBarView.h"
#import "TopBarScrollView.h"
#import "AddRemindViewController.h"
#import "UIFont+AdaptiveFont.h"
#import "RemindNotification.h"

NS_ASSUME_NONNULL_BEGIN

@interface WYCClassBookViewController : UIViewController<WYCClassAndRemindDataModelDelegate>

/// 课表数据、备忘数据模型
@property (nonatomic, strong) WYCClassAndRemindDataModel *model;

/// 课表类型，共分3种：个人、没课约、查课表
@property (nonatomic, assign)ScheduleType schedulType;

/// 真正的tabbar，显示下节课信息
@property (nonatomic, weak)id <updateSchedulTabBarViewProtocol>schedulTabBar;

/// 学号
//@property (nonatomic, copy) NSString *stuNum;

/// 身份证后六位
//@property (nonatomic, copy) NSString *idNum;

/// 假的tabbar，用来模拟真的tabbar，达到tabbar跟随课表运动的假象
@property (nonatomic,strong)FakeTabBarView *fakeBar;

/// 最顶部的 一个可以切换成选择周 或 当前课表页周信息的条，上面有左右箭头按钮、回到本周按钮、选择周条。。
@property (nonatomic, strong)TopBarScrollView *topBarView;

//如果WYCClassAndRemindDataModel的代理是课表控制器，那么模型加载操作完毕后无需外界调这两个方法
//但是如果代理不是课表控制器，那么外界需要调用一下这两个方法
- (void)ModelDataLoadFailure;
- (void)ModelDataLoadSuccess;

//type是ScheduleTypePersonal时, 要求info是学号
//type是ScheduleTypeClassmate时, 要求info是学号
//type是ScheduleTypeTeacher时, 要求info是这种结构@{ @"teaName": name, @"tea": teaNum }
//type是ScheduleTypeWeDate时, 要求info是这种结构@[@{@"stuNum":学号}, @{@"stuNum":学号}...]
- (instancetype)initWithType:(ScheduleType)type andInfo:(id)info;
@end

NS_ASSUME_NONNULL_END
//用代码加载WYCClassBookViewController的使用说明：

//1.创建所需模型，在对模型完成一些初始化操作
//WYCClassAndRemindDataModel *model = [[WYCClassAndRemindDataModel alloc] init];
//model.weekArray = [@[array]mutableCopy];
//[model parsingClassBookData:array];
//[model setValue:@"YES" forKey:@"remindDataLoadFinish"];
//[model setValue:@"YES" forKey:@"classDataLoadFinish"];

//2.从控制器加载WYCClassBookViewController
//WYCClassBookViewController *vc = [[UIStoryboard storyboardWithName:@"Main" bundle:nil] instantiateViewControllerWithIdentifier:@"WYCClassBookViewController"];

//
//[vc initStuNum:@"x" andIdNum:@"x"];

//4.用模型对vc在初始化一下
//[vc initWYCClassAndRemindDataModel:model];

//5.presentViewController，或pushVC


