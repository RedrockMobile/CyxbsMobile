//
//  SportAttendanceViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 潘申冰 on 2022/8/3.
//  Copyright © 2022 Redrock. All rights reserved.
//

#import "SportAttendanceViewController.h"
#import "SportAttendanceModel.h"
#import "SportAttendanceHeadView.h"
#import "SportAttendanceTableViewCell.h"
#import "DateModle.h"
#import "RemindHUD.h"
#import "MJRefresh.h"
#import "掌上重邮-Swift.h"

@interface SportAttendanceViewController ()<UITableViewDelegate, UITableViewDataSource>

/// 详细打卡记录列表
@property (nonatomic, strong) UITableView *sADetailsTableView;
/// SportAttendance数据模型
@property (nonatomic, strong) SportAttendanceModel *sAModel;

@property (nonatomic, strong) UIView *backgroundView;
@property (nonatomic, strong) UILabel *titleLabel;
@property (nonatomic, strong) UILabel *timeLabel;

@property (nonatomic, assign) bool Isholiday;
@property (nonatomic, assign) bool IsLoad;
@end

@implementation SportAttendanceViewController

- (instancetype)init
{
    self = [super init];
    if (self) {
        self.IsLoad = false;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF"] darkColor:[UIColor colorWithHexString:@"#000000"]];
    // 默认为成功页
    [self addSussesView];
    //判断是否假期
    [self judgeHoliday];
    self.IsLoad = false;
    if (self.IsLoad) {
        //数据正确加载
        if (self.sAModel.status == 10000) {
            if (self.Isholiday) {
                [self addHolidayView];
            } else {
                [self addSussesView];
            }
        } else {
            [self addWrongView];
        }
    } else {
        [self loadNewData];
    }
    
}

- (void)viewWillAppear:(BOOL)animated{
    [super viewWillAppear:animated];
}

#pragma mark - UITableViewDataSource
- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView{
    return self.sAModel.sAItemModel.itemAry.count;
}

//设置每个分区的行数
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section{
    return 1;
}

//设置一行高度
- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    return 76;
}

//具体数据
- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath{
    static NSString *reusedId = @"sportAttendance";
    SportAttendanceTableViewCell *cell = (SportAttendanceTableViewCell *) [tableView dequeueReusableHeaderFooterViewWithIdentifier:reusedId];
    if (!cell) {
        cell = [[SportAttendanceTableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:reusedId];
    }
    //禁止点击
    cell.userInteractionEnabled =NO;
    cell.backgroundColor = UIColor.clearColor;
    //   显示所有内容
    cell.sa = self.sAModel.sAItemModel.itemAry[indexPath.section];
    return cell;
}

#pragma mark - getter

- (UITableView *)sADetailsTableView{
    if (!_sADetailsTableView) {
        _sADetailsTableView = [[UITableView alloc] initWithFrame:CGRectMake(0, 240, SCREEN_WIDTH, SCREEN_HEIGHT - 240) style:UITableViewStyleGrouped];
        _sADetailsTableView.delegate = self;
        _sADetailsTableView.dataSource = self;
        _sADetailsTableView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FBFCFF"] darkColor:[UIColor colorWithHexString:@"#1D1D1D"]];
        _sADetailsTableView.separatorStyle = UITableViewCellSeparatorStyleNone;
        _sADetailsTableView.layer.cornerRadius = 20;
        _sADetailsTableView.sectionHeaderHeight = 0;
        _sADetailsTableView.sectionFooterHeight = 8;
        
        MJRefreshGifHeader *header = [MJRefreshGifHeader headerWithRefreshingTarget:self refreshingAction:@selector(loadNewData)];
        // 设置即将刷新状态的动画图片（一松开就会刷新的状态）
        NSMutableArray *refreshingImages = [NSMutableArray array];
        for (NSUInteger i = 1; i<=12; i++) {
            UIImage *image = [UIImage imageNamed:[NSString stringWithFormat:@"sportrefreshIcon%zd", i]];
            [refreshingImages addObject:image];
        }
        // 设置下拉刷新状态的动画图片
        [header setImages:refreshingImages forState:MJRefreshStateIdle];
        // 设置正在刷新状态的动画图片
        [header setImages:refreshingImages forState:MJRefreshStateRefreshing];
        _sADetailsTableView.mj_header = header;
        header.lastUpdatedTimeLabel.hidden = YES;
        header.stateLabel.hidden = YES;
    }
    return _sADetailsTableView;
}

- (SportAttendanceModel *)sAModel{
    if (!_sAModel) {
        _sAModel = [[SportAttendanceModel alloc] init];
    }
    return _sAModel;
}

#pragma mark - 返回条
//自定义的Tabbar
- (void)addCustomTabbarView {
    UIView *backgroundView;
    backgroundView = [[UIView alloc]initWithFrame:CGRectMake(0, NVGBARHEIGHT, SCREEN_WIDTH, STATUSBARHEIGHT)];
    self.backgroundView = backgroundView;
    backgroundView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:0] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:0]];
    [self.view addSubview:backgroundView];
    //addTitleView
    UILabel *titleLabel = [[UILabel alloc]init];
    self.titleLabel = titleLabel;
    titleLabel.text = @"体育打卡";
    titleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:22];
    titleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#112C53"] darkColor:[UIColor colorWithHexString:@"#DFDFE3"]];
    [self.backgroundView addSubview:titleLabel];
    [titleLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.view).offset(37);
        make.centerY.equalTo(self.backgroundView);
    }];
    titleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#DFDFE3" alpha:1]];

    //获取当前月份
    NSDate *currentDate = [NSDate date];
    NSCalendar *calendar = [NSCalendar currentCalendar];
    NSDateComponents *components = [calendar components:NSCalendarUnitYear|NSCalendarUnitMonth|NSCalendarUnitDay fromDate:currentDate];
    long month = [components month];
    long year = [components year];
    
    UILabel *timeLabel = [[UILabel alloc]init];
    timeLabel.text = @"null";
    self.timeLabel = timeLabel;
    
    if (self.Isholiday) {
        timeLabel.text = @"放假中";
    }else{
        if (month > 1 && month < 9) {
            timeLabel.text = [NSString stringWithFormat:@"%ld年 春", year];
        }else{
            timeLabel.text = [NSString stringWithFormat:@"%ld年 秋", year];
        }
    }
    
    timeLabel.font = [UIFont fontWithName:PingFangSCMedium size:12];
    [self.backgroundView addSubview:timeLabel];
    [timeLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.right.equalTo(self.backgroundView).offset(-16);
        make.centerY.equalTo(self.backgroundView);
    }];
    timeLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:0.7] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:0.4]];
    
    //添加返回按钮
    [self addBackButton];
}

