//
//  StampCenterVC.m
//  Demo5
//
//  Created by 钟文韬 on 2021/8/14.
//

//View
#import "StampCenterVC.h"
#import "StampCenterTopView.h"
#import "DetailsMainViewController.h"
#import "ExchangeCenterViewController.h"
#import "GoodsCollectionViewCell.h"
#import "StampCenterSecondHeaderView.h"
#import "TaskTableViewCell.h"
#import "TableHeaderView.h"
#import "FoodVC.h"
#import "AttitudeMainPageVC.h"
#import "掌上重邮-Swift.h"
#import "CheckInModel.h"

//Model
#import "StampGoodsData.h"
#import "StampTaskData.h"
#import "CheckInModel.h"

#import "RemindHUD.h"

///邮票中心主界面
@interface StampCenterVC () <UITableViewDelegate,UICollectionViewDelegate,UIScrollViewDelegate,UITableViewDataSource,UICollectionViewDataSource,TopViewDelegate>

///当前Header高度
@property (nonatomic,assign) CGFloat CorrectHeaderY;
///共用的TopView
@property (nonatomic,strong) StampCenterTopView *topView;
///明细按钮
@property (nonatomic,strong) UIButton *detailBtn;
///第一分区装扮数据
@property (nonatomic,copy) NSArray *goodsAry;
///第二分区邮货数据
@property (nonatomic,copy) NSArray *section2GoodsAry;
///任务数据
@property (nonatomic,copy) NSArray *taskAry;
///额外任务数据
@property (nonatomic,copy) NSArray *extraTaskAry;
///左右划的scroll
@property (nonatomic,strong) StampCenterMainScrollView *mainScrollView;
///小型邮票数量View
@property (nonatomic,strong) UIView *stampCountView;
///邮票数量
@property (nonatomic,strong) NSNumber *number;
///小型邮票Lbl
@property (nonatomic,strong) UILabel *smallcountLbl;
///正确的小型邮票View的X坐标
@property (nonatomic,assign) CGFloat stampCountView_X;

@end

@implementation StampCenterVC

#pragma mark - setter
//商品数据
- (void)setGoodsAry:(NSArray *)goodsAry{
        NSMutableArray *mArray =[[NSMutableArray alloc]initWithCapacity:99];
        NSMutableArray *mArray2 = [[NSMutableArray alloc]initWithCapacity:99];
        for (int i = 0; i < goodsAry.count; i++) {
            StampGoodsData *data = goodsAry[i];
            if (data.type == 0) {
                //第二分区数据
                [mArray2 addObject:data];
            }
            //第一分区数据
            if(data.type == 1){
                [mArray addObject:data];
            }
        }
//    if (mArray.count == 0) {
//        self.mainScrollView.collectionHeaderView.detailLabel.text = @"敬请期待";
//        self.mainScrollView.collectionHeaderView.detailLabel.x = 0.8*SCREEN_WIDTH;
//    }
        _goodsAry = mArray;
    self.section2GoodsAry = mArray2;
    //刷新控件
    [self.mainScrollView.collectionView reloadData];
}

//任务数据
- (void)setTaskAry:(NSArray *)taskAry{
    NSMutableArray *mArray =[[NSMutableArray alloc]initWithCapacity:99];
    NSMutableArray *mArray2 = [[NSMutableArray alloc]initWithCapacity:99];
    for (int i = 0; i < taskAry.count; i++) {
        StampTaskData *data = taskAry[i];
        if ([data.type isEqualToString:@"base"]) {
            [mArray addObject:data];
        }
        if ([data.type isEqualToString:@"more"]) {
            [mArray2 addObject:data];
        }
    }
//    if (mArray.count > 0) {
//        [mArray removeObjectAtIndex:0];
//    }
    _taskAry = mArray;
    _extraTaskAry = mArray2;
    //刷新控件
    [self.mainScrollView.tableView reloadData];
}

