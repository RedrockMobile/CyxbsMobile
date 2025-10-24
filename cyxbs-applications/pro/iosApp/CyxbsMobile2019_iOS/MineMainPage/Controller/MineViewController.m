//
//  MineViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by Stove on 2021/9/21.
//  Copyright © 2021 Redrock. All rights reserved.
//

//是否开启CCLog
#define CCLogEnable 1

#import "MineViewController.h"
#import "MineTopBlurView.h" //顶部半透明
#import "MineSignView.h" //签到那一块的view
#import "MineMSSEnterBtn.h" //消息中心、邮票中心、消息中心的按钮
#import "MineTableViewCell.h" //底部的cell(关于我们/设置)
#import "EditMyInfoModel.h" //编辑个人信息
#import "MineAboutController.h" //关于我们
#import "MineSettingViewController.h" //设置
#import "EditMyInfoViewController.h" //个人信息编辑
#import "MineUserInfoModel.h" //点赞获赞评论新消息个数相关
#import "StampCenterVC.h" //邮票中心
#import "FeedBackMainPageViewController.h" //意见反馈
#import "CheckInModel.h" //签到的网络请求

#import "MineMessageVC.h"//消息中心模块by ssr，将接入router技术
#import "RemindHUD.h"
#import "CheckInViewController.h"

//获取用户关注的人和粉丝的个人信息
#define fansAndFollowsInfo @"/magipoke-loop/user/fansAndFollowsInfo"
// swift (将Swift中的类暴露给OC)
#import "掌上重邮-Swift.h"

@interface MineViewController ()<
    UITableViewDelegate,
    UITableViewDataSource,
    UINavigationControllerDelegate
>

/// 整个页面背后的 scrollView
@property(nonatomic, strong)UIScrollView *scrollView;

/// 放在 scrollView 上面，用来容纳其它 view
@property(nonatomic, strong)UIView *contentView;

/// 背景图片
@property(nonatomic, strong)UIImageView *backImgView;

/// 顶部带有模糊效果的view
@property(nonatomic, strong)MineTopBlurView *blurView;

/// 顶部带有模糊效果的view 的下面带圆角效果的背景板
@property(nonatomic, strong)UIView *backBoardView;

/// 消息中心入口按钮
@property(nonatomic, strong)MineMSSEnterBtn *msgCenterBtn;

@property(nonatomic, strong)UIView *redDotView;

@property(nonatomic, strong)UILabel *messageCountLabel;

/// 邮票中心入口按钮
@property(nonatomic, strong)MineMSSEnterBtn *stampCenterBtn;

/// 意见与反馈入口按钮
@property(nonatomic, strong)MineMSSEnterBtn *suggesstionBtn;

/// 活动中心按钮
@property(nonatomic, strong)MineMSSEnterBtn *activityCenterBtn;

/// 签到相关的一块 view
@property(nonatomic, strong)MineSignView *signView;

/// 签到相关的一块 view 下面的 tableView
@property(nonatomic, strong)UITableView *tableView;

/// 挡在底部，用来避免因为 tabbar 半透明而导致的 tabbar 颜色变化
@property(nonatomic, strong)UIView *bottomView;

/// 个人信息相关的model
@property(nonatomic, strong)MineUserInfoModel *userInfoModel;

/// 因为请求个人信息用了两个借口，所以用这个属性请求个人信息失败的次数，当失败两次后进行弹窗提醒
@property(nonatomic, assign)int failureCnt;

/// 是否可以请求个人信息，避免因为网络有问题时反复进行网络请求，影响用户体验
@property(nonatomic, assign)BOOL canRequestUserInfo;

@end

@implementation MineViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.navigationController.navigationBarHidden = YES;
    self.view.backgroundColor = [UIColor dm_colorWithLightColor:RGBColor(243, 246, 254, 1) darkColor:RGBColor(0, 1, 1, 1)];
    self.canRequestUserInfo = YES;
    CCLog(@"%@", [UserItem defaultItem]);
    [self addBottomView];
    [self addScrollView];
    [self addContentView];
    [self addBackImgView];
    [self addBlurView];
    [self addBackBoardView];
    [self addMsgCenterBtn];
    [self addStampCenterBtn];
    [self addSuggesstionBtn];
    [self addActivityCenterBtn];
    [self addSignView];
    [self addTableView];
    
    self.userInfoModel = [MineUserInfoModel shareModel];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(userItemDidUpdate:) name:@"UserItemGetUserInfo" object:nil];
