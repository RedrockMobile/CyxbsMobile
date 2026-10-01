//
//  ClassScheduleTabBarView.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/3/29.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "ClassScheduleTabBarView.h"
//#import "WYCClassBookViewController.h"
#import "TransitionManager.h"
#import "FakeTabBarView.h"
#import "UserDefaultTool.h"

@interface ClassScheduleTabBarView ()

@property (nonatomic, weak) UIView *bottomCoverView;
@property (nonatomic, strong) NSDictionary *dic;
@property (nonatomic, weak) UIView *dragHintView;
@property (nonatomic, assign)BOOL isPresenting;
@property (nonatomic, strong)UINavigationController *nav;
@property (nonatomic, assign)BOOL isInitingMySchedul;

/// 上拉弹出课表的手势
@property (nonatomic,strong)UIPanGestureRecognizer *PGR;
@property (nonatomic,strong)TransitionManager *TM;

@end

@implementation ClassScheduleTabBarView

- (instancetype)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (self) {
        if (@available(iOS 11.0, *)) {
            self.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#2D2D2D" alpha:1]];
               } else {
                  self.backgroundColor = [UIColor whiteColor];
               }
        [[NSNotificationCenter defaultCenter] addObserver:self
                            selector:@selector(hideBottom)
                            name:@"HideBottomClassScheduleTabBarView"
                            object:nil];
        
        [[NSNotificationCenter defaultCenter] addObserver:self
                            selector:@selector(showBottom)
                            name:@"ShowBottomClassScheduleTabBarView"
                            object:nil];
        
        self.layer.shadowOffset = CGSizeMake(0, -5);
        self.layer.shadowOpacity = 0.05;
        
        self.TM = [[TransitionManager alloc] init];
        
        // 遮住下面两个圆角
        UIView *bottomCoverView = [[UIView alloc] init];
        bottomCoverView.backgroundColor = self.backgroundColor;
        [self addSubview:bottomCoverView];
        self.bottomCoverView = bottomCoverView;
        
        UIView *dragHintView = [[UIView alloc] init];
        
        if (@available(iOS 11.0, *)) {
            dragHintView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#E2EDFB" alpha:1] darkColor:[UIColor colorWithHexString:@"#010101" alpha:1]];
        } else {
            // Fallback on earlier versions
            dragHintView.backgroundColor = [UIColor whiteColor];
        }
        dragHintView.layer.cornerRadius = 2.5;
        [self addSubview:dragHintView];
        self.dragHintView = dragHintView;
        
        
        FYHCycleLabel *classLabel = [[FYHCycleLabel alloc] initWithFrame:CGRectMake(10, 10, 0.3*MAIN_SCREEN_W, 50)];
        classLabel.cycleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:22];
        [self addSubview:classLabel];
        self.classLabel = classLabel;
        
        
        
        UIImageView *clockImageView = [[UIImageView alloc] init];
        [clockImageView setImage:[UIImage imageNamed:@"nowClassTime"]];
        [self addSubview:clockImageView];
        self.clockImageView = clockImageView;
        
        
        FYHCycleLabel *classTimeLabel = [[FYHCycleLabel alloc] initWithFrame:CGRectMake(10, 10, 0.2*MAIN_SCREEN_W, 50)];
        classTimeLabel.cycleLabel.font = [UIFont fontWithName:PingFangSCLight size:12];
        [self addSubview:classTimeLabel];
        self.classTimeLabel = classTimeLabel;
        
        
        UIImageView *locationImageView = [[UIImageView alloc] init];
        [locationImageView setImage:[UIImage imageNamed:@"nowLocation"]];
        [self addSubview:locationImageView];
        self.locationImageView = locationImageView;
        
        
        FYHCycleLabel *classroomLabel = [[FYHCycleLabel alloc] initWithFrame:CGRectMake(10, 10, 0.2*MAIN_SCREEN_W, 50)];
        classroomLabel.cycleLabel.font = [UIFont fontWithName:PingFangSCLight size:12];
        [self addSubview:classroomLabel];
        self.classroomLabel = classroomLabel;
        
        
        //统一改一下label字色和字
        if (@available(iOS 11.0, *)) {
            classTimeLabel.cycleLabel.textColor =
            classroomLabel.cycleLabel.textColor =
            classLabel.cycleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
        } else {
            classTimeLabel.cycleLabel.textColor =
            classroomLabel.cycleLabel.textColor =
            classLabel.cycleLabel.textColor =
            [UIColor colorWithRed:21/255.0 green:49/255.0 blue:91/255.0 alpha:1];
        }
//        [self layoutSubviews];
        self.classroomLabel.labelText =
        self.classTimeLabel.labelText =
        self.classLabel.labelText = @"加载数据中..";
        
        //加上登录成功通知
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(initMySchedul)
            name:@"Login_LoginSuceeded" object:nil];
        
//        UserItem *item = [UserItem defaultItem];
        //如果真实姓名非空，那么已登录

