//
//  TestArrangeViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2020/1/30.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "TestArrangeViewController.h"
#import "ScoreViewController.h"
#import "TestCardTableViewCell.h"
#import "PointAndDottedLineView.h"
#import "ExamArrangeModel.h"
#import "ExamArrangeData.h"
#import "UserInfoView.h"
#import "ScorePresentAnimation.h"
#import <WebKit/WebKit.h>
#import "GPAUnavailableViewController.h"

@interface TestArrangeViewController ()<UITableViewDelegate,UITableViewDataSource,UIViewControllerTransitioningDelegate,WKNavigationDelegate>
@property (nonatomic, weak) UITableView *tableView;
@property (nonatomic, weak)UILabel *titleLabel;
@property (nonatomic, strong)ExamArrangeModel *examArrangeModel;
@property (nonatomic, weak)UIView *seperateLine;//分割线
@property (nonatomic, weak)UILabel *backButtonTitle;//“我的考试”
@property (nonatomic, weak)UIButton *backButton;//返回按钮
@property (nonatomic, weak)UIButton *scoreEnterButton;//学分成绩入口按钮
@property (nonatomic, weak)UIView *topBar;
@property (nonatomic, strong)UserInfoView *bottomBar;
@end

@implementation TestArrangeViewController
- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    [self testArrangeFailed];
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    CCLog(@"finish");
}



- (void)viewDidLoad {
    [super viewDidLoad];
    self.navigationController.navigationBar.hidden = YES;
    self.navigationController.navigationBar.topItem.title = @"";
    if (@available(iOS 11.0, *)) {
        self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F8F9FC" alpha:1] darkColor:[UIColor colorWithHexString:@"#000101" alpha:1]];
    } else {
        self.view.backgroundColor = [UIColor colorWithHexString:@"#F8F9FC"];
    }
    [self.navigationController.navigationBar setTitleTextAttributes:@{NSForegroundColorAttributeName:self.view.backgroundColor}];
    
    [self addTopBar];
    [self addBackButton];
    [self addBackButtonTitle];
    [self addBottomBar];  //添加下方学分成绩入口的按钮
    
    [HttpTool.shareTool
     request:Discover_GET_examModel_API
     type:HttpToolRequestTypeGet
     serializer:HttpToolRequestSerializerHTTP
     bodyParameters:nil
     progress:nil
     success:^(NSURLSessionDataTask * _Nonnull task, id  _Nullable object) {
        NSString *type = object[@"data"][@"ExamModel"];
        if ([type isEqualToString:@"magipoke"]) {//掌邮显示
            [self displayByAPP];
        }else {//h5显示
            int mode = 0;
            if (@available(iOS 13.0, *)) {
                //判断是否使用深色模式：
                if (UITraitCollection.currentTraitCollection.userInterfaceStyle == UIUserInterfaceStyleDark) {
                    mode = 1;
                }
            }
            //考试h5:https://fe-prod.redrock.team/zscy-exam/
            //2020迎新h5:https://fe-prod.redrock.team/welcome-2020/
            NSString *h5UrlStr = object[@"data"][@"Redirect"];
            //h5url后拼接的参数：stuNum为学号；uiType为1代表使用黑夜模式，0代表不是
            h5UrlStr = [NSString stringWithFormat:@"%@?stuNum=%@&uiType=%d",h5UrlStr,[UserItem defaultItem].stuNum,mode];
            [self displayByH5:h5UrlStr];
        }
    }
     failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
        [self testArrangeFailed];
    }];
    