//    [self updateUserInfoInUserInfoModel];
    [self updateUserInfoInUserItem];
}

- (void)updateUserInfoInUserItem {
    [CheckInModel requestCheckInInfoSucceeded:nil Failed:nil];
    UserItem *item = [UserItem defaultItem];
    MineTopBlurView *blurView = self.blurView;
    [blurView.headImgBtn sd_setImageWithURL:[NSURL URLWithString:item.headImgUrl] forState:UIControlStateNormal];
    blurView.realNameLabel.text = item.realName;
    blurView.introductionLabel.text = @"快来红岩网校和我一起玩吧⁓";
    [self.signView setSignDay:item.checkInDay];
    BOOL canCheckIn = !item.isCheckedToday;
    [self.signView setSignBtnEnable:canCheckIn];
}

//- (void)updateUserInfoInUserInfoModel {
//    MineUserInfoModel *model = self.userInfoModel;
//    [self.blurView.blogBtn setTitle:[NSString stringWithFormat:@"%ld", model.blogCnt] forState:UIControlStateNormal];
//    [self.blurView.remarkBtn setTitle:[NSString stringWithFormat:@"%ld", model.remarkCnt] forState:UIControlStateNormal];
//    [self.blurView.praiseBtn setTitle:[NSString stringWithFormat:@"%ld", model.praiseCnt] forState:UIControlStateNormal];
//    
//    self.blurView.remarkBtn.hideTipView = !model.hasNewRemark;
//    self.blurView.praiseBtn.hideTipView = !model.hasNewPraise;
//}


- (void)userItemDidUpdate:(NSNotification*)noti {
    BOOL isSuccess = [(NSNumber*)noti.object boolValue];
    if (!isSuccess) {
        [self requestUserInfoFailure];
    }
    [self updateUserInfoInUserItem];
}
- (void)requestUserInfoFailure {
    self.failureCnt++;
    if (self.failureCnt==2) {
        [RemindHUD.shared showDefaultHUDWithText:@"加载数据失败" completion:nil];
        self.failureCnt = 0;
        self.canRequestUserInfo = NO;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(10 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            self.canRequestUserInfo = YES;
        });
    }
}

/// 在这里刷新数据
- (void)viewWillAppear:(BOOL)animated {
    if (self.canRequestUserInfo) {
//        [self.userInfoModel updateUserInfoCompletion:^(MineUserInfoModelUpdateUserInfoState state) {
//
//            if (state==MineUserInfoModelUpdateUserInfoStateError) {
//                [self requestUserInfoFailure];
//            }
//            [self updateUserInfoInUserInfoModel];
//        }];
        [[UserItem defaultItem] getUserInfo];
    }
    
    [JudgeArrangeMessage needRedDotNumberWithCompletion:^(NSInteger num) {
        [self addRedDot:num];
    }];
}

//MARK: - UI
- (void)addScrollView {
    UIScrollView *scrollView = [[UIScrollView alloc] init];
    self.scrollView = scrollView;
    [self.view addSubview:scrollView];
    
    scrollView.showsVerticalScrollIndicator = NO;
    
    [scrollView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.equalTo(self.view);
        make.bottom.equalTo(self.view).offset(-150);
        make.top.equalTo(self.view).offset(getStatusBarHeight_Double);
    }];
}
- (void)addContentView {
    UIView *view = [[UIView alloc] init];
    self.contentView = view;
    [self.scrollView addSubview:view];
    
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.edges.equalTo(self.scrollView);
    }];
}
- (void)addBackImgView {
    UIImageView *imgView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"mineBackImg"]];
    [self.contentView addSubview:imgView];
    self.backImgView = imgView;
    
    [imgView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.top.equalTo(self.contentView);
    }];
}

- (void)addBlurView {
    MineTopBlurView *blurView = [[MineTopBlurView alloc] init];
    self.blurView = blurView;
    [self.contentView addSubview:blurView];
    
    [blurView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backImgView).offset(0.04533333333*SCREEN_WIDTH);
        make.top.equalTo(self.backImgView).offset(0.07512315*SCREEN_HEIGHT);
    }];
    
    [blurView.headImgBtn addTarget:self action:@selector(homePageBtnClicked) forControlEvents:UIControlEventTouchUpInside];
