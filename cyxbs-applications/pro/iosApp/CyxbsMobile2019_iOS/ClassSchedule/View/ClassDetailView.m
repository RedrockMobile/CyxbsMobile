//
//  ClassDetailView.m
//  CyxbsMobile2019_iOS
//
//  Created by Stove on 2020/8/16.
//  Copyright © 2020 Redrock. All rights reserved.
//显示某课详情的弹窗view

#import "ClassDetailView.h"

@interface ClassDetailView()

/// 课程名称
@property (nonatomic, strong)UILabel *lessonNameLabel;

/// 教室名
@property (nonatomic, strong)UILabel *classroomNameLabel;

/// 老师名
@property (nonatomic, strong)UILabel *teacherNameLabel;

/// 周期
@property (nonatomic, strong)UILabel *weekRangeLabel;

/// 时间
@property (nonatomic, strong)UILabel *classTimeLabel;

/// 学课程类型
@property (nonatomic, strong)UILabel *typeLabel;

/// 显示“周期”二字的label
@property (nonatomic, strong)UILabel *week;

/// 显示“时间”二字的label
@property (nonatomic, strong)UILabel *time;

/// 显示@"课程类型"的label
@property (nonatomic, strong)UILabel *type;

/// 教室地址旁边的右箭头按钮
@property (nonatomic, strong)UIButton *rightArrBtn;
@end

@implementation ClassDetailView
//MARK:-重写的方法
- (instancetype)init {
    self = [super init];
    if(self){
        [self setFrame:CGRectMake(0, 0, MAIN_SCREEN_W, DETAILVIEW_H)];
        
        //文本框初始化，完成对定死的数据的设置
        [self initLabel];
        
        // 添加教室地址旁边的右箭头按钮
        [self addRightArrBtn];
        
        self.backgroundColor = UIColor.clearColor;
    }
    return self;
}

/// 加约束
- (void)layoutSubviews {
    [super layoutSubviews];
    
    [self.lessonNameLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.05867*MAIN_SCREEN_W);
    }];
    
    [self.classroomNameLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.15733*MAIN_SCREEN_W);
    }];
    
    [self.week mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.272*MAIN_SCREEN_W);
    }];
    
    [self.time mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.392*MAIN_SCREEN_W);
    }];
    
    [self.type mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self).offset(0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.512*MAIN_SCREEN_W);
    }];
    
//___________________________________________________
    
    [self.teacherNameLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.classroomNameLabel.mas_right).offset(0.0747*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.15733*MAIN_SCREEN_W);
    }];
    
//___________________________________________________
    
    [self.weekRangeLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.right.equalTo(self).offset(-0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.272*MAIN_SCREEN_W);
    }];
    
    [self.classTimeLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.right.equalTo(self).offset(-0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.392*MAIN_SCREEN_W);
    }];
    
    [self.typeLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.right.equalTo(self).offset(-0.04*MAIN_SCREEN_W);
        make.top.equalTo(self).offset(0.512*MAIN_SCREEN_W);
    }];
    
    [self.rightArrBtn mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.classroomNameLabel.mas_right).offset(-4);
        make.top.equalTo(self).offset(0.15733*MAIN_SCREEN_W);
        make.width.height.mas_equalTo(20);
    }];
}

/// 重写了dataDict的set方法，这样给dataDict赋值就可以自动完成对文字的设置
- (void)setDataDict:(NSDictionary *)dataDict {
    _dataDict = dataDict;
    self.lessonNameLabel.text = dataDict[@"course"];
    
    //判断课程名称的长度是不是太长了，如果是，那么按照比例缩小fontSize
    if(self.lessonNameLabel.text.length>15){
        float fontSize = 16.0/self.lessonNameLabel.text.length*22;
        if(fontSize<17){
            fontSize=17;
        }
        [self.lessonNameLabel setFont:[UIFont fontWithName:PingFangSCSemibold size:fontSize]];
    }

    self.classroomNameLabel.text = dataDict[@"classroom"];
    self.teacherNameLabel.text = dataDict[@"teacher"];
    self.weekRangeLabel.text = dataDict[@"rawWeek"];
    
    NSString *daytime = [self transformDataString:dataDict[@"lesson"] withPeriod:[dataDict[@"period"]intValue]];
    self.classTimeLabel.text =
    [NSString stringWithFormat:@"%@  %@",dataDict[@"day"],daytime];
    self.typeLabel.text = dataDict[@"type"];
//    self.classroomNameLabel.text = @"计算机教室(十一) (综合实验楼C405/C406算机教室(十一) (综合实验楼C405/C406算机教室(十一) (综合实验楼C405/C406/C407)";
    if (self.classroomNameLabel.text.length>25) {
        [self.classroomNameLabel mas_updateConstraints:^(MASConstraintMaker *make) {
            make.width.mas_equalTo(MAIN_SCREEN_W*0.62);
        }];
    }
}

//MARK:-添加子控件的方法
/// 添加教室地址旁边的右箭头按钮
- (void)addRightArrBtn {
    UIButton *btn = [[UIButton alloc] init];
    self.rightArrBtn  = btn;
    [self addSubview:btn];
    
    
    [btn addTarget:self action:@selector(rightArrowBtnClicked) forControlEvents:UIControlEventTouchUpInside];
    [btn setImage:[UIImage imageNamed:@"右箭头"] forState:UIControlStateNormal];
    [btn setImageEdgeInsets:(UIEdgeInsetsMake(0, 5, 0, 6))];
}