//    [[HttpClient defaultClient] requestWithPath:Discover_GET_examModel_API method:HttpRequestGet parameters:nil prepareExecute:nil progress:nil success:^(NSURLSessionDataTask *task, id responseObject) {
//        NSString *type = responseObject[@"data"][@"ExamModel"];
//        if ([type isEqualToString:@"magipoke"]) {//掌邮显示
//            [self displayByAPP];
//        }else {//h5显示
//            int mode = 0;
//            if (@available(iOS 13.0, *)) {
//                //判断是否使用深色模式：
//                if (UITraitCollection.currentTraitCollection.userInterfaceStyle == UIUserInterfaceStyleDark) {
//                    mode = 1;
//                }
//            }
//            //考试h5:https://fe-prod.redrock.team/zscy-exam/
//            //2020迎新h5:https://fe-prod.redrock.team/welcome-2020/
//            NSString *h5UrlStr = responseObject[@"data"][@"Redirect"];
//            //h5url后拼接的参数：stuNum为学号；uiType为1代表使用黑夜模式，0代表不是
//            h5UrlStr = [NSString stringWithFormat:@"%@?stuNum=%@&uiType=%d",h5UrlStr,[UserItem defaultItem].stuNum,mode];
//            [self displayByH5:h5UrlStr];
//        }
//    } failure:^(NSURLSessionDataTask *task, NSError *error) {
//        [self testArrangeFailed];
//    }];
    //修改考试的展示模式
    /*
    [[HttpClient defaultClient] requestWithPath:@"https://be-prod.redrock.team/magipoke-jwzx/changExamModel?model=magipoke" method:HttpRequestPost parameters:@{} prepareExecute:nil progress:nil success:^(NSURLSessionDataTask *task, id responseObject) {
        CCLog(@"kkksuccess%@",responseObject);
    } failure:^(NSURLSessionDataTask *task, NSError *error) {
        CCLog(@"kkkfaliue%@",error);
    }];
     */
}

- (void)displayByAPP {
    [self addTableView];
    [self addTitleLabel];
    [self getExamArrangeData];
    [self updateUI];
}

- (void)displayByH5:(NSString*)h5UrlStr {
    WKWebView * webView = [[WKWebView alloc]init];
    [self.view addSubview:webView];
    
    webView.navigationDelegate = self;
    NSURL * url = [NSURL URLWithString:h5UrlStr];
    NSURLRequest * request = [NSURLRequest requestWithURL:url];
    [webView loadRequest:request];
    webView.backgroundColor = self.view.backgroundColor;
    [webView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.right.equalTo(self.view);
        make.top.equalTo(self.topBar.mas_bottom);
        make.bottom.equalTo(self.bottomBar.mas_top);
    }];
}


- (void)addTopBar {
    UIView *view = [[UIView alloc]initWithFrame:CGRectMake(0, 0, self.view.width, 47 + STATUSBARHEIGHT)];
    self.topBar = view;
    view.backgroundColor = self.view.backgroundColor;
    [self.view addSubview:view];
}
- (void)addBackButton {
    UIButton *button = [[UIButton alloc]init];
    [self.topBar addSubview:button];
    self.backButton = button;
    [button setImage:[UIImage imageNamed:@"LQQBackButton"] forState:normal];
    [button setImage: [UIImage imageNamed:@"EmptyClassBackButton"] forState:UIControlStateHighlighted];
    [button mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.view).offset(17);
        make.top.equalTo(self.view).offset(10 + STATUSBARHEIGHT);
        make.width.equalTo(@30);
        make.height.equalTo(@30);
    }];
    [button.imageView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.width.equalTo(@7);
        make.height.equalTo(@14);
    }];
    [button addTarget:self action:@selector(popController) forControlEvents:UIControlEventTouchUpInside];
}
-(void)addBackButtonTitle {
    UILabel *label = [[UILabel alloc]init];
    self.backButtonTitle = label;
    [self.topBar addSubview:label];
    
    label.text = @"我的考试";
    [label setFont:[UIFont fontWithName:PingFangSCSemibold size:21]];
    if (@available(iOS 11.0, *)) {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]];
    }
    
    [label mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.backButton.mas_right).offset(20);
        make.centerY.equalTo(self.backButton);
    }];
}