//邮票数量
- (void)setNumber:(NSNumber *)number{
    _number = number;
    int j = [number intValue];
    if (j < 10) {
        self.stampCountView.width = 65;
        self.stampCountView_X = 0.78*SCREEN_WIDTH;
    }
    if (j >= 10 && j < 100) {
        self.stampCountView_X = 0.76*SCREEN_WIDTH;
    }
    if (j >= 100 && j <1000) {
        self.stampCountView_X = 0.74*SCREEN_WIDTH;
        self.stampCountView.width = 80;
    }
    if (j >= 1000 && j < 10000) {
        self.stampCountView_X = 0.72*SCREEN_WIDTH;
        self.smallcountLbl.width = 40;
        self.stampCountView.width = 90;
    }
    if (j >= 10000 && j < 100000) {
        self.stampCountView_X = 0.70*SCREEN_WIDTH;
        self.smallcountLbl.width = 50;
        self.stampCountView.width = 100;
    }else{
        self.stampCountView_X = 0.68*SCREEN_WIDTH;
        self.smallcountLbl.width = 60;
        self.stampCountView.width = 110;
    }
    self.smallcountLbl.text = [NSString stringWithFormat:@"%@",_number];
}


#pragma mark - viewWillAppear
- (void)viewWillAppear:(BOOL)animated{
    
    //初始化一些数据，避免token失效导致请求不到数据导致数组越界的闪退
    //初始化商品数据
    if (!self.goodsAry) {
        NSMutableArray *mArray = [[NSMutableArray alloc]initWithCapacity:4];
        for (int i = 0; i < 4; i++) {
            StampGoodsData *data = [[StampGoodsData alloc]init];
            data.url = @"NULL";
            data.title = @" NULL";
            data.type = 0;
            [mArray addObject:data];
        }
        self.goodsAry = mArray;
    }
    
    //初始化任务数据
    if (!self.taskAry) {
        NSMutableArray *mArray2 = [[NSMutableArray alloc]initWithCapacity:4];
        for (int i = 0; i < 4; i++) {
            StampTaskData *data = [[StampTaskData alloc]init];
            data.title = @"NULL";
            data.type = @"base";
            data.Description = @"NULL";
            data.max_progress = 1;
            data.current_progress = 1;
            data.gain_stamp = 999;
            [mArray2 addObject:data];
        }
        self.taskAry = mArray2;
    }
   
    [self checkAlertLbl];
    [self refreshPage];

}

#pragma mark - viewDidLoad
- (void)viewDidLoad{
    [super viewDidLoad];
    //加载数据
    [self setupData];
    
    //加载通知中心
    [self setupNotification];
    
    //加载TopBar
    [self setupBar];
    
    //加载横向Scroll
    [self.view addSubview:self.mainScrollView];
    
    //加载TopView
    [self.view addSubview:self.topView];
    
    //加载邮票数量View
    [self.topBarView addSubview:self.stampCountView];
    
    //topBar优先级最高
    [self.view bringSubviewToFront:self.topBarView];
    
    //设置小点
    [self setupPoint];

}

#pragma mark - table数据源

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView{
    return 3;
}

//row数量
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section{
    switch (section) {
        case 0:
            return self.taskAry.count;
            break;
        case 1:
            return self.extraTaskAry.count;
            break;
        default:
            return self.section2GoodsAry.count*0.5;
            break;
    }
}

//Cell
- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath{
        
    if (indexPath.section == 0) {
        TaskTableViewCell *cell = [[TaskTableViewCell alloc]init];
        StampTaskData *data = self.taskAry[indexPath.row];
        cell.row = indexPath.row;
        cell.data = data;
        return cell;
    }if (indexPath.section == 1){
        TaskTableViewCell *cell = [[TaskTableViewCell alloc]init];
        StampTaskData *data = self.extraTaskAry[indexPath.row];
        cell.row = indexPath.row;
        cell.data = data;
        return cell;
    }else{
        UITableViewCell *cell = [[UITableViewCell alloc]init];
        return cell;
    }
}

#pragma mark - table代理
//高度
- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath{
    return 41;
}


- (UIView *)tableView:(UITableView *)tableView viewForFooterInSection:(NSInteger)section{
    UIView *view = [[UIView alloc] init];
    view.backgroundColor = [UIColor clearColor];
    return view;
}


-(CGFloat)tableView:(UITableView *)tableView heightForFooterInSection:(NSInteger)section{
    return 0.1f;
}