///文本框初始化，完成对定死的数据的设置
- (void)initLabel {
    //alloc init
    self.lessonNameLabel = [[UILabel alloc] init];
    self.weekRangeLabel = [[UILabel alloc] init];
    self.classTimeLabel = [[UILabel alloc] init];
    self.typeLabel = [[UILabel alloc] init];
    self.classroomNameLabel = [[UILabel alloc] init];
    self.teacherNameLabel = [[UILabel alloc] init];
    self.week = [[UILabel alloc] init];
    self.time = [[UILabel alloc] init];
    self.type = [[UILabel alloc] init];
    
    // 手势
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(rightArrowBtnClicked)];
    [self.classroomNameLabel addGestureRecognizer:tap];
    self.classroomNameLabel.userInteractionEnabled = YES;
    
    //教室地点的行数
    self.classroomNameLabel.numberOfLines = 0;
    
    //alpha
    float alpha1 = 0.61, alpha2 = 0.81;
    self.classroomNameLabel.alpha = alpha1;
    self.teacherNameLabel.alpha = alpha1;
    self.week.alpha = alpha2;
    self.time.alpha = alpha2;
    self.type.alpha = alpha2;

    
    //上色
    UIColor *textColor;
    if (@available(iOS 11.0, *)) {
        textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
    } else {
        textColor = [UIColor colorWithRed:21/255.0 green:49/255.0 blue:91/255.0 alpha:1];
    }
    self.lessonNameLabel.textColor = textColor;
    self.weekRangeLabel.textColor = textColor;
    self.classTimeLabel.textColor = textColor;
    self.typeLabel.textColor = textColor;
    self.classroomNameLabel.textColor = textColor;
    self.teacherNameLabel.textColor = textColor;
    self.week.textColor = textColor;
    self.time.textColor = textColor;
    self.type.textColor = textColor;
    
    
    //字号
    UIFont *regu13 = [UIFont fontWithName:PingFangSCRegular size: 13];
    UIFont *regu15 = [UIFont fontWithName:PingFangSCRegular size: 15];
    UIFont *semi15 = [UIFont fontWithName:PingFangSCSemibold size:15];
    
    self.lessonNameLabel.font = [UIFont fontWithName:PingFangSCSemibold size: 22];
    self.weekRangeLabel.font = semi15;
    self.classTimeLabel.font = semi15;
    self.typeLabel.font = semi15;
    
    self.classroomNameLabel.font = regu13;
    self.teacherNameLabel.font = regu13;
    
    self.week.font = regu15;
    self.time.font = regu15;
    self.type.font = regu15;
    
    //addsubView
    [self addSubview:self.lessonNameLabel];
    [self addSubview:self.weekRangeLabel];
    [self addSubview:self.classTimeLabel];
    [self addSubview:self.typeLabel];
    [self addSubview:self.classroomNameLabel];
    [self addSubview:self.teacherNameLabel];
    [self addSubview:self.week];
    [self addSubview:self.time];
    [self addSubview:self.type];
    
    //给几个文字定死的label加字
    self.week.text = @"周期";
    self.time.text = @"时间";
    self.type.text = @"课程类型";
}

//MARK:-点击某按钮后调用的方法
/// 点击课程详情旁的右箭头按钮后调用，旧 iOS 课表已迁移到 CMP，这里先禁用地图跳转。
- (void)rightArrowBtnClicked {
    return;
}

//MARK:-其他方法
///把@"一二节"转换为@"1-2节"、如果period==3，那么@"九十节"->@"9-11节"
- (NSString*)transformDataString:(NSString*)dataString withPeriod:(int)period {
    NSString *str12,*str56,*str910;
         switch (period) {
         case 2:
             str12 = @"8:00-9:40";
             str56 = @"14:00-15:40";
             str910 = @"19:00-20:40";
             break;
         case 3:
             str12 = @"8:00-11:00";
             str56 = @"14:00-17:00";
             str910 = @"19:00-21:35";
             break;
         case 4:
             str12 = @"8:00-11:55";
             str56 = @"14:00-17:55";
             str910 = @"19:00-22:30";
             break;
         default:
             break;
     }
    NSDictionary *tranfer = @{
        @"一二节":str12,
        @"三四节":@"10:15-11:55",
        @"五六节":str56,
        @"七八节":@"16:15-17:55",
        @"九十节":str910,
        @"十一十二节":@"20:50-22:30",
    };
    return tranfer[dataString];
}
//十一十二节没有时间的用户2019210081
@end

/**
"begin_lesson" = 1;
教室                                classroom = 2217;
课程名称                        course = "高等数学A(下)";
                                    "course_num" = A1110310;
                                    day = "星期一";
周几的课                        "hash_day" = 0;
从第几节开始上                  "hash_lesson" = 0;
                                    lesson = "一二节";
这节课的时长                    period = 2;
                                    rawWeek = "3-17周";
                                    teacher = "邓志颖";
                                    type = "必修";
  那些周有课                    week =         (
                                                            3,
                                                          5,
                                                            7,
                                                            9,
                                                            11,
                                                            13,
                                                            15,
                                                            17
                                                  );
                                        weekBegin = 3;
                                        weekEnd = 17;
                                        weekModel = single;
*/
//@[@"8:00 - 9:40",@"10:15 - 11:55",@"14:00 - 15:40",@"16:15 - 17:55",@"19:00 - 20:40",@"20:50 - 22:30"];