//添加退出的按钮
- (void)addBackButton {
    UIButton *backButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [self.backgroundView addSubview:backButton];
    [backButton setImage:[UIImage imageNamed:@"空教室返回"] forState:UIControlStateNormal];
    [backButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.view).offset(17);
        make.centerY.equalTo(self.titleLabel);
        make.width.equalTo(@9);
        make.height.equalTo(@19);
    }];
    [backButton addTarget:self action: @selector(back) forControlEvents:UIControlEventTouchUpInside];
}

//返回的方法
- (void)back {
     [self.navigationController popViewControllerAnimated:YES];
}

#pragma mark - Method
- (void)judgeHoliday {
    //获取当前周数
    int count = [SwiftToOC getNowWeek];
    if (count > 19) {
        _Isholiday = true;
    }else{
        _Isholiday = false;
    }
}

//加载新数据
- (void)loadNewData {
    [self.sAModel requestSuccess:^(bool isCachedData) {
        if (self.Isholiday) {
            [self addHolidayView];
        } else if (self.sAModel.status == 10000) {
            [self addSussesView];
            //无需向前页面回传数据(返回时会自动重新网络请求)
            [self.sADetailsTableView reloadData];
        }
        if (isCachedData) {
            NSString *loadTime = [NSUserDefaults.standardUserDefaults   objectForKey:@"Sprot_Loadtime"];
            NSTimeInterval timeInterval = [loadTime doubleValue];
            if (timeInterval) {
                NSDate *loadDate = [[NSDate alloc] initWithTimeIntervalSince1970:timeInterval];
                NSString *string = [loadDate stringWithFormat:@"MM月dd日 HH:mm"];
                [RemindHUD.shared showDefaultHUDWithText:[NSString stringWithFormat:@"请求失败，为您展示%@的缓存", string] completion:nil];
            }
        }
        } failure:^(NSError * _Nonnull error) {
            NSLog(@"体育打卡刷新失败");
    }];
    
    //伪刷新
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        // 结束刷新
        [self.sADetailsTableView.mj_header endRefreshing];
    });
}

//获取数据成功加载此视图
- (void)addSussesView {
    //先移除所有View
    [self.view removeAllSubviews];
    //添加头视图
    SportAttendanceHeadView *headView = [[SportAttendanceHeadView alloc] init];
    //加载头视图
    [headView loadViewWithDate:self.sAModel Isholiday:self.Isholiday];
    [self.view addSubview:headView];
    //添加返回条
    [self addCustomTabbarView];
    //添加跑步的详情列表
    [self.view addSubview:self.sADetailsTableView];
    self.sADetailsTableView.bounces = YES;
    //判断是否已完成总数为0
    if (self.sAModel.run_done + self.sAModel.other_done == 0) {
        [self addNoRunImg];
    }
}