- (UIView *)tableView:(UITableView *)tableView viewForHeaderInSection:(NSInteger)section{
    if (section == 0) {
        UIView* view = [[UIView alloc] initWithFrame:CGRectMake(0, 0, SCREEN_WIDTH, 270.0)];
        view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
        UIView* contentView = [[UIView alloc] initWithFrame:CGRectMake(0, 217, SCREEN_WIDTH, 53)];
        contentView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
        UIBezierPath  *maskPath = [UIBezierPath bezierPathWithRoundedRect:contentView.bounds byRoundingCorners:UIRectCornerTopLeft|UIRectCornerTopRight cornerRadii:CGSizeMake(20, 20)];
        CAShapeLayer *maskLayer = [[CAShapeLayer alloc]init];
        maskLayer.frame = contentView.bounds;
        maskLayer.path = maskPath.CGPath;
        contentView.layer.mask = maskLayer;
        UILabel *la = [[UILabel alloc]init];
        la.frame = CGRectMake(0.0427*SCREEN_WIDTH, 24, SCREEN_WIDTH - 40 , 28);
        la.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]];
        la.text = @"今日任务";
        la.font = [UIFont fontWithName:PingFangSCSemibold size:20];
        [view addSubview:contentView];
        [contentView addSubview:la];
        return view;
    }
    if (section == 1) {
        UIView* footerView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, SCREEN_WIDTH, 60.0)];
        footerView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
        UILabel *la = [[UILabel alloc]init];
        la.frame = CGRectMake(0.0427*SCREEN_WIDTH, 20, SCREEN_WIDTH- 40 , 28);
        la.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]];
        la.text = @"更多任务";
        la.font = [UIFont fontWithName:PingFangSCSemibold size:20];
        [footerView addSubview:la];
        return footerView;
    }
    return nil;
}

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section{
    if (section == 0) {
        return 270;
    } else if (section == 1) {
        return 60;
    }
    return 0.001;
}
#pragma mark - collection数据源
//组数
- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView{
    return 1;
}