- (void)addTableView {
    UITableView *tableView;
    if (IS_IPHONEX) {
        tableView = [[UITableView alloc]initWithFrame:CGRectMake(53, self.tabBarController.tabBar.height+40, self.view.width - 53 - 19, self.view.height - 87 -  TABBARHEIGHT - 30) style:UITableViewStylePlain];
    }else {
        tableView = [[UITableView alloc]initWithFrame:CGRectMake(53, self.tabBarController.tabBar.height+75, self.view.width - 53 - 19, self.view.height - 87 -  TABBARHEIGHT - 50) style:UITableViewStylePlain];
    }
    self.tableView = tableView;
    [self.view addSubview:tableView];
    //避免tableView上面的tittleLabel和topbar的label文字重叠
    [self.view sendSubviewToBack:tableView];
    
    tableView.showsVerticalScrollIndicator = NO;
    tableView.backgroundColor = self.view.backgroundColor;
    tableView.delegate = self;
    tableView.dataSource = self;
    tableView.separatorStyle = UITableViewCellSeparatorStyleNone;
}
- (void)addSeperateLine {
    UIView *line = [[UIView alloc]initWithFrame:CGRectMake(0, self.navigationController.navigationBar.origin.y + self.navigationController.navigationBar.height, self.view.width, 1)];
    line.backgroundColor = [UIColor colorWithRed:42/255.0 green:78/255.0 blue:132/255.0 alpha:0.1];
    [self.view addSubview:line];
    self.seperateLine = line;
}
- (void)addTitleLabel {
    UILabel *label = [[UILabel alloc]initWithFrame:CGRectMake(-34, -48, 250, 40)];
    self.titleLabel = label;
    [self.tableView addSubview:label];
    label.text = @"考试安排";
    [label setFont:[UIFont fontWithName:PingFangSCSemibold size:22]];
    if (@available(iOS 11.0, *)) {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1]];
    }
}
- (void) addBottomBar {
    UserInfoView *view = [[UserInfoView alloc]init];
    [self.view addSubview:view];
    self.bottomBar = view;
    //加一个透明的button用来接受点击事件
    UIButton *scoreEnterButton = [[UIButton alloc]init];
    self.scoreEnterButton = scoreEnterButton;
    scoreEnterButton.backgroundColor = UIColor.clearColor;
    [view addSubview:scoreEnterButton];
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(scoreEnterButton).offset(-10);//十个像素缓冲了scoreEnterButton的10个像素
        if IS_IPHONEX {
            make.top.equalTo(scoreEnterButton).offset(-20);//十个像素缓冲了scoreEnterButton的10个像素
        }
        make.left.right.bottom.equalTo(scoreEnterButton);
    }];
    
    [scoreEnterButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.bottom.equalTo(self.view).offset(10);//10个像素是为了挡住下面的圆角
        make.width.equalTo(self.view);
        make.centerX.equalTo(self.view);
        make.height.equalTo(@80);
    }];
    view.layer.cornerRadius = 16;
    view.clipsToBounds = YES;
    //GPA接口暂时弄不了，所以关闭GPA查询入口
    [scoreEnterButton addTarget:self action:@selector(pushToScoreVC) forControlEvents:UIControlEventTouchUpInside];
}


- (void) pushToScoreVC {
    ScoreViewController *vc = [[ScoreViewController alloc]init];
    vc.transitioningDelegate = self;
//    GPAUnavailableViewController *vc = [[GPAUnavailableViewController alloc]init];
    [self presentViewController:vc animated:YES completion:^{
        NSLog(@"跳转至学分成绩vc");
    }];

}
- (void)getExamArrangeData {
    //alloc init 里面会进行网络请求，请求后会发通知
    ExamArrangeModel *model = [[ExamArrangeModel alloc]init];
    self.examArrangeModel = model;
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(testArrangeFailed) name:@"getExamArrangeFailed" object:nil];
}
-(void)testArrangeFailed {
    //当数据加载失败时alert弹窗此服务不可用，并pop
    UIAlertController *controller = [UIAlertController alertControllerWithTitle:@"通知" message:@"此服务目前不可用: (" preferredStyle:UIAlertControllerStyleAlert];
    UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"好的" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [self popController];
    }];
    [controller addAction:okAction];
    [self presentViewController:controller animated:YES completion:nil];
}
- (void)updateUI {
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(getExamArrangeDataSucceed) name:@"getExamArrangeSucceed" object:nil];
}
- (void)getExamArrangeDataSucceed {
    NSLog(@"%@",self.examArrangeModel.examArrangeData);
    [self.tableView reloadData];
    //展示左边的小球和点点
    int pointCount = (int)self.examArrangeModel.examArrangeData.data.count;
    CGFloat spacing = 166.13;
    PointAndDottedLineView *pointAndDottedLineView = [[PointAndDottedLineView alloc]initWithPointCount:pointCount Spacing:spacing];
    [pointAndDottedLineView setFrame:CGRectMake(-30, 7, 11, pointAndDottedLineView.bigCircle.width + spacing * (pointCount - 1))];
    self.tableView.clipsToBounds = NO;
    [self.tableView addSubview:pointAndDottedLineView];
    if (pointAndDottedLineView.isNoExam) {
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
        hud.mode = MBProgressHUDModeText;
        hud.labelText = @"您当前没有考试哦～";
        [hud hide:YES afterDelay:1.5];
    }
}
- (void)popController {
    [self.navigationController popViewControllerAnimated:YES];
//    self.navigationController.navigationBar.hidden = NO;
}