//        if(item.realName!=nil&&![item.realName isEqualToString:@""]){
//            [self initMySchedul];
//        }
        if([UserDefaultTool getStuNum] != nil && [UserDefaultTool getIdNum] != nil && ![[UserDefaultTool getStuNum]isEqualToString:@""] && ![[UserDefaultTool getIdNum]isEqualToString:@""]){

            [self initMySchedul];
        }
    }
    return self;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    [self.bottomCoverView mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.leading.trailing.bottom.equalTo(self);
        make.height.equalTo(@16);
    }];
    
    [self.dragHintView mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.width.equalTo(@27);
        make.height.equalTo(@5);
        make.top.equalTo(self).offset(8);
        make.centerX.equalTo(self);
    }];
    
    [self.classLabel mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(MAIN_SCREEN_W*0.0774);
        make.centerY.equalTo(self);
        make.width.mas_equalTo(0.3*MAIN_SCREEN_W);
        make.height.mas_equalTo(0.13*MAIN_SCREEN_W);
    }];
    
    [self.clockImageView mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(MAIN_SCREEN_W*0.4054);
        make.centerY.equalTo(self.classLabel);
        make.height.width.equalTo(@11);
    }];
    
    [self.classTimeLabel mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(MAIN_SCREEN_W*0.4554);
        make.centerY.equalTo(self.classLabel);
        make.width.mas_equalTo(0.1867*MAIN_SCREEN_W);
        make.height.mas_equalTo(0.13*MAIN_SCREEN_W);
    }];
    
    [self.locationImageView mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(MAIN_SCREEN_W*0.6694);
        make.centerY.equalTo(self.classLabel);
        make.height.width.equalTo(@11);
    }];
    
    [self.classroomLabel mas_remakeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(MAIN_SCREEN_W * 0.7214);
        make.centerY.equalTo(self.classLabel);
        make.width.mas_equalTo(0.224*MAIN_SCREEN_W);
        make.height.mas_equalTo(MAIN_SCREEN_W*0.13);
    }];
}

/// 更新下节课展示；原设置页的课前提醒已迁移至 CMP 提醒服务，不再在此注册通知。
/// @param paramDict 下节课的数据字典
- (void)updateSchedulTabBarViewWithDic:(NSDictionary *)paramDict{
    if(paramDict.count > 1){
        if( [paramDict[@"is"] intValue] == 1){//有下一节课
            self.classroomLabel.labelText = paramDict[@"classroomLabel"];
            self.classTimeLabel.labelText = paramDict[@"classTimeLabel"];
            self.classLabel.labelText = paramDict[@"classLabel"];
        }else{//无下一节课
            self.classroomLabel.labelText = @"---";
            self.classTimeLabel.labelText = @"---";
            self.classLabel.labelText = @"无课了";
        }
    }else{
        self.classroomLabel.labelText = @"无网了";
        self.classTimeLabel.labelText = @"无网了";
        self.classLabel.labelText = @"联网才能使用";
    }
    
}
/// 添加一个上拉后显示课表的手势和点击后显示课表的手势
- (void)addGesture{
    //上拉后显示课表
    UIPanGestureRecognizer *PGR = [[UIPanGestureRecognizer alloc]initWithTarget:self action:@selector(presentMySchedul)];
    self.PGR = PGR;
    [self addGestureRecognizer:PGR];
    
    //点击后显示课表
    UITapGestureRecognizer *TGR = [[UITapGestureRecognizer alloc] initWithActionBlock:^(id  _Nonnull sender) {
        self.mySchedul.fakeBar.alpha = 0;
        [self.viewController presentViewController:self.mySchedul animated:YES completion:nil];
    }];
    [self addGestureRecognizer:TGR];
}

/// 弹出课表的方法
- (void)presentMySchedul{
    if(self.PGR.state==UIGestureRecognizerStateBegan){
        self.TM.PGRToInitTransition = self.PGR;
        [self.viewController presentViewController:self.mySchedul animated:YES completion:^{
            self.TM.PGRToInitTransition = nil;
        } ];
    }
}
/// 初始化课表，课表控制器是这个类的一个属性
- (void)initMySchedul{
    /*
    if(self.isInitingMySchedul==YES)return;
    self.isInitingMySchedul = YES;
    self.mySchedul = [[WYCClassBookViewController alloc] init];
    
    self.mySchedul.schedulTabBar = self;
    
    self.mySchedul.idNum = [UserDefaultTool getIdNum];
    
    self.mySchedul.stuNum = [UserDefaultTool getStuNum];
    
    self.mySchedul.schedulType = ScheduleTypePersonal;
    
    WYCClassAndRemindDataModel *model = [[WYCClassAndRemindDataModel alloc]init];
    
    self.mySchedul.model = model;
    
    model.delegate = self.mySchedul;
    
    [model getPersonalClassBookArrayWithStuNum:self.mySchedul.stuNum];
    
    self.mySchedul.transitioningDelegate = self.TM;
    
    [self.mySchedul setModalPresentationStyle:(UIModalPresentationCustom)];
    
    [self addGesture];
    self.isInitingMySchedul = NO;
     */
    
    if(self.isInitingMySchedul==YES)return;
    self.isInitingMySchedul = YES;
    self.mySchedul = [[WYCClassBookViewController alloc] initWithType:ScheduleTypePersonal andInfo:[UserDefaultTool getStuNum]];
    
    self.mySchedul.schedulTabBar = self;
    
    self.mySchedul.transitioningDelegate = self.TM;
    [self.mySchedul setModalPresentationStyle:(UIModalPresentationCustom)];
    [self addGesture];
    self.isInitingMySchedul = NO;
}


- (void)hideBottom {
    self.userInteractionEnabled = NO;
    self.alpha = 0;
}

- (void)showBottom {
    self.userInteractionEnabled = YES;
    self.alpha = 1;
}

@end