//每组的个数
-(NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section{
    return self.section2GoodsAry.count;
}

//Cell
-(UICollectionViewCell *)collectionView:(UICollectionView*)collectionView cellForItemAtIndexPath:(nonnull NSIndexPath *)indexPath{
    GoodsCollectionViewCell *cell=[collectionView dequeueReusableCellWithReuseIdentifier:@"cell" forIndexPath:indexPath];
    StampGoodsData *data =self.section2GoodsAry[(indexPath.item)];
    cell.data = data;
    [cell.exchangeBtn addTarget:self action:@selector(jump:) forControlEvents:UIControlEventTouchUpInside];
    [cell.showBtn addTarget:self action:@selector(jump:) forControlEvents:UIControlEventTouchUpInside];
    return cell;
}

#pragma mark - collection代理
//Cell大小
-(CGSize)collectionView:(UICollectionView *)collectionView layout:(UICollectionViewLayout *)collectionViewLayout sizeForItemAtIndexPath:(NSIndexPath *)indexPath{

    return CGSizeMake(0.445*SCREEN_WIDTH, 0.632*SCREEN_WIDTH);
}

//HeaderView
//- (UICollectionReusableView *)collectionView:(UICollectionView *)collectionView viewForSupplementaryElementOfKind:(NSString *)kind atIndexPath:(NSIndexPath *)indexPath{
//    if (kind == UICollectionElementKindSectionHeader) {
//        StampCenterSecondHeaderView *collectionheader = [collectionView dequeueReusableSupplementaryViewOfKind:kind withReuseIdentifier:@"header" forIndexPath:indexPath];
//        collectionheader.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FBFCFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
////        if (indexPath.section == 0) {
////            collectionheader.height = 0;
////        }
////        else{
////            collectionheader.height = 54;
////        }
//        collectionheader.height = 54;
//        return collectionheader;
//    }
//    return nil;
//}

//内边距
-(UIEdgeInsets)collectionView:(UICollectionView *)collectionView layout:(UICollectionViewLayout *)collectionViewLayout insetForSectionAtIndex:(NSInteger)section{
    return UIEdgeInsetsMake(215+54, 0.042*SCREEN_WIDTH, 10, 0.042*SCREEN_WIDTH);
}

//后面有时间详细说明滚动逻辑（核心）
#pragma mark - 滚动代理
- (void)scrollViewDidScroll:(UIScrollView *)scrollView{
//====================================================横向
    if (scrollView.tag == 123) {
        //滑到任务界面时，小圆点消失，并将日期写入NSUserdefualt
        if (scrollView.contentOffset.x == SCREEN_WIDTH) {
            NSDate *date = [NSDate date];
            NSDateFormatter *formatter = [[NSDateFormatter alloc]init];
            formatter.dateFormat = @"yyyy-MM-dd";
            NSString *str = [formatter stringFromDate:date];
            [NSUserDefaults.standardUserDefaults setObject:str forKey:@"NowDate"];
            self.topView.point.hidden = YES;
        }

        //滑动条
        CGFloat x = self.topView.stampStoreLbl.x+3 + (scrollView.contentOffset.x * ((self.topView.stampTaskLbl.x-self.topView.stampStoreLbl.x)/SCREEN_WIDTH));
        self.topView.switchbar.x = x;
        self.topView.swithPoint.x = x+63;
    }

    //====================================================CollectionView
    if ([scrollView isKindOfClass:[UICollectionView class]]) {
        CGFloat f = Bar_H - self.CorrectHeaderY;
        //当未滑动时
        if (scrollView.contentOffset.y <= 0) {
           _CorrectHeaderY = Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = SCREEN_WIDTH;
            } completion:nil];
                self.topView.bannerImage.transform = CGAffineTransformMakeScale(1, 1);
                self.detailBtn.transform = CGAffineTransformMakeScale(1, 1);
                self.topView.bannerImage.alpha = 1;
                self.topView.bannerImage.y = 28;
        }
        //当正在滑动时 （不出现邮票）
        if (scrollView.contentOffset.y > 0 && scrollView.contentOffset.y < 75) {
            _CorrectHeaderY = -scrollView.contentOffset.y+Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = SCREEN_WIDTH;
            } completion:nil];
            self.topView.bannerImage.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.detailBtn.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.topView.bannerImage.alpha = (125 - f)/125;
        }
        //当正在滑动时 （出现邮票）
        if (scrollView.contentOffset.y >= 75 && scrollView.contentOffset.y < 138) {
            _CorrectHeaderY = -scrollView.contentOffset.y+Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = self.stampCountView_X;
            } completion:nil];
            self.topView.bannerImage.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.detailBtn.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.topView.bannerImage.alpha = (125 - f)/125;
        }
        //到顶了
        if (scrollView.contentOffset.y >= 138) {
            self.topView.bannerImage.transform = CGAffineTransformMakeScale(0.1,0.1);
            self.detailBtn.transform = CGAffineTransformMakeScale(0.1,0.1);
        }
        _topView.y = _CorrectHeaderY;
    }
    
    
    if ([scrollView isKindOfClass:[UITableView class]]) {
        CGFloat f = Bar_H - self.CorrectHeaderY;
        //当未滑动时
        if (scrollView.contentOffset.y <= 0) {
           _CorrectHeaderY = Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = SCREEN_WIDTH;
            } completion:nil];
                self.topView.bannerImage.transform = CGAffineTransformMakeScale(1, 1);
                self.detailBtn.transform = CGAffineTransformMakeScale(1, 1);
                self.topView.bannerImage.alpha = 1;
                self.topView.bannerImage.y = 28;
        }
        //当正在滑动时 （不出现邮票）
        if (scrollView.contentOffset.y > 0 && scrollView.contentOffset.y < 75) {
            _CorrectHeaderY = -scrollView.contentOffset.y+Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = SCREEN_WIDTH;
            } completion:nil];
            self.topView.bannerImage.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.detailBtn.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.topView.bannerImage.alpha = (125 - f)/125;
        }
        //当正在滑动时 （出现邮票）
        if (scrollView.contentOffset.y >= 75 && scrollView.contentOffset.y < 138) {
            _CorrectHeaderY = -scrollView.contentOffset.y+Bar_H;
            [UIView animateWithDuration:1 delay:0 usingSpringWithDamping:0.7 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseIn animations:^{
                self->_stampCountView.x = self.stampCountView_X;
            } completion:nil];
            self.topView.bannerImage.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.detailBtn.transform = CGAffineTransformMakeScale((280 - f)/280, (280 - f)/280);
            self.topView.bannerImage.alpha = (125 - f)/125;
        }
        //到顶了
        if (scrollView.contentOffset.y >= 138) {
            self.topView.bannerImage.transform = CGAffineTransformMakeScale(0.1,0.1);
            self.detailBtn.transform = CGAffineTransformMakeScale(0.1,0.1);
        }
        _topView.y = _CorrectHeaderY;
    }

}

