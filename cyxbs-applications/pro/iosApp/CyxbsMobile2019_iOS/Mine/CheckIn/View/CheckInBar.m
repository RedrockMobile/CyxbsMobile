//
//  CheckInBar.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/1/10.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "CheckInBar.h"
#import "CheckedInDot.h"
#import "DidNotCheckInDot.h"
#import "TodaysDot.h"

#define BARWIDTH (MAIN_SCREEN_W * 0.14)
#define BARX (MAIN_SCREEN_W * 0.04267)

@interface CheckInBar ()

@property (nonatomic, copy) NSArray *weekInfo;
@property (nonatomic, assign) int isCheckedInToday;

@end

@implementation CheckInBar

#pragma mark - Getter
- (NSArray *)weekInfo {
    NSMutableArray *tmp = [NSMutableArray arrayWithCapacity:7];
    for (int i = 0; i < [UserItemTool defaultItem].week_info.length; i++) {
        if ([[[UserItemTool defaultItem].week_info substringWithRange:NSMakeRange(i, 1)] isEqualToString:@"1"]) {
            [tmp addObject:@(7 - i)];
        }
    }
    return tmp;
}

- (int)isCheckedInToday {
    if ([UserItemTool defaultItem].rank.intValue == 0) {
        return 0;
    } else {
        return 1;
    }
}


- (instancetype)init {
    self = [super init];
    if (self) {
        [self drawBars];
        [self drawDots];
    }
    return self;
}

- (void)drawBars {
    // 添加圆点之间的杠杠
    // i表示“星期i”的前面一根杠，星期一前面没有杠
    int flag = 0;
    for (int i = 2; i <= 7; i++) {
        for (NSNumber *j in self.weekInfo) {
            if (i == j.intValue) {
                flag = 1;
                break;
            }
        }
        
        if (flag == 1) {
            UIView *bar = [[UIView alloc] init];
            if (@available(iOS 11.0, *)) {
                bar.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#3934D1" alpha:1] darkColor:[UIColor colorWithHexString:@"#2BDEFF" alpha:1]];
            } else {
                bar.backgroundColor = [UIColor colorWithRed:58/255.0 green:53/255.0 blue:210/255.0 alpha:1];
            }
            bar.frame = CGRectMake(16 + (i - 2) * BARWIDTH, 13.5, BARWIDTH, 5);
            [self addSubview:bar];
            [self.barArray addObject:bar];
        } else {
            UIView *bar = [[UIView alloc] init];
            if (@available(iOS 11.0, *)) {
                bar.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#E1E6EF" alpha:1] darkColor:[UIColor colorWithHexString:@"#5A5A5A" alpha:1]];
            } else {
                bar.backgroundColor = [UIColor colorWithRed:225/255.0 green:230/255.0 blue:240/255.0 alpha:1];
            }
            bar.frame = CGRectMake(16 + (i - 2) * BARWIDTH, 13.5, BARWIDTH, 5);
            [self addSubview:bar];
            [self.barArray addObject:bar];
        }
        flag = 0;
    }
}

- (void)drawDots {
    // 添加小圆点
    int flag = 0;
    NSInteger today;
    if ([NSDate date].weekday == 1) {
        today = 7;
    } else {
        today = [NSDate date].weekday - 1;
    }

    for (int i = 1; i <= 7; i++) {
        
        if (i == today) {
            TodaysDot *dot = [[TodaysDot alloc] init];
            dot.center = CGPointMake(6 + (i - 1) * BARWIDTH, 6);
             [self addSubview:dot];
            [self.dotArray addObject:dot];
            continue;
        }
        
        for (NSNumber *j in self.weekInfo) {
            if (i == j.intValue) {
                flag = 1;
                break;
            }
        }
        
        if (flag == 1) {
            CheckedInDot *dot = [[CheckedInDot alloc] init];
            dot.center = CGPointMake(8 + (i - 1) * BARWIDTH, 8);
            [self addSubview:dot];
            [self.dotArray addObject:dot];
        } else {
            DidNotCheckInDot *dot = [[DidNotCheckInDot alloc] init];
            dot.center = CGPointMake(8 + (i - 1) * BARWIDTH, 8);
            [self addSubview:dot];
            [self.dotArray addObject:dot];
        }
        flag = 0;
    }
    
    // 添加显示星期几的label
    // 第0个空字符串是为了对齐下标和星期几，占位用的
    NSArray *weekdays = @[@"", @"周一", @"周二", @"周三", @"周四", @"周五", @"周六", @"周日"];
    for (int i = 1; i <= 7; i++) {
        UILabel *weekdayLabel = [[UILabel alloc] initWithFrame:CGRectMake(5 + (i - 1) * BARWIDTH, 32, 23, 16)];
        weekdayLabel.text = weekdays[i];
        weekdayLabel.font = [UIFont systemFontOfSize:11];
        if (@available(iOS 11.0, *)) {
            weekdayLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#14305B" alpha:1] darkColor:[UIColor colorWithHexString:@"#EFEFEF" alpha:1]];
        } else {
            weekdayLabel.textColor = [UIColor colorWithRed:21/255.0 green:49/255.0 blue:91/255.0 alpha:0.35];
        }
        [self addSubview:weekdayLabel];
    }
    
    UIImageView *integralImageView = [[UIImageView alloc] init];
    integralImageView.frame = CGRectMake(-11 + (today - 1) * BARWIDTH, -39, 53, 29);
    if (IS_IPHONESE) {
        integralImageView.frame = CGRectMake(-8 + (today - 1) * BARWIDTH, -39, 47, 26);
    }
    integralImageView.image = [UIImage imageNamed:@"签到气泡"];
    [self addSubview:integralImageView];
    
    // 计算签到可获得邮票
    NSInteger checkInDays = [UserItemTool defaultItem].checkInDay.integerValue;
    NSInteger integral = 0;
    // 签到天数大于星期数
    if (checkInDays > today) {
        integral = 10 + today * 5 - 5;
    } else {
        integral = 10 + checkInDays * 5 - 5;
    }
    if (integral > 30) {
        integral = 30;
    }
    
    UILabel *integralLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, 38, 16)];
    integralLabel.text = [NSString stringWithFormat:@"%ld邮票", integral];
    integralLabel.textAlignment = NSTextAlignmentCenter;
    if (@available(iOS 11.0, *)) {
        integralLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#4841E2" alpha:1] darkColor:[UIColor colorWithHexString:@"#1C1C1C" alpha:1]];
    } else {
        integralLabel.textColor = [UIColor colorWithRed:72/255.0 green:65/255.0 blue:226/255.0 alpha:0.73];
    }
    integralLabel.font = [UIFont systemFontOfSize:11];
    [integralImageView addSubview:integralLabel];
    integralLabel.center = CGPointMake(integralImageView.width / 2.0, integralImageView.height / 2.0 - 2);
}

@end