- (void)addWrongView {
    //先移除所有View
    [self.view removeAllSubviews];
    self.sADetailsTableView.bounces = NO;
    //添加头视图
    SportAttendanceHeadView *headView = [[SportAttendanceHeadView alloc] init];
    //加载头视图
    [headView loadViewWithDate:self.sAModel Isholiday:self.Isholiday];
    [self.view addSubview:headView];
    //添加返回条
    [self addCustomTabbarView];
    //添加跑步的详情列表
    [self.view addSubview:self.sADetailsTableView];
    
    UIImageView *img = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"placeholder.empty.boy_404"]];
    [self.view addSubview:img];
    [img mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(self.view).offset(SCREEN_HEIGHT/2);
        make.width.equalTo(@170);
        make.height.equalTo(@105);
    }];
    
    UILabel *Lab = [[UILabel alloc] init];
    Lab.text = @"数据错误";
    Lab.font = [UIFont fontWithName:PingFangSCMedium size:12];
    Lab.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#112C54"] darkColor:[UIColor colorWithHexString:@"#DFDFE3"]];
    
    [self.view addSubview:Lab];
    [Lab mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(img.mas_bottom).offset(20);
    }];
}

- (void)addNoRunImg {
    UIImageView *img = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"人在手机里"]];
    [self.view addSubview:img];
    [img mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(self.view).offset(SCREEN_HEIGHT/2);
        make.width.equalTo(@171);
        make.height.equalTo(@111);
    }];
    
    UILabel *Lab = [[UILabel alloc] init];
    Lab.text = @"暂时还没记录哦~";
    Lab.font = [UIFont fontWithName:PingFangSCMedium size:12];
    Lab.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#112C54"] darkColor:[UIColor colorWithHexString:@"#DFDFE3"]];
    
    [self.view addSubview:Lab];
    [Lab mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(img.mas_bottom).offset(20);
    }];
}

- (void)addHolidayView {
    //先移除所有View
    [self.view removeAllSubviews];
    self.sAModel = nil;
    self.sADetailsTableView.bounces = NO;
    //添加头视图
    SportAttendanceHeadView *headView = [[SportAttendanceHeadView alloc] init];
    //加载头视图
    [headView loadViewWithDate:self.sAModel Isholiday:self.Isholiday];
    [self.view addSubview:headView];
    //添加返回条
    [self addCustomTabbarView];
    //添加跑步的详情列表
    [self.view addSubview:self.sADetailsTableView];
    
    UIImageView *img = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"放假啦"]];
    [self.view addSubview:img];
    [img mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(self.view).offset(SCREEN_HEIGHT/2);
        make.width.equalTo(@125);
        make.height.equalTo(@110);
    }];
    
    UILabel *Lab = [[UILabel alloc] init];
    Lab.text = @"大家都放假了，好好度假吧！";
    Lab.font = [UIFont fontWithName:PingFangSCMedium size:12];
    Lab.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#112C54"] darkColor:[UIColor colorWithHexString:@"#DFDFE3"]];
    
    [self.view addSubview:Lab];
    [Lab mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(self.view);
        make.top.equalTo(img.mas_bottom).offset(20);
    }];
}

#pragma mark - RisingRouterHandler

+ (NSArray<NSString *> *)routerPath {
    return @[
        @"SportController"
    ];
}

+ (void)responseRequest:(RisingRouterRequest *)request completion:(RisingRouterResponseBlock)completion {
    
    RisingRouterResponse *response = [[RisingRouterResponse alloc] init];
    
    switch (request.requestType) {
        case RouterRequestPush: {
            
            UINavigationController *nav = (request.requestController ? request.requestController : RisingRouterRequest.useTopController).navigationController;
            
            if (nav) {
                SportAttendanceViewController *vc = [[self alloc] init];
                response.responseController = vc;
                
                [nav pushViewController:vc animated:YES];
            } else {
                
                response.errorCode = RouterWithoutNavagation;
            }
            
        } break;
            
        case RouterRequestParameters: {
            // TODO: 传回参数
            
        } break;
            
        case RouterRequestController: {
            
            SportAttendanceViewController *vc = [[self alloc] init];
            SportAttendanceModel *model = request.parameters[@"sportData"];
            vc.sAModel = model;
            vc.IsLoad = true;//数据已经在首页尝试过加载,并且是从首页直接点进来的
            response.responseController = vc;
        } break;
    }
    
    if (completion) {
        completion(response);
    }
}

@end
