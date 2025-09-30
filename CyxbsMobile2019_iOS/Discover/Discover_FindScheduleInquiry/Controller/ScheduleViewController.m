//
//  ScheduleViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by Stove on 2020/7/14.
//  Copyright © 2020 Redrock. All rights reserved.
//对于这个控制器的说明：查询老师课表、查询学生课表是分别由两个控制器管理的，而这两个控制器的类型都是本控制器

#import "ScheduleViewController.h"
#import "HistoryView.h"
#import "ClassmatesList.h"
#import "RemindHud.h"
/**最大的搜索历史记录个数*/
#define MAXLEN 9

@interface ScheduleViewController ()<UITextFieldDelegate,HistoryViewDelegate>
/**搜索栏里的UITextField*/
@property (nonatomic, weak)UITextField *textField;

/**容纳搜索输入框和搜索按钮的View*/
@property (nonatomic, weak)UIView *searchBackView;

/**显示"历史记录"四个字label*/

/**各条历史记录的按钮的父控件*/
@property (nonatomic, weak)HistoryView *historyView;

/**参数key是用来当作从缓存取搜索记录数组时需要的UserDefaultKey*/
@property (nonatomic, copy)NSString *UserDefaultKey;

/**清除历史记录的按钮*/

/**被查的人的身份*/
@property (nonatomic,assign)PeopleType peopleType;
@end

@implementation ScheduleViewController
- (void)viewDidLoad {
    [super viewDidLoad];
    if (@available(iOS 11.0, *)) {
        self.view.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F8F9FC" alpha:1] darkColor:[UIColor colorWithHexString:@"#000101" alpha:1]];
    } else {
        self.view.backgroundColor = [UIColor colorWithHexString:@"#F8F9FC"];
    }
    
    //添加搜索框
    [self addSearchField];
    
    //添加历史记录按钮
    [self addHistoryView];
}
//参数key是用来当作从缓存取搜索记录数组时需要的UserDefaultKey，PeopleType是被查的人的身份
- (instancetype)initWithUserDefaultKey:(NSString*)key andPeopleType:(PeopleType)peopleType{
    self = [super init];
    if(self){
        self.UserDefaultKey = key;
        self.peopleType = peopleType;
    }
    return self;
}

//MARK: - 初始化子控件的方法：
//添加搜索框
- (void)addSearchField {
    //add background cornerRadius view
    UIView *backView = [[UIView alloc]init];
    self.searchBackView = backView;
    [self.view addSubview:backView];
    if (@available(iOS 11.0, *)) {
        backView.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#E8F1FC" alpha:0.77] darkColor:[UIColor colorWithHexString:@"#2C2C2C" alpha:0.72]];
    } else {
        backView.backgroundColor = [UIColor colorWithHexString:@"#E8F1FC"];
    }
    backView.layer.cornerRadius = 23;
    [backView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.view).offset(18);
        make.right.equalTo(self.view).offset(-18);
        make.top.equalTo(self.view).offset(30);
        make.height.equalTo(@44);
    }];
    
    //add search field
    UITextField *textField = [[UITextField alloc]init];
    self.textField = textField;
       [self.view addSubview:textField];
       [textField mas_makeConstraints:^(MASConstraintMaker *make) {
           make.left.equalTo(backView).offset(17);
           make.right.equalTo(backView).offset(-37);
           make.top.equalTo(backView).offset(12);
           make.bottom.equalTo(backView).offset(-11);
       }];
       [textField setFont:[UIFont fontWithName:PingFangSCRegular size:15]];
       textField.placeholder = @"输入内容";
    
    //设置return类型为search，这样键盘上就会有一个搜索按钮
    [textField setReturnKeyType:(UIReturnKeySearch)];
    
    textField.delegate = self;
    
    if (@available(iOS 11.0, *)) {
       textField.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
       textField.tintColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
       textField.textColor = [UIColor colorWithRed:21/255.0 green:49/255.0 blue:91/255.0 alpha:1];
       textField.tintColor = [UIColor colorWithRed:21/255.0 green:49/255.0 blue:91/255.0 alpha:1];
    }
    textField.backgroundColor = UIColor.clearColor;
    [self addKeyBoardToolBarforTextField:textField];
}
/// 给textfield加一个自定义toolBar
- (void)addKeyBoardToolBarforTextField:(UITextField*)textField{
    UIToolbar *toolBar = [[UIToolbar alloc] initWithFrame:CGRectMake(0, 0, MAIN_SCREEN_W, 44)];
    
    UIButton *btn = [[UIButton alloc] initWithFrame:CGRectMake(MAIN_SCREEN_W-60, 0, 50, 44)];
    [toolBar addSubview:btn];
    [btn setTitleColor:UIColor.systemBlueColor forState:UIControlStateNormal];
    btn.titleLabel.font = [UIFont boldSystemFontOfSize:16];
    [btn setTitle:@"完成" forState:UIControlStateNormal];
    
    [btn addTarget:self action:@selector(doneClicked) forControlEvents:UIControlEventTouchUpInside];

    
    UILabel *placeHolderLabel = [[UILabel alloc] init];
    [toolBar addSubview:placeHolderLabel];
    placeHolderLabel.text = textField.placeholder;
    placeHolderLabel.font = [UIFont systemFontOfSize:13];
    placeHolderLabel.alpha = 0.8;
    placeHolderLabel.textColor = [UIColor systemGrayColor];
    [placeHolderLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerX.equalTo(toolBar);
        make.top.equalTo(toolBar);
        make.bottom.equalTo(toolBar);
    }];
    
    textField.inputAccessoryView = toolBar;
}

