//
//  FinderToolViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2019/11/15.
//  Copyright © 2019 Redrock. All rights reserved.
//

#import "FinderToolViewController.h"
#import "FinderToolViewItem.h"
#import "ScheduleInquiryViewController.h"
#import "SchoolBusVC.h"
#import "CalendarViewController.h"
//#import "WeDateViewController.h"
#import "CQUPTMapViewController.h"
//#import "TODOMainViewController.h"
#import "TestArrangeViewController.h"
#import "SportAttendanceViewController.h"
#import "EmptyClassViewController.h"
#import "掌上重邮-Swift.h"

@interface FinderToolViewController () <
    UIScrollViewDelegate
>

@property (nonatomic, weak) UIScrollView *scrollView;
@property (nonatomic, weak) UIView *viewContainer;
@property (nonatomic) NSArray<FinderToolViewItem *> *toolViewItems;
@property (nonatomic, weak) UILabel *toolTitle;
@property (nonatomic, weak) UIButton *settingButton;
@property (nonatomic, weak) UIButton *OKButton;    //选择结束
@property (nonatomic, weak) UIButton *backButton;  //返回按钮

@end

@implementation FinderToolViewController

#pragma mark - Life cycle

- (void)viewDidLoad {
    [super viewDidLoad];
    [self addBackButton];
    [self addToolTitle];
    [self addSettingButton];
    [self addContentView];  // 添加底层的ScrollView
    [self addViewContainer];
    [self configNavigationBar];
    [self addToolViewItems];  // 将每个工具添加到当前页面

    if (@available(iOS 11.0, *)) {
        self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
    } else {
        // Fallback on earlier versions
    }
    [self layoutItems];

}

#pragma mark - Method

- (void)addBackButton {
    UIButton *button = [[UIButton alloc]init];
    [self.view addSubview:button];
    self.backButton = button;
    [button setImage:[UIImage imageNamed:@"LQQBackButton"] forState:normal];
    [button setImage: [UIImage imageNamed:@"EmptyClassBackButton"] forState:UIControlStateHighlighted];
    [button mas_makeConstraints:^(MASConstraintMaker *make) {
        make.width.equalTo(@30);
        make.height.equalTo(@30);
        make.top.equalTo(self.view).offset(STATUSBARHEIGHT +15 );
        make.left.equalTo(self.view).offset(8.6);
    }];
    [button setImageEdgeInsets:UIEdgeInsetsMake(6, 10, 6, 10)];//增大点击范围
    [button addTarget:self action:@selector(popController) forControlEvents:UIControlEventTouchUpInside];
}

- (void)popController {
    [self.navigationController popViewControllerAnimated:YES];
    self.navigationController.navigationBar.hidden = NO;
}


- (void)addContentView {
    UIScrollView *contentView = [[UIScrollView alloc]init];
    self.scrollView = contentView;
    self.scrollView.delegate = self;
    if (@available(iOS 11.0, *)) {
        contentView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
    } else {
        contentView.backgroundColor = [UIColor colorWithRed:242/255.0 green:243/255.0 blue:248/255.0 alpha:1];
    }

    [self.view addSubview:contentView];
    [self.scrollView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self.toolTitle.mas_bottom).offset(6.5);
        make.left.right.bottom.equalTo(self.view);
    }];
}

- (void)addViewContainer {
    UIView *viewContainer = [[UIView alloc]init];
    [self.scrollView addSubview:viewContainer];
    self.viewContainer = viewContainer;
    [viewContainer mas_makeConstraints:^(MASConstraintMaker *make) {
        make.edges.equalTo(self.scrollView);
        make.width.equalTo(self.scrollView);
    }];

}

- (void)configNavigationBar {
    self.navigationController.navigationBar.hidden = YES;
}

- (void)addToolTitle {
    UILabel *toolTitle = [[UILabel alloc]init];
    self.toolTitle = toolTitle;
    toolTitle.text = @"工具";
    toolTitle.font = [UIFont fontWithName:PingFangSCSemibold size: 34];
    if (@available(iOS 11.0, *)) {
        toolTitle.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
        // Fallback on earlier versions
    }
    [self.view addSubview:toolTitle];
    [toolTitle mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backButton).offset(-1.5);
        make.top.equalTo(self.backButton.mas_bottom).offset(6.5);
    }];
}

- (void)addSettingButton {
    UIButton *button = [[UIButton alloc]init];
    self.settingButton = button;
    [self.view addSubview:button];
    [button addTarget:self action:@selector(customizeMainPageUI) forControlEvents:UIControlEventTouchUpInside];
    [button setImage:[UIImage imageNamed:@"LQQsetting"] forState:normal];
    [button mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerY.equalTo(self.toolTitle);
        make.right.equalTo(self.view).offset(-15.32);
        make.width.equalTo(@23.4);
        make.height.equalTo(@25.82);
    }];
}