- (void)scrollViewWillEndDragging:(UIScrollView *)scrollView withVelocity:(CGPoint)velocity targetContentOffset:(inout CGPoint *)targetContentOffset{
    if ([scrollView isKindOfClass:[UICollectionView class]]) {
        self.mainScrollView.tableView.contentOffset = self.mainScrollView.collectionView.contentOffset;
    }
    if ([scrollView isKindOfClass:[UITableView class]]) {
        self.mainScrollView.collectionView.contentOffset = self.mainScrollView.tableView.contentOffset;
    }
}


- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView{
    if ([scrollView isKindOfClass:[UICollectionView class]]) {
        self.mainScrollView.tableView.contentOffset = self.mainScrollView.collectionView.contentOffset;
    }
    if ([scrollView isKindOfClass:[UITableView class]]) {
        self.mainScrollView.collectionView.contentOffset = self.mainScrollView.tableView.contentOffset;
    }
}


#pragma mark - getter
//顶部View
- (StampCenterTopView *)topView{
    if (!_topView) {
        _topView = [[StampCenterTopView alloc]init];
        _topView.delegate = self;
        _number = _topView.number;
        [_topView addSubview:self.detailBtn];
    }
    return _topView;
}

//跳转至邮票详情按钮
- (UIButton *)detailBtn{
    if (!_detailBtn) {
        _detailBtn = [[UIButton alloc]initWithFrame:self.topView.bannerImage.frame];
        [_detailBtn addTarget:self action:@selector(goDetail) forControlEvents:UIControlEventTouchUpInside];
        _detailBtn.backgroundColor = [UIColor clearColor];
    }
    return _detailBtn;
}

//主界面
- (StampCenterMainScrollView *)mainScrollView{
    if (!_mainScrollView) {
        _mainScrollView = [[StampCenterMainScrollView alloc]initWithFrame:CGRectMake(0, Bar_H - 20, SCREEN_WIDTH, SCREEN_HEIGHT-Bar_H)];
        _mainScrollView.collectionView.delegate = self;
        _mainScrollView.collectionView.dataSource = self;
        _mainScrollView.tableView.delegate = self;
        _mainScrollView.tableView.dataSource = self;
        _mainScrollView.delegate = self;
    }
    return _mainScrollView;
}

#pragma mark - 私有方法
//返回
- (void)pop{
    [self.navigationController popViewControllerAnimated:YES];
}

//跳转至第一页
- (void)goPageOne{
    [UIView animateWithDuration:0.7 delay:0 usingSpringWithDamping:0.8 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseInOut animations:^{
        self.mainScrollView.contentOffset = CGPointMake(0, 0);
    } completion:nil];
}

//跳转至第二页
- (void)goPageTwo{
    [UIView animateWithDuration:0.7 delay:0 usingSpringWithDamping:0.8 initialSpringVelocity:0 options:UIViewAnimationOptionCurveEaseInOut animations:^{
        self.mainScrollView.contentOffset = CGPointMake(SCREEN_WIDTH, 0);
    } completion:nil];
     
  
}

//跳转至邮票详情
- (void)goDetail{
    NSLog(@"跳转至邮票详情");
    DetailsMainViewController *dvc = [[DetailsMainViewController alloc]init];
    dvc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:dvc animated:YES];
    
}

//跳转至商品详情111
- (void)jump:(UIButton *)sender{
    NSLog(@"跳转至商品详情，id = %ld",(long)sender.tag);
    ExchangeCenterViewController *evc = [[ExchangeCenterViewController alloc]initWithID:[NSString stringWithFormat:@"%ld",(long)sender.tag]];
    evc.hidesBottomBarWhenPushed = YES;
    [self.navigationController pushViewController:evc animated:YES];
}

//设置Bar
- (void)setupBar{
    self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
    self.VCTitleStr = @"邮票中心";
    self.titlePosition = TopBarViewTitlePositionLeft;
    self.splitLineColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#2A4E84" alpha:0.1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
    self.titleFont = [UIFont fontWithName:PingFangSCSemibold size:22];
    self.splitLineHidden = YES;
    self.CorrectHeaderY = Bar_H;
}