//    [blurView.blogBtn addTarget:self action:@selector(homePageBtnClicked) forControlEvents:UIControlEventTouchUpInside];
//    [blurView.remarkBtn addTarget:self action:@selector(homePageBtnClicked) forControlEvents:UIControlEventTouchUpInside];
//    [blurView.praiseBtn addTarget:self action:@selector(homePageBtnClicked) forControlEvents:UIControlEventTouchUpInside];
    [blurView.homePageBtn addTarget:self action:@selector(homePageBtnClicked) forControlEvents:UIControlEventTouchUpInside];
    UITapGestureRecognizer *tgr = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(homePageBtnClicked)];
    [blurView.blurImgView setUserInteractionEnabled:YES];
    [blurView.blurImgView addGestureRecognizer:tgr];
}

- (void)addBackBoardView {
    UIView *view = [[UIView alloc] init];
    self.backBoardView = view;
    [self.contentView addSubview:view];
     
    view.backgroundColor = [UIColor dm_colorWithLightColor:RGBColor(252, 253, 255, 1) darkColor:RGBColor(44, 44, 44, 1)];
    CGRect rect = CGRectMake(0, 0, SCREEN_WIDTH, 0.6896551724*SCREEN_HEIGHT);
    UIBezierPath *path = [UIBezierPath bezierPathWithRoundedRect:rect byRoundingCorners:UIRectCornerTopLeft|UIRectCornerTopRight cornerRadii:CGSizeMake(20, 20)];
    CAShapeLayer *layer = [[CAShapeLayer alloc] init];
    layer.frame = rect;
    layer.path = path.CGPath;
    view.layer.mask = layer;
    
    view.layer.shadowOpacity = 1;
    view.layer.shadowColor = RGBColor(39, 63, 98, 0.05).CGColor;
    view.layer.shadowOffset = CGSizeMake(0, -0.004926108374*SCREEN_HEIGHT);
    
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.bottom.equalTo(self.contentView);
        make.top.equalTo(self.blurView.mas_bottom).offset(-0.06216748768*SCREEN_HEIGHT);
        make.width.mas_equalTo(SCREEN_WIDTH);
        make.height.mas_equalTo(0.6896551724*SCREEN_HEIGHT);
    }];
}

- (void)addMsgCenterBtn {
    MineMSSEnterBtn *btn = [[MineMSSEnterBtn alloc] init];
    self.msgCenterBtn = btn;
    [self.backBoardView addSubview:btn];
    
    [btn.iconImgView setImage:[UIImage imageNamed:@"消息中心"]];
    [btn.nameLabel setText:@"消息中心"];
    
    [btn mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backBoardView).offset(0.136*SCREEN_WIDTH);
        make.top.equalTo(self.backBoardView).offset(0.08533333333*SCREEN_WIDTH);
    }];
    
    [btn addTarget:self action:@selector(msgCenterBtnClicked) forControlEvents:UIControlEventTouchUpInside];
}

- (void)addStampCenterBtn {
    MineMSSEnterBtn *btn = [[MineMSSEnterBtn alloc] init];
    self.stampCenterBtn = btn;
    [self.backBoardView addSubview:btn];
    
    [btn.iconImgView setImage:[UIImage imageNamed:@"邮票"]];
    [btn.nameLabel setText:@"邮票中心"];
    
    [btn mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backBoardView).offset(0.448*SCREEN_WIDTH);
        make.top.equalTo(self.backBoardView).offset(0.08533333333*SCREEN_WIDTH);
    }];
    
    [btn addTarget:self action:@selector(stampCenterBtnClicked) forControlEvents:UIControlEventTouchUpInside];
}

- (void)addSuggesstionBtn {
    MineMSSEnterBtn *btn = [[MineMSSEnterBtn alloc] init];
    self.suggesstionBtn = btn;
    [self.backBoardView addSubview:btn];
    
    [btn.iconImgView setImage:[UIImage imageNamed:@"意见与反馈"]];
    [btn.nameLabel setText:@"意见与反馈"];
    
    [btn mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backBoardView).offset(0.7573333333*SCREEN_WIDTH);
        make.top.equalTo(self.backBoardView).offset(0.08533333333*SCREEN_WIDTH);
    }];
    
    [btn addTarget:self action:@selector(suggesstionBtnClicked) forControlEvents:UIControlEventTouchUpInside];
}

- (void)addActivityCenterBtn {
    MineMSSEnterBtn *btn = [[MineMSSEnterBtn alloc] init];
    self.activityCenterBtn = btn;
    [self.backBoardView addSubview:btn];
    
    [btn.iconImgView setImage:[UIImage imageNamed:@"活动中心"]];
    [btn.nameLabel setText:@"活动中心"];
    
    [btn mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backBoardView).offset(0.136*SCREEN_WIDTH);
        make.top.equalTo(self.backBoardView).offset(0.39543186*SCREEN_WIDTH);
    }];
    
    [btn addTarget:self action:@selector(activityCenterBtnClicked) forControlEvents:UIControlEventTouchUpInside];
}

