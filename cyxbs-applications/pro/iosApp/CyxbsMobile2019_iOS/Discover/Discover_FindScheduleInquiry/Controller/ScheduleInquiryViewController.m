//
//  ScheduleInquiryViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2020/1/20.
//  Copyright © 2020 Redrock. All rights reserved.
// 这个控制器是查课表页的总控制器

#import "ScheduleInquiryViewController.h"
#import "QAListSegmentView.h"
#import "ScheduleViewController.h"
#import "MXObjCBackButton.h"
#define STU_FIND_HISTORY @"FindStudentSchedule_historyArray"
#define TEA_FIND_HISTORY @"FindTeacherSchedule_historyArray"

@interface ScheduleInquiryViewController () <ScheduleViewControllerDelegate>
/**显示"查课表"三个字delabel*/
@property (nonatomic, weak)UILabel *titleLabel;
/**self.titleLabel的背景*/
@property (nonatomic, weak)UIView *backgroundView;
@end

@implementation ScheduleInquiryViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    if (@available(iOS 11.0, *)) {
        self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
    } else {
        self.view.backgroundColor = UIColor.whiteColor;
    }
    //自定义的Tabbar（显示“查课表”的那块）
    [self addCustomTabbarView];
    
    //两个页面，两个控制器
    [self addStudentView];
    
    //添加返回按钮
    [self addBackButton];
    // Do any additional setup after loading the view.
}

//自定义的Tabbar（显示“查课表”的那块）
- (void)addCustomTabbarView {
    UIView *backgroundView;
    if IS_IPHONEX {
        backgroundView = [[UIView alloc]initWithFrame:CGRectMake(0, 55, self.view.width, 40)];
    }else {
        backgroundView = [[UIView alloc]initWithFrame:CGRectMake(0, 25, self.view.width, 40)];
    }
    self.backgroundView = backgroundView;
    if (@available(iOS 11.0, *)) {
        backgroundView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
    } else {
        backgroundView.backgroundColor = UIColor.whiteColor;
    }
    [self.view addSubview:backgroundView];
    //addTitleView
    UILabel *titleLabel = [[UILabel alloc]init];
    self.titleLabel = titleLabel;
    titleLabel.text = @"查课表";
    titleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:21];
    [self.backgroundView addSubview:titleLabel];
    [titleLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.centerY.equalTo(self.backgroundView);
    }];
    if (@available(iOS 11.0, *)) {
        titleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
        titleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]];
    }
}

//添加推出查课表页的按钮
- (void)addBackButton {
    UIButton *backButton = [[MXObjCBackButton alloc] initWithIsAutoHotspotExpand:YES];
    [self.view addSubview:backButton];
    [backButton setImage:[UIImage imageNamed:@"空教室返回"] forState:UIControlStateNormal];
    [backButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.view).offset(17);
        make.centerY.equalTo(self.titleLabel);
        make.width.equalTo(@9);
        make.height.equalTo(@19);
    }];
    [backButton addTarget:self action: @selector(back) forControlEvents:UIControlEventTouchUpInside];
}

//跳出查课表页的方法
- (void) back {
     [self.navigationController popViewControllerAnimated:YES];
}

//添加一个查询同学课表的VC
- (void)addStudentView{
    ScheduleViewController *stu = [[ScheduleViewController alloc] initWithUserDefaultKey:STU_FIND_HISTORY andPeopleType:PeopleTypeStudent];
    stu.title = @"同学课表";
    stu.delegate = self;
    
    [self addChildViewController:stu];
    [self.view addSubview:stu.view];
    [stu.view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self.backgroundView.mas_bottom);
        make.left.right.equalTo(self.view);
        make.bottom.equalTo(self.view);
    }];
    [stu didMoveToParentViewController:self];
}

//已废弃
//添加带标签栏的一个分页的一个scrollView（即QAListSegmentView）
//- (void)addSegmentView {
//    ScheduleViewController *stu = [[ScheduleViewController alloc] initWithUserDefaultKey:STU_FIND_HISTORY andPeopleType:PeopleTypeStudent];
//    stu.title = @"同学课表";
//    stu.delegate = self;
//    
//    ScheduleViewController *tea = [[ScheduleViewController alloc] initWithUserDefaultKey:TEA_FIND_HISTORY andPeopleType:PeopleTypeTeacher];
//    tea.title = @"老师课表";
//    tea.delegate = self;
//    
//    
//    QAListSegmentView *segmentView = [[QAListSegmentView alloc]initWithFrame:CGRectMake(0, 120, self.view.width, self.view.height-60) controllers:@[stu]];
//    [self.view addSubview:segmentView];
//    if (@available(iOS 11.0, *)) {
//        [segmentView setValue:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]] forKey:@"titleColor"];
//    } else {
//        [segmentView setValue:[UIColor colorWithHexString:@"#15315B"] forKey:@"titleColor"];
//    }
//    
//    [segmentView mas_makeConstraints:^(MASConstraintMaker *make) {
//        make.top.equalTo(self.backgroundView.mas_bottom);
//        make.left.right.width.equalTo(self.backgroundView);
//        make.bottom.equalTo(self.view);
//    }];
//}

//这个是<ScheduleViewControllerDelegate>协议里需要实现的方法，起到跳转到符合条件的人员名单页的功能
- (void)pushToController:(UIViewController *)studentListVC {
    [self.navigationController pushViewController:studentListVC animated:YES];
}

@end