- (void)customizeMainPageUI {
    [self.settingButton setHidden:YES];
    UIButton *OKButton = [[UIButton alloc]init];
    self.OKButton = OKButton;
    [OKButton setImage:[UIImage imageNamed:@"toolPageRight"] forState:normal];
    [self.view addSubview:OKButton];
    [OKButton addTarget:self action:@selector(chooseCompleted) forControlEvents:UIControlEventTouchUpInside];
    [OKButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.centerY.equalTo(self.settingButton);
        make.width.height.equalTo(@29);
    }];
    
    for (FinderToolViewItem*item in self.toolViewItems) {
        item.isChooingNow = YES;
        item.isFavorite = NO;
    }
    
}

-(void)chooseCompleted {
    int fav = 0;//标记是否选择了三个
    for (FinderToolViewItem*item in self.toolViewItems) {
        if (item.isFavorite == YES) {
            fav++;
        }
    }
    if (fav == 0) {
        [self.OKButton removeFromSuperview];
        [self.settingButton setHidden:NO];
        for (FinderToolViewItem*item in self.toolViewItems) {
            item.isChooingNow = NO;
            item.isFavorite = NO;
        }
        return;
    }
    if (fav != 3) {
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
        hud.mode = MBProgressHUDModeText;
        hud.labelText = @"只能选择三个哦";
        [hud hide:YES afterDelay:1];
    } else {
        [self.OKButton removeFromSuperview];
        [self.settingButton setHidden:NO];
        for (FinderToolViewItem*item in self.toolViewItems) {
            item.isChooingNow = NO;
        }
        [self writeUserFavoriteToolToPropertylist];//将用户喜欢的三个控件写入本地缓存文件
        for (FinderToolViewItem*item in self.toolViewItems) {
            item.isFavorite = NO;
            [item changeBackgroundColorIfNeeded];
        }
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
        hud.mode = MBProgressHUDModeText;
        hud.labelText = @"自定义主页控件成功";
        [hud hide:YES afterDelay:1];
        //发送消息给DiscoverMainView更新finderView视图
        [[NSNotificationCenter defaultCenter] postNotificationName:@"customizeMainPageViewSuccess" object:nil];
    }
}

- (void)writeUserFavoriteToolToPropertylist {
    NSMutableArray<NSString*>*array = [NSMutableArray array];
    for (FinderToolViewItem*item in self.toolViewItems) {
        if (item.isFavorite == YES){
            [array addObject:item.title];
        }
    }
    [NSUserDefaults.standardUserDefaults setObject:array forKey:@"ToolPage_UserFavoriteToolsName"];
}

- (void)addToolViewItems {
    FinderToolViewItem *item1 = [[FinderToolViewItem alloc]initWithIconView:@"没课约" Title:@"没课约" Detail:@"多人空课表同步查询"];
    FinderToolViewItem *item2 = [[FinderToolViewItem alloc]initWithIconView:@"校车轨迹" Title:@"校车轨迹" Detail:@"校园观光车轨迹路线实时查看"];
    FinderToolViewItem *item3 = [[FinderToolViewItem alloc]initWithIconView:@"查课表" Title:@"查课表" Detail:@"同学、老师课表快捷查询"];
    FinderToolViewItem *item4 = [[FinderToolViewItem alloc]initWithIconView:@"校历" Title:@"校历" Detail:@"学期安排一目了然"];
    FinderToolViewItem *item5 = [[FinderToolViewItem alloc]initWithIconView:@"重邮地图" Title:@"重邮地图" Detail:@"校园地图，尽收重邮风光"];
    FinderToolViewItem *item6 = [[FinderToolViewItem alloc] initWithIconView:@"邮子清单" Title:@"邮子清单" Detail:@"邮子清单"];
//    FinderToolViewItem *item7 = [[FinderToolViewItem alloc]initWithIconView:@"我的考试" Title:@"我的考试" Detail:@"考试安排、成绩学分轻松查询"];
    FinderToolViewItem *item8 = [[FinderToolViewItem alloc] initWithIconView:@"体育打卡" Title:@"体育打卡" Detail:@"体育打卡"];
    FinderToolViewItem *item9 = [[FinderToolViewItem alloc]initWithIconView:@"空教室" Title:@"空教室" Detail:@"空余教室及时查询"];
    FinderToolViewItem *item10 = [[FinderToolViewItem alloc]initWithIconView:@"更多功能" Title:@"更多功能" Detail:@"敬请期待"];
    [item1 addTarget:self action:@selector(chooseWeDate:) forControlEvents:UIControlEventTouchUpInside];
    [item2 addTarget:self action:@selector(chooseSchoolBus:) forControlEvents:UIControlEventTouchUpInside];
    [item3 addTarget:self action:@selector(chooseScheduleInquiry:) forControlEvents:UIControlEventTouchUpInside];
    [item4 addTarget:self action:@selector(chooseSchoolSchedule:) forControlEvents:UIControlEventTouchUpInside];
    [item5 addTarget:self action:@selector(chooseCQUPTMap:) forControlEvents:UIControlEventTouchUpInside];
    [item6 addTarget:self action:@selector(chooseToDo:) forControlEvents:UIControlEventTouchUpInside];
//    [item7 addTarget:self action:@selector(chooseTestArrange:) forControlEvents:UIControlEventTouchUpInside];
    [item8 addTarget:self action:@selector(chooseSportAttendance:) forControlEvents:UIControlEventTouchUpInside];
    [item9 addTarget:self action:@selector(chooseEmptyClassRoom:) forControlEvents:UIControlEventTouchUpInside];
    
    NSMutableArray *itemsArray = [NSMutableArray array];
    [itemsArray addObject:item1];
    [itemsArray addObject:item2];
    [itemsArray addObject:item3];
    [itemsArray addObject:item4];
    [itemsArray addObject:item5];
    [itemsArray addObject:item6];
//    [itemsArray addObject:item7];
    [itemsArray addObject:item8];
    [itemsArray addObject:item9];
    [itemsArray addObject:item10];
    
    self.toolViewItems = itemsArray;
    for (FinderToolViewItem*item in self.toolViewItems) {
        [self.viewContainer addSubview:item];
    }
}