- (void)addSignView {
    MineSignView *view = [[MineSignView alloc] init];
    self.signView = view;
    self.signView.userInteractionEnabled = YES;
    UITapGestureRecognizer *tapGes = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(signViewClicked)];
    [self.signView addGestureRecognizer:tapGes];
    [self.backBoardView addSubview:view];
    
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backBoardView).offset(0.04266666667*SCREEN_WIDTH);
        make.top.equalTo(self.activityCenterBtn.mas_bottom).offset(25);
    }];
    
    [view.signBtn addTarget:self action:@selector(signBtnClicked) forControlEvents:UIControlEventTouchUpInside];
}

- (void)addTableView {
    UITableView *tableView = [[UITableView alloc] init];
    self.tableView = tableView;
    [self.contentView addSubview:tableView];
    
    tableView.delegate = self;
    tableView.dataSource = self;
    
    tableView.showsVerticalScrollIndicator = NO;
    tableView.showsHorizontalScrollIndicator = NO;
    [tableView setSeparatorStyle:(UITableViewCellSeparatorStyleNone)];
    tableView.scrollEnabled = NO;
    tableView.backgroundColor = [UIColor dm_colorWithLightColor:RGBColor(252, 253, 255, 1) darkColor:RGBColor(44, 44, 44, 1)];
    
    [tableView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.bottom.equalTo(self.backBoardView);
        make.top.equalTo(self.signView.mas_bottom).offset(25);
        
    }];
}

- (void)addBottomView {
    UIView *view = [[UIView alloc] init];
    self.bottomView = view;
    [self.view addSubview:view];
    
    view.backgroundColor =
    [UIColor dm_colorWithLightColor:RGBColor(252, 253, 255, 1) darkColor:RGBColor(44, 44, 44, 1)];
    
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.bottom.equalTo(self.view);
        make.height.mas_equalTo(0.5*SCREEN_HEIGHT);
    }];
}

- (void)addRedDot:(NSInteger)messageCount {
//    self.redDotView = [[UIView alloc] initWithFrame:CGRectMake(self.msgCenterBtn.right, self.msgCenterBtn.top - 6, 15, 15)];
//    self.redDotView = redView;
//    self.redDotView.layer.cornerRadius = 7.5;
//    [self.redDotView setClipsToBounds:YES];
//    self.redDotView.backgroundColor = [UIColor colorWithHexString:@"#FF6262" alpha:1];
    
//    UILabel *label = [[UILabel alloc] initWithFrame:CGRectMake(1, 1, 13, 13)];
//    label.text = [NSString stringWithFormat:@"%ld", messageCount];
//    label.textColor = [UIColor whiteColor];
//    label.font = [UIFont systemFontOfSize:10];
//    label.textAlignment = NSTextAlignmentCenter;
    
    self.messageCountLabel.text = [NSString stringWithFormat:@"%ld", messageCount];
    [self.redDotView addSubview:self.messageCountLabel];
    [self.backBoardView addSubview:self.redDotView];
    if (messageCount > 0) {
        self.redDotView.hidden = NO;
    } else {
        self.redDotView.hidden = YES;
    }
}

- (UIView *)redDotView {
    if (_redDotView == nil) {
//        _redDotView = [[UIView alloc] initWithFrame:CGRectMake(self.msgCenterBtn.right, self.msgCenterBtn.top - 6, 15, 15)];
        _redDotView = [[UIView alloc] initWithFrame:CGRectMake(self.msgCenterBtn.right - 2, self.msgCenterBtn.top - 4, 15, 15)];
        _redDotView.layer.cornerRadius = 7.5;
        [_redDotView setClipsToBounds:YES];
        _redDotView.backgroundColor = [UIColor colorWithHexString:@"#FF6262" alpha:1];
    }
    return _redDotView;
}

- (UILabel *)messageCountLabel {
    if (_messageCountLabel == nil) {
        _messageCountLabel = [[UILabel alloc] initWithFrame:CGRectMake(1, 1, 13, 13)];
        _messageCountLabel.textColor = [UIColor whiteColor];
        _messageCountLabel.font = [UIFont systemFontOfSize:10];
        _messageCountLabel.textAlignment = NSTextAlignmentCenter;
    }
    return _messageCountLabel;
}