//MARK: - translationVC Delegate
- (id<UIViewControllerAnimatedTransitioning>)animationControllerForPresentedController:(UIViewController *)presented presentingController:(UIViewController *)presenting sourceController:(UIViewController *)source {
    ScorePresentAnimation *myAnimation = [[ScorePresentAnimation alloc]init];
    myAnimation.isPresent = YES;
    return myAnimation;
}
//MARK: - tableView代理

- (nonnull UITableViewCell *)tableView:(nonnull UITableView *)tableView cellForRowAtIndexPath:(nonnull NSIndexPath *)indexPath {
    TestCardTableViewCell *cell = [[TestCardTableViewCell alloc]init];
    cell.selectionStyle  = UITableViewCellSelectionStyleNone;
    //数据分配
    cell.weekTimeLabel.text = [NSString stringWithFormat:@"%@周周%@",[self translationArabicNum:self.examArrangeModel.examArrangeData.data[indexPath.row].week.intValue], [self translationArabicNum: self.examArrangeModel.examArrangeData.data[indexPath.row].weekday.intValue]];
    NSString *lastDay =[self calcDaysFromBegin:[self dateFromString:[self getCurrentTime]] end:[self dateFromString:self.examArrangeModel.examArrangeData.data[indexPath.row].date]];
    cell.leftDayLabel.text = [NSString stringWithFormat:@"还剩%@天考试",lastDay];
    cell.subjectLabel.text = self.examArrangeModel.examArrangeData.data[indexPath.row].course;
    cell.testNatureLabel.text = self.examArrangeModel.examArrangeData.data[indexPath.row].type;
    cell.dayLabel.text = [NSString stringWithFormat:@"%@月%@日", [self getArrayWithString: self.examArrangeModel.examArrangeData.data[indexPath.row].date][1], [self getArrayWithString: self.examArrangeModel.examArrangeData.data[indexPath.row].date][2]];
    cell.timeLabel.text = [NSString stringWithFormat:@"%@ - %@",self.examArrangeModel.examArrangeData.data[indexPath.row].begin_time, self.examArrangeModel.examArrangeData.data[indexPath.row].end_time];
    cell.classLabel.text = self.examArrangeModel.examArrangeData.data[indexPath.row].classroom;
    cell.seatNumLabel.text = [NSString stringWithFormat:@"%@号", self.examArrangeModel.examArrangeData.data[indexPath.row].seat];
    //当某科考试剩余天数小于0天时更换label为考试已结束样式
    if(lastDay.intValue < 0) {
        cell.leftDayLabel.text = [NSString stringWithFormat:@"考试已结束"];
        if (@available(iOS 11.0, *)) {
            cell.leftDayLabel.textColor =[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#2A4E84" alpha:0.56] darkColor:[UIColor colorWithHexString:@"#858585" alpha:1]];
        } else {
            cell.leftDayLabel.textColor = [UIColor colorWithHexString:@"#2A4E84"];
        }
        
        [cell.leftDayLabel setAlpha:0.56];
    }
    [self.view addSubview:cell];
    return cell;
}

- (NSInteger)tableView:(nonnull UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return self.examArrangeModel.examArrangeData.data.count;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    return 166;
}

/// 获得一个字符串例如:2019-12-28，返回一个两个元素的数组@[2019, 12, 28]，元素为年，月，日
/// @param string 字符串
- (NSArray *)getArrayWithString:(NSString*)string {
    NSMutableArray<NSString*> *array = [NSMutableArray arrayWithArray:[string componentsSeparatedByString:@"-"]];
    if([array[1] hasPrefix:@"0"]) {
        NSString *month = [array[1] substringFromIndex:1];
        array[1] = month;
    }
    if([array[2] hasPrefix:@"0"]) {
        NSString *day = [array[2] substringFromIndex:1];
        array[2] = day;
    }
    return array;
    
}
//MARK: - 阿拉伯数字转汉字
- (NSString *)translationArabicNum:(NSInteger)arabicNum {
    NSString *arabicNumStr = [NSString stringWithFormat:@"%ld",(long)arabicNum];
    NSArray *arabicNumeralsArray = @[@"1",@"2",@"3",@"4",@"5",@"6",@"7",@"8",@"9",@"0"];
    NSArray *chineseNumeralsArray = @[@"一",@"二",@"三",@"四",@"五",@"六",@"七",@"八",@"九",@"零"];
    NSArray *digits = @[@"个",@"十",@"百",@"千",@"万",@"十",@"百",@"千",@"亿",@"十",@"百",@"千",@"兆"];
    NSDictionary *dictionary = [NSDictionary dictionaryWithObjects:chineseNumeralsArray forKeys:arabicNumeralsArray];
    
    if (arabicNum < 20 && arabicNum > 9) {
        if (arabicNum == 10) {
            return @"十";
        }else{
            NSString *subStr1 = [arabicNumStr substringWithRange:NSMakeRange(1, 1)];
            NSString *a1 = [dictionary objectForKey:subStr1];
            NSString *chinese1 = [NSString stringWithFormat:@"十%@",a1];
            return chinese1;
        }
    }else{
        NSMutableArray *sums = [NSMutableArray array];
        for (int i = 0; i < arabicNumStr.length; i ++)
        {
            NSString *substr = [arabicNumStr substringWithRange:NSMakeRange(i, 1)];
            NSString *a = [dictionary objectForKey:substr];
            NSString *b = digits[arabicNumStr.length -i-1];
            NSString *sum = [a stringByAppendingString:b];
            if ([a isEqualToString:chineseNumeralsArray[9]])
            {
                if([b isEqualToString:digits[4]] || [b isEqualToString:digits[8]])
                {
                    sum = b;
                    if ([[sums lastObject] isEqualToString:chineseNumeralsArray[9]])
                    {
                        [sums removeLastObject];
                    }
                }else
                {
                    sum = chineseNumeralsArray[9];
                }
                
                if ([[sums lastObject] isEqualToString:sum])
                {
                    continue;
                }
            }
            
            [sums addObject:sum];
        }
        NSString *sumStr = [sums  componentsJoinedByString:@""];
        NSString *chinese = [sumStr substringToIndex:sumStr.length-1];
        return chinese;
    }
}
//MARK: - 获取今天日期
- (NSString *)getCurrentTime {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    [formatter setDateFormat:@"yyyy-MM-dd"];
    NSString *dateTime = [formatter stringFromDate:[NSDate date]];
    NSLog(@"%@",dateTime);
    return dateTime;
}
//MARK: - 将字符串转成NSDate类型
- (NSDate *)dateFromString:(NSString *)dateString {
    
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat: @"yyyy-MM-dd"];
    NSDate *destDate= [dateFormatter dateFromString:dateString];
    return destDate;
}