//小型邮票数量View
- (UIView *)stampCountView{
    if (!_stampCountView) {
    
        [HttpTool.shareTool
         request:Mine_GET_stampStoreMainPage_API
         type:HttpToolRequestTypeGet
         serializer:HttpToolRequestSerializerHTTP
         bodyParameters:nil
         progress:nil
         success:^(NSURLSessionDataTask * _Nonnull task, id  _Nullable object) {
            self.number = object[@"data"][@"user_amount"];
        }
         failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
            NSLog(@"==========================出错了");
        }];
        
//        HttpClient *client = [HttpClient defaultClient];
//        [client.httpSessionManager GET:Mine_GET_stampStoreMainPage_API parameters:nil headers:nil progress:nil success:^(NSURLSessionDataTask * _Nonnull task, id  _Nonnull responseObject) {
//            self.number = responseObject[@"data"][@"user_amount"];
//            } failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
//                NSLog(@"==========================出错了");
//            }];
        _stampCountView = [[UIView alloc]initWithFrame:CGRectMake(SCREEN_WIDTH,7, 75, 27)];
        _stampCountView.backgroundColor = [UIColor colorWithHexString:@"#2B333F" alpha:0.4];
        _stampCountView.layer.cornerRadius = 14;
        UIImageView *barstamp = [[UIImageView alloc]initWithFrame:CGRectMake(11.7, 2.3, 21.1, 21.1)];
        barstamp.image = [UIImage imageNamed:@"barstamp"];
        _smallcountLbl = [[UILabel alloc]initWithFrame:CGRectMake(37.5, 2.3, 30.5, 22)];
        _smallcountLbl.font = [UIFont fontWithName:@"DIN-Medium" size:18.7];
        _smallcountLbl.textColor = [UIColor whiteColor];
        _smallcountLbl.text = @"正在加载";
        [_stampCountView addSubview:barstamp];
        [_stampCountView addSubview:_smallcountLbl];
    }
    return _stampCountView;
}

//设置小圆点的状态
- (void)setupPoint{
    NSDate *date = [NSDate date];
    NSDateFormatter *formatter = [[NSDateFormatter alloc]init];
    formatter.dateFormat = @"yyyy-MM-dd";
    NSString *str = [formatter stringFromDate:date];
    if ([str isEqualToString:[NSUserDefaults.standardUserDefaults objectForKey:@"NowDate"]]) {
        self.topView.point.hidden = YES;
    }
    else{
        self.topView.point.hidden = NO;
    }
    
    //请勿移动此代码的位置，不然会引起UI错乱，连锁玄学问题
    //=============================================<<<
    UIView *v = [[UIView alloc]initWithFrame:CGRectMake(0, 0, SCREEN_WIDTH, STATUSBARHEIGHT)];
    v.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
    [self.view addSubview:v];
    //=============================================>>>
}


//获取数据
- (void)setupData{
    [StampTaskData TaskDataWithSuccess:^(NSArray * _Nonnull array) {
        self.taskAry = array;
    } error:^{
        [self netWorkAlert];
    }];
    
    [StampGoodsData GoodsDataWithSuccess:^(NSArray * _Nonnull array) {
        self.goodsAry = array;
    } error:^{
        [self netWorkAlert];
    }];
}

//网络异常警告
- (void)netWorkAlert{
    [RemindHUD.shared showDefaultHUDWithText:@"网络异常" completion:nil];
}

//跳转至发动态界面
- (void)jumpToReleaseDynamic{
//    SZHReleaseDynamic *SVC = [[SZHReleaseDynamic alloc]init];
//    [self.navigationController pushViewController:SVC animated:YES];
}

//刷新界面
- (void)refreshPage{
    [StampTaskData TaskDataWithSuccess:^(NSArray * _Nonnull array) {
        self.taskAry = array;
    } error:^{
        [self netWorkAlert];
    }];
    
    [HttpTool.shareTool
     request:Mine_GET_stampStoreMainPage_API
     type:HttpToolRequestTypeGet
     serializer:HttpToolRequestSerializerHTTP
     bodyParameters:nil
     progress:nil
     success:^(NSURLSessionDataTask * _Nonnull task, id  _Nullable object) {
        self.number = object[@"data"][@"user_amount"];
        self.topView.number = object[@"data"][@"user_amount"];
    }
     failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
        NSLog(@"==========================出错了");
    }];
    
//    HttpClient *client = [HttpClient defaultClient];
//    [client.httpSessionManager GET:Mine_GET_stampStoreMainPage_API parameters:nil headers:nil progress:nil success:^(NSURLSessionDataTask * _Nonnull task, id  _Nonnull responseObject) {
//        self.number = responseObject[@"data"][@"user_amount"];
//        self.topView.number = responseObject[@"data"][@"user_amount"];
//        } failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
//            NSLog(@"==========================出错了");
//        }];
}