//点击键盘右上角的完成按钮后调用
- (void)doneClicked{
    [self.view endEditing:YES];
    [self touchSearchButton];
}

//添加历史记录按钮
- (void)addHistoryView {
    HistoryView *view = [[HistoryView alloc]initWithUserDefaultKey:self.UserDefaultKey];
    
    if(self.historyView!=nil){
        [self.historyView removeFromSuperview];
    }
    
    self.historyView = view;
    [self.view addSubview:view];
    view.btnClickedDelegate = self;
    
    
    [view mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self.self.searchBackView.mas_bottom).offset(9);
        make.left.equalTo(self.searchBackView);
        make.right.equalTo(self.searchBackView);
        make.height.mas_equalTo(400);
    }];
}

//MARK: - 点击按钮后调用
//点击键盘上的搜索按钮后调用
- (void)touchSearchButton {
    //判断输入内容是否为空
    if ([self.textField.text isEqualToString:@""]) {
        MBProgressHUD *noInput = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
        noInput.mode = MBProgressHUDModeText;
        noInput.labelText = @"输入为空";
        [noInput hide:YES afterDelay:1];
        return;
    }
    //搜索输入框的内容，如果有返回则跳转如选择同学页面
    [self requestStudentNameDataWithNSString:self.textField.text];
}

//点击了某一条历史记录后调用,historyView的代理方法
- (void)touchHistoryButton:(UIButton *)sender {
    [self requestStudentNameDataWithNSString:sender.titleLabel.text];
}


//MARK: -需要实现的代理方法：
//搜索框textfield的代理方法
- (BOOL)textFieldShouldReturn:(UITextField *)textField{
    [self touchSearchButton];
    return YES;
}

//MARK: - 其他的方法
//通过string来发送网络请求的方法
- (void)requestStudentNameDataWithNSString:(NSString*)string {
    [self.view endEditing:YES];
    
    
    MBProgressHUD *loading = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
       loading.mode = MBProgressHUDModeIndeterminate;
       loading.labelText = @"加载中";
       
    ClassmatesList *classmates = [[ClassmatesList alloc] initWithPeopleType:self.peopleType];
    
    
       [classmates getPeopleListWithName:string success:^(ClassmatesList * _Nonnull classmatesList) {
           [loading hide:YES];
           if (classmatesList.classmatesArray.count == 0) {
               MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
               hud.mode = MBProgressHUDModeText;
               hud.labelText = @"无结果";
               [hud hide:YES afterDelay:1];
               //没有发生跳转，那就只改变显示历史记录的控件的内部数据，而不刷新历史记录控件布局
               [self.historyView addHistoryBtnWithString:string reLayout:NO];
               return;
           }
           
           //有多个结果就跳转到搜索结果页面
           
           ChooseStudentListViewController *studentListVC = [[ChooseStudentListViewController alloc]initWithClassmatesList:classmatesList];
           studentListVC.peopleType = self.peopleType;
           [self.delegate pushToController: studentListVC];
           //跳转后刷新历史记录表
           [self.historyView addHistoryBtnWithString:string reLayout:YES];
           
       } failure:^(NSURLSessionDataTask * _Nonnull task, NSError * _Nonnull error) {
           [loading hide:YES];
           if ([error.userInfo[NSLocalizedDescriptionKey] isEqual: @"Request failed: client error (429)"]) {
               [RemindHUD.shared showDefaultHUDWithText:@"请求过于频繁~，请稍后再试" completion:nil];
           } else {
               [RemindHUD.shared showDefaultHUDWithText:@"加载失败" completion:nil];
           }
           //没有发生跳转，那就只改变显示历史记录的控件的内部数据，而不刷新历史记录控件布局
           [self.historyView addHistoryBtnWithString:string reLayout:NO];
       }];
}

@end