- (NSString *)pleaseInsertStarTimeo:(NSString *)time1 andInsertEndTime:(NSString *)time2{
// 1.将时间转换为date
NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
formatter.dateFormat = @"yyyy-MM-dd";
NSDate *date1 = [formatter dateFromString:time1];
NSDate *date2 = [formatter dateFromString:time2];
// 2.创建日历
NSCalendar *calendar = [NSCalendar currentCalendar];
NSCalendarUnit type = NSCalendarUnitYear | NSCalendarUnitMonth | NSCalendarUnitDay | NSCalendarUnitHour | NSCalendarUnitMinute | NSCalendarUnitSecond;
// 3.利用日历对象比较两个时间的差值
NSDateComponents *cmps = [calendar components:type fromDate:date1 toDate:date2 options:0];
// 4.输出结果
    return [NSString stringWithFormat:@"%ld", cmps.day];
}

- (NSString*)calcDaysFromBegin:(NSDate *)beginDate end:(NSDate *)endDate {

//创建日期格式化对象

NSDateFormatter *dateFormatter=[[NSDateFormatter alloc] init];

[dateFormatter setDateFormat:@"yyyy-MM-dd HH:mm"];



//取两个日期对象的时间间隔：

//这里的NSTimeInterval 并不是对象，是基本型，其实是double类型，是由c定义的:typedef double NSTimeInterval;
NSTimeInterval time=[endDate timeIntervalSinceDate:beginDate];
int days=((int)time)/(3600*24);
return [NSString stringWithFormat:@"%d",days];

}

@end