//跳转至界面
- (void)jumpToZhiyuan{
//    QueryLoginViewController *QVC = [[QueryLoginViewController alloc]init];
//    [self.navigationController pushViewController:QVC animated:YES];
}

//跳转至QA
- (void)jumpToNewQA{
    NewQuestionVC *QVC = [[NewQuestionVC alloc] init];
    [self.navigationController pushViewController:QVC animated:YES];
}

//跳转至个人中心
// 编辑资料页已迁移到 CMP（cyxbs-pages/mine/edit），iOS 原生 EditMyInfoViewController
// 已删除。这里的任务条目原本由通知 "jumpToProfile" 触发（TaskTableViewCell），
// 暂以 toast 提示用户到「我的」页面操作，待 StampCenter 整体迁移 CMP 后再统一接入。
- (void)jumpToProfile{
    [RemindHUD.shared showDefaultHUDWithText:@"请到「我的」-「编辑资料」中完善信息" completion:nil];
}

//跳转至中心->美食版块
- (void)jumpToFood{
    FoodVC *FVC = [[FoodVC alloc]init];
    [self.navigationController pushViewController:FVC animated:YES];
}

//跳转至中心->活动布告栏
- (void)jumpToActivity{
    ActivityMainViewController *AVC = [[ActivityMainViewController alloc]init];
    [self.navigationController pushViewController:AVC animated:YES];
}

//跳转至中心->表态
- (void)jumpToAttitude{
    AttitudeMainPageVC *AVC = [[AttitudeMainPageVC alloc]init];
    [self.navigationController pushViewController:AVC animated:YES];
}

//跳转至没课约
- (void)jumpToWeDate{
    WeDateVC *WVC = [[WeDateVC alloc]init];
    [self.navigationController pushViewController:WVC animated:YES];
}

//每日签到
- (void)checkInToday{
    CheckInModel *checkInModel = [[CheckInModel alloc] init];
    [CheckInModel CheckInSucceeded:^{
        [RemindHUD.shared showDefaultHUDWithText:[NSString stringWithFormat:@"签到成功，邮票+%ld",checkInModel.CheckInStampsCalculate] completion:^{
            [[NSNotificationCenter defaultCenter] postNotificationName:@"refreshPage" object:nil];
        }];
    } Failed:^(NSError * _Nonnull err) {
        NSLog(@"出错了");
    }];
}


//检查是否有未领取的货物
- (void)checkAlertLbl {
    
    [HttpTool.shareTool
     request:Mine_GET_commonQuestion_API
     type:HttpToolRequestTypeGet
     serializer:HttpToolRequestSerializerHTTP
     bodyParameters:nil
     progress:nil
     success:^(NSURLSessionDataTask * _Nonnull task, id  _Nullable object) {
        BOOL un_got_good = object[@"un_got_good"];
        if (un_got_good == YES) {
            self.topView.alertLbl.hidden = NO;
        }else {
            self.topView.alertLbl.hidden = YES;
        }
    }
     failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
        NSLog(@"==========================出错了");
    }];
    
//    HttpClient *client = [HttpClient defaultClient];
//    [client requestWithPath:Mine_GET_commonQuestion_API method:HttpRequestGet parameters:nil prepareExecute:nil progress:nil success:^(NSURLSessionDataTask *task, id responseObject) {
//            BOOL un_got_good = responseObject[@"un_got_good"];
//        if (un_got_good == YES) {
//            self.topView.alertLbl.hidden = NO;
//        }else{
//            self.topView.alertLbl.hidden = YES;
//        }
//        } failure:^(NSURLSessionDataTask *task, NSError *error) {
//            NSLog(@"==========================出错了");
//        }];

}

//设置通知中心
- (void)setupNotification{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(netWorkAlert) name:@"networkerror" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToNewQA) name:@"jumpToNewQA" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToReleaseDynamic) name:@"jumpToReleaseDynamic" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(refreshPage) name:@"refreshPage" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(checkAlertLbl) name:@"checkAlertLbl" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToZhiyuan) name:@"jumpToZhiyuan" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToProfile) name:@"jumpToProfile" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToFood) name:@"jumpToFood" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToActivity) name:@"jumpToActivity" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToWeDate) name:@"jumpToWeDate" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(jumpToAttitude) name:@"jumpToAttitude" object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(checkInToday) name:@"checkInToday" object:nil];
}

@end
