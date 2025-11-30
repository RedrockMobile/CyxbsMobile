//
//  EmptyClassViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/4/19.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "EmptyClassViewController.h"
#import "EmptyClassPresenter.h"
#import "EmptyClassContentView.h"
#import "EmptyClassItem.h"
#import "EmptyClassTableViewCell.h"
#import "EmptyClassCollectionViewCell.h"
#import "MJRefresh.h"
#import "掌上重邮-Swift.h"

@interface EmptyClassViewController () <EmptyContentViewDelegate, UITableViewDataSource>

@property (nonatomic, weak) EmptyClassContentView *contentView;

/// 请求体
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSString *> *uploadParams;

/// 选择的周数
@property (nonatomic, assign) NSInteger choosedWeek;

/// 选择的星期
@property (nonatomic, assign) NSInteger choosedWeekday;

/// 选择的课程时间段
@property (nonatomic, strong) NSMutableArray *choosedClassesArray;

/// 选择的教学楼
@property (nonatomic, assign) NSInteger choosedBuilding;

/// 标识参数是否准备完毕
@property (nonatomic, assign) BOOL isParamsReady;

@property (nonatomic, strong) NSMutableArray<EmptyClassItem *> *emptyClassItemArray;

@end

@implementation EmptyClassViewController


#pragma mark - 控制器生命周期
- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.presenter = [[EmptyClassPresenter alloc] init];
    [self.presenter attatchView:self];
    
    // 初始化上传数据
    NSDictionary *params = @{
        @"week": @"",
        @"weekDayNum": @"",
        @"sectionNum": @"",
        @"buildNum": @""
    };
    self.uploadParams = [params mutableCopy];
    
    // 初始化已选择的课程时段数组
    self.choosedClassesArray = [NSMutableArray array];
    
    // 初始化UI界面
    EmptyClassContentView *contentView = [[EmptyClassContentView alloc] initWithFrame:self.view.bounds];
    contentView.delegate = self;
    [self.view addSubview: contentView];
    self.contentView = contentView;
    
    // 配置TableView
    self.contentView.resultTable.dataSource = self;
    self.contentView.resultTable.rowHeight = UITableViewAutomaticDimension;
    self.contentView.resultTable.estimatedRowHeight = 60;
    
    // 注册tableViewCell
    [self.contentView.resultTable registerNib:[UINib nibWithNibName:@"EmptyClassTableViewCell" bundle:nil] forCellReuseIdentifier:@"EmptyClassTableViewCell"];
}

- (void)viewWillAppear:(BOOL)animated {
    self.navigationController.navigationBar.hidden = YES;
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    
    NSInteger nowWeek = [SwiftToOC getNowWeek] - 1;
    if(nowWeek < 0){
        nowWeek = 0;
    }
    
    if (nowWeek >= self.contentView.weekButtonArray.count) {
        NSLog(@"周数索引越界保护: nowWeek=%ld, 数组长度=%lu", nowWeek, (unsigned long)self.contentView.weekButtonArray.count);
        nowWeek = self.contentView.weekButtonArray.count - 1; // 选择最后一个有效的周数
    }
    
    // 获取当前是星期几
    NSInteger today = [NSDate date].weekday;
    if (today == 1) {
        today = 7;
    } else {
        today = today - 1;
    }
    
    NSInteger todayIndex = today - 1;
    if (todayIndex >= self.contentView.weekDayButtonArray.count) {
        NSLog(@"星期索引越界保护: todayIndex=%ld, 数组长度=%lu", todayIndex, (unsigned long)self.contentView.weekDayButtonArray.count);
        todayIndex = self.contentView.weekDayButtonArray.count - 1; // 选择最后一个有效的星期
    }
    
    // 自动选择当前日期
    if (self.contentView.weekButtonArray.count > nowWeek) {
        [self weekButtonChoosed:self.contentView.weekButtonArray[nowWeek]];
    }
    
    if (self.contentView.weekDayButtonArray.count > todayIndex) {
        [self weekdayButtonChoosed:self.contentView.weekDayButtonArray[todayIndex]];
    }
}

- (void)dealloc
{
    [self.presenter dettatchView];
    [self removeObserver:self forKeyPath:@"uploadParams" context:nil];
}


#pragma mark - contentView代理
- (void)backButtonClicked {
    [self.navigationController popViewControllerAnimated:YES];
}

// 选择第几周
- (void)weekButtonChoosed:(UIButton *)sender {
    [UIView animateWithDuration:0.3 animations:^{
        // 改变点击按钮的颜色
        if (self.choosedWeek != 0) {
            self.contentView.weekButtonArray[self.choosedWeek - 1].backgroundColor = [UIColor clearColor];
        }
        if (@available(iOS 11.0, *)) {
            sender.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#DDE2F7" alpha:1] darkColor:[UIColor colorWithHexString:@"#5A5A5A" alpha:1]];
        } else {
            sender.backgroundColor = [UIColor colorWithRed:221/255.0 green:227/255.0 blue:248/255.0 alpha:1.0];
        }
        
        // 自动调整scrollView
        if (sender.tag != 24 && sender.tag != 25 && sender.tag != 1 && sender.tag != 2) {
            self.contentView.weekScrollView.contentOffset = CGPointMake(sender.mj_x - 60, 0);
        } else if (sender.tag == 24 || sender.tag == 25) {
            [self.contentView.weekScrollView scrollToRight];
        }
    }];
    
    self.choosedWeek = sender.tag;
    self.uploadParams[@"week"] = [NSString stringWithFormat:@"%ld", sender.tag];
    [self checkParams];
}