- (void)layoutItems {
    int times = 0;//用来记录当前正在遍历第几个
    for (FinderToolViewItem*item in self.toolViewItems) {
        [self.viewContainer addSubview:item];
        if(item == self.toolViewItems[0]) {
            [item mas_makeConstraints:^(MASConstraintMaker *make) {
                make.top.equalTo(self.viewContainer).offset(6.5);
                make.left.equalTo(self.viewContainer).offset(16);
                make.height.equalTo(@224);
                make.width.equalTo(self.view).multipliedBy(168/375.0);
            }];
        }else if (item == self.toolViewItems[1]) {
            [item mas_makeConstraints:^(MASConstraintMaker *make) {
                make.top.equalTo(self.toolViewItems[0]);
                make.right.equalTo(self.view).offset(-16);
                make.height.equalTo(@255);
                make.left.equalTo(self.toolViewItems[0].mas_right).offset(8);
            }];
        }else {
            [item mas_makeConstraints:^(MASConstraintMaker *make) {
                make.top.equalTo(self.toolViewItems[times-2].mas_bottom).offset(8);
                make.left.right.equalTo(self.toolViewItems[times-2]);
                make.height.equalTo(self.toolViewItems[0]);
            }];
            if(item == self.toolViewItems.lastObject) {
                [item mas_makeConstraints:^(MASConstraintMaker *make) {
                    make.bottom.equalTo(self.viewContainer).offset(-20);
                }];
            }
        }
        times++;
    }
}

// MARK: SEL

/// 没课约
- (void)chooseWeDate:(FinderToolViewItem *)sender {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        //点击了没课约
        WeDateVC *vc = [[WeDateVC alloc] init];
        [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 空课表
- (void)chooseScheduleInquiry:(FinderToolViewItem *)sender {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        //点击了空课表
        ScheduleInquiryViewController *vc = [[ScheduleInquiryViewController alloc] init];
        [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 校车定位
- (void)chooseSchoolBus:(FinderToolViewItem *)sender  {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        SchoolBusVC *vc = [[SchoolBusVC alloc]init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
        
    }
}

/// 校历
- (void)chooseSchoolSchedule:(FinderToolViewItem *)sender {
    if (sender.isChooingNow == YES) {
           [sender toggleFavoriteStates];
    } else {
           CalendarViewController *vc = [[CalendarViewController alloc]init];
           vc.hidesBottomBarWhenPushed = YES;
           [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 重邮地图
- (void)chooseCQUPTMap:(FinderToolViewItem *)sender {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        CQUPTMapViewController *vc = [[CQUPTMapViewController alloc] init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 邮子清单
- (void)chooseToDo:(FinderToolViewItem *)sender{
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        ToDoVC *vc = [[ToDoVC alloc] init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 我的考试
- (void)chooseTestArrange:(FinderToolViewItem *)sender  {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    }else {
        TestArrangeViewController *vc = [[TestArrangeViewController alloc] init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
    }
}

/// 体育打卡
- (void)chooseSportAttendance:(FinderToolViewItem *)sender{
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    } else {
        SportAttendanceViewController *vc = [[SportAttendanceViewController alloc] init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
    }
}
/// 空教室
- (void)chooseEmptyClassRoom:(FinderToolViewItem *)sender  {
    if (sender.isChooingNow == YES) {
        [sender toggleFavoriteStates];
    }else {
        EmptyClassViewController *vc = [[EmptyClassViewController alloc] init];
        vc.hidesBottomBarWhenPushed = YES;
        [self.navigationController pushViewController:vc animated:YES];
    }
}

#pragma mark - UIScrollViewDelegate

- (void)scrollViewDidScroll:(UIScrollView *)scrollView {
    if (scrollView.contentOffset.y >= self.navigationController.navigationBar.height + self.toolTitle.height){
        if (@available(iOS 11.0, *)) {
            [self.navigationController.navigationBar setTitleTextAttributes:@{NSForegroundColorAttributeName:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]]}];
        } else {
            // Fallback on earlier versions
        }
    }else{
        if (@available(iOS 11.0, *)) {
            [self.navigationController.navigationBar setTitleTextAttributes:@{NSForegroundColorAttributeName:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]]}];
        } else {
            // Fallback on earlier versions
        }
    }
}

@end