//MARK: - tableView 的代理方法
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return 2;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    MineTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"MineViewController.tableView"];
    if (cell==nil) {
        cell = [[MineTableViewCell alloc] initWithStyle:(UITableViewCellStyleDefault) reuseIdentifier:@"MineViewController.tableView"];
    }
    
    if (indexPath.row==0) {
        cell.label.text = @"关于我们";
    }else {
        cell.label.text = @"设置";
    }
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    if (indexPath.row==0) {
        [self aboutUsClicked];
    }else {
        [self settingClicked];
    }
}

//MARK: - 按钮点击事件：

/// 点击消息中心按钮后调用
- (void)msgCenterBtnClicked {
//    CCLog(@"%s",__func__);
    MineMessageVC *vc = [[MineMessageVC alloc] init];
    vc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:vc animated:YES];
}

/// 点击邮票中心按钮后调用
- (void)stampCenterBtnClicked {
    StampCenterVC * vc = [[StampCenterVC alloc] init];
    vc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:vc animated:YES];
}

/// 点击活动中心按钮后调用
- (void)activityCenterBtnClicked {
    ActivityCenterVC *avc = [[ActivityCenterVC alloc]init];
    avc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:avc animated:YES];
}

/// 点击意见与反馈按钮后调用
- (void)suggesstionBtnClicked {
    FeedBackMainPageViewController *fvc = [[FeedBackMainPageViewController alloc]init];
      fvc.hidesBottomBarWhenPushed = YES;
      [self.navigationController pushViewController:fvc animated:YES];
}

/// 点击签到按钮后调用
- (void)signBtnClicked {
    [CheckInModel CheckInSucceeded:^{
        [self.signView setSignBtnEnable:NO];
        [self.signView setSignDay:[UserItemTool defaultItem].checkInDay];
        CheckInModel *checkInModel = [[CheckInModel alloc] init];
        [RemindHUD.shared showDefaultHUDWithText:[NSString stringWithFormat:@"签到成功，邮票+%ld",checkInModel.CheckInStampsCalculate] completion:nil];
    } Failed:^(NSError * _Nonnull err) {
        [RemindHUD.shared showDefaultHUDWithText:@"签到失败" completion:nil];
    }];
}

/// 点击进入个人主页的按钮后调用
- (void)homePageBtnClicked {
    NSLog(@"editing");
    EditMyInfoViewController *vc = [[EditMyInfoViewController alloc] init];
    vc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:vc animated:YES];
}

/// 关于我们点击后调用
- (void)aboutUsClicked {
    MineAboutController *vc = [[MineAboutController alloc] init];
    
    vc.hidesBottomBarWhenPushed = YES;
    
    [self.navigationController pushViewController:vc animated:YES];
}

/// 设置点击后调用
- (void)settingClicked {
    MineSettingViewController *vc = [[MineSettingViewController alloc] init];
    
    vc.hidesBottomBarWhenPushed = YES;
    
    [self.navigationController pushViewController:vc animated:YES];
}

///点击签到的view后调用
- (void)signViewClicked {
    CheckInViewController *vc = [[CheckInViewController alloc] init];
    vc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:vc animated:true];
}

@end

/*
 
 /// 点击头像按钮后调用
 - (void)headImgBtnClicked {
     UIImagePickerController *ctrler = [[UIImagePickerController alloc] init];
     ctrler.allowsEditing = YES;
     ctrler.delegate = self;
     [self presentViewController:ctrler animated:YES completion:nil];
 }
 
 - (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary<UIImagePickerControllerInfoKey,id> *)info {
     UIImage *img = info[UIImagePickerControllerEditedImage];
     [self.blurView.headImgBtn setImage:img forState:normal];
     [self dismissViewControllerAnimated:YES completion:nil];
     
     [EditMyInfoModel uploadProfile:img success:^(NSDictionary * _Nonnull responseObject) {
         if ([responseObject[@"status"] intValue] == 200) {
             [UserItemTool defaultItem].headImgUrl = responseObject[@"data"][@"photosrc"];
             MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
             hud.mode = MBProgressHUDModeText;
             hud.labelText = @"上传成功～";
             [hud hide:YES afterDelay:1];
         }
     } failure:^(NSError * _Nonnull error) {
         MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
         hud.mode = MBProgressHUDModeText;
         hud.labelText = @"上传成功～";
         [hud hide:YES afterDelay:1];
     }];
 }
 */