// 选择星期
- (void)weekdayButtonChoosed:(UIButton *)sender {
    // 更改选中的按钮的颜色
    [UIView animateWithDuration:0.3 animations:^{
        if (self.choosedWeekday != 0) {
            self.contentView.weekDayButtonArray[self.choosedWeekday - 1].backgroundColor = [UIColor clearColor];
        }
        if (@available(iOS 11.0, *)) {
            sender.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#DDE2F7" alpha:1] darkColor:[UIColor colorWithHexString:@"#5A5A5A" alpha:1]];
        } else {
            sender.backgroundColor = [UIColor colorWithRed:221/255.0 green:227/255.0 blue:248/255.0 alpha:1.0];
        }
    }];
    
    self.choosedWeekday = sender.tag;
    self.uploadParams[@"weekDayNum"] = [NSString stringWithFormat:@"%ld", sender.tag];
    [self checkParams];
}

// 选择时段
- (void)classButtonChoosed:(UIButton *)sender {
    sender.selected = !sender.selected;
    
    // 更改选中的按钮的颜色
    [UIView animateWithDuration:0.3 animations:^{
        if (sender.selected) {
            if (@available(iOS 11.0, *)) {
                sender.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#DDE2F7" alpha:1] darkColor:[UIColor colorWithHexString:@"#5A5A5A" alpha:1]];
            } else {
                sender.backgroundColor = [UIColor colorWithRed:221/255.0 green:227/255.0 blue:248/255.0 alpha:1.0];
            }
        } else {
            sender.backgroundColor = [UIColor clearColor];
        }
    }];
    
    NSString *data = [NSString stringWithFormat:@"%ld", sender.tag];
    
    // 取消选择时，从数组里删除该选项，然后发送请求
    if (!sender.selected) {
        [self.choosedClassesArray removeObject:[NSString stringWithFormat:@"%ld", sender.tag]];
        self.uploadParams[@"sectionNum"] = [self.choosedClassesArray componentsJoinedByString:@", "];
        
        if (self.choosedClassesArray.count == 0) {      // 如果全部取消，清空数据
            [self.emptyClassItemArray removeAllObjects];
            [self.contentView.resultTable reloadData];
            return;
        }
        
        [self checkParams];
        return;
    }
    
    if (![self.choosedClassesArray containsObject:data]) {
        [self.choosedClassesArray addObject:[NSString stringWithFormat:@"%ld", sender.tag]];
        self.uploadParams[@"sectionNum"] = [self.choosedClassesArray componentsJoinedByString:@", "];
    }
    
    [self checkParams];
}

// 选择教学楼
- (void)buildingButtonChoosed:(UIButton *)sender {
    // 楼栋号和编号的映射
    NSDictionary<NSNumber *, NSNumber *> *buildingReflection = @{
        @2: @0,
        @3: @1,
        @4: @2,
        @5: @3,
        @8: @4
    };
    
    // 更改选中的按钮的颜色
    [UIView animateWithDuration:0.3 animations:^{
        if (self.choosedBuilding != 0) {
            self.contentView.buildingButtonArray[[buildingReflection[@(self.choosedBuilding)] intValue]].backgroundColor = [UIColor clearColor];
        }

        if (@available(iOS 11.0, *)) {
            sender.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#DDE2F7" alpha:1] darkColor:[UIColor colorWithHexString:@"#5A5A5A" alpha:1]];
        } else {
            sender.backgroundColor = [UIColor colorWithRed:221/255.0 green:227/255.0 blue:248/255.0 alpha:1.0];
        }

    }];
    
    self.choosedBuilding = sender.tag;
    self.uploadParams[@"buildNum"] = [NSString stringWithFormat:@"%ld", sender.tag];
    [self checkParams];
}

// 检查参数完整性并发送请求
- (void)checkParams {
    if (self.uploadParams[@"week"].length != 0 && self.uploadParams[@"weekDayNum"].length != 0 && self.uploadParams[@"sectionNum"].length != 0 && self.uploadParams[@"buildNum"].length != 0) {
        [self.presenter requestEmptyClassRoomDataWithParams:self.uploadParams];
    }
}


#pragma mark - TableView数据源
- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return self.emptyClassItemArray.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    EmptyClassTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"EmptyClassTableViewCell"];
    cell.selectionStyle = UITableViewCellSelectionStyleNone;
    
    cell.item = self.emptyClassItemArray[indexPath.row];
    [cell.roomsCollectionView reloadData];

    switch ([self.emptyClassItemArray[indexPath.row].floorNum intValue]) {
        case 1:
            cell.floorLabel.text = @"一楼";
            break;
            
        case 2:
            cell.floorLabel.text = @"二楼";
            break;
            
        case 3:
            cell.floorLabel.text = @"三楼";
            break;
            
        case 4:
            cell.floorLabel.text = @"四楼";
            break;
            
        case 5:
            cell.floorLabel.text = @"五楼";
            break;
            
        case 6:
            cell.floorLabel.text = @"六楼";
            break;
            
        default:
            break;
    }
        
    return cell;
}


#pragma mark - Presenter回调
- (void)emptyClassRoomDataRequestsSuccess:(NSArray<EmptyClassItem *> *)itemsArray {
    self.emptyClassItemArray = [itemsArray mutableCopy];
    [self.contentView.resultTable reloadData];
}

- (void)emptyClassRoomDataRequestsfailure:(NSError *)error {
    MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
    hud.mode = MBProgressHUDModeText;
    hud.labelText = @"Σ（ﾟдﾟlll）请求失败了...";
    [hud hide:YES afterDelay:1];
}

@end
