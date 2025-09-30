//
//  PickerDormitoryModel.m
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2020/5/27.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "PickerDormitoryModel.h"

@implementation PickerDormitoryModel

- (instancetype)init {
    self = [super init];
    if (self) {
        self.placeArray = @[@"宁静苑", @"明理苑", @"知行苑", @"兴业苑", @"四海苑"];
        self.siHaiPlace = @[@"1舍", @"2舍"];
        self.ningJingPlace = @[@"1舍", @"2舍", @"3舍", @"4舍", @"5舍", @"6舍", @"7舍", @"8舍", @"9舍", @"10舍"];
        self.mingLiPlace = @[@"1舍", @"2舍", @"3舍", @"4舍", @"5舍", @"6舍", @"7舍", @"8舍", @"9舍"];
        self.zhiXingPlace = @[@"1舍", @"2舍", @"3舍", @"4舍", @"5舍", @"6舍", @"7舍", @"8舍", @"9舍"];
        self.xingYePlace = @[@"1舍", @"2舍", @"3舍", @"4舍", @"5舍", @"6舍", @"7舍", @"8舍"];
        self.allArray = @[self.ningJingPlace, self.mingLiPlace, self.zhiXingPlace, self.xingYePlace, self.siHaiPlace];
    }

    return self;
}

- (NSString *)getNumberOfDormitoryWith:(NSString *)building andPlace:(NSString *)place {
    int num = [place substringToIndex:2].intValue;

    if (num != 10) {
        num = [place substringToIndex:1].intValue;
    }

    if ([building isEqual:@"宁静苑"]) {
        if (num >= 1 && num <= 5) {
            num += 7;
        } else if (num == 10) {
            num = 40;
        } else if (num >= 6) {
            num += 26;
        } else {
        }
    } else if ([building isEqual:@"明理苑"]) {
        if (num >= 1 && num <= 8) {
            num += 23;
        } else {
            num += 30;
        }
    } else if ([building isEqual:@"知行苑"]) {
        if (num >= 1 && num <= 6) {
        } else {
            num += 8;
        }
    } else if ([building isEqual:@"兴业苑"]) {
        if (num >= 1 && num <= 6) {
            num += 16;
        } else if (num == 7) {
            return @"23A栋";
        } else if (num == 8) {
            return @"23B栋";
        }
    } else {//四海苑
        num += 35;
    }

    return [NSString stringWithFormat:@"%2d栋", num];
}

- (NSArray *)getBuildingNameIndexAndBuildingNumberIndexByNumberOfDormitory:(NSString *)dormitoryNumber {
    if ([dormitoryNumber isEqual:@"23A"]) {
        return @[@3, @6];
    } else if ([dormitoryNumber isEqual:@"23B"]) {
        return @[@3, @7];
    }

    int num = dormitoryNumber.intValue;

    if (num == 40) {//宁静苑10舍
        return @[@0, @10];
    } else if (num >= 1 && num <= 6) {//知行苑1-6舍
        return @[@2, @(num - 1)];
    } else if (num >= 8 && num <= 12) {//宁静苑1-5舍
        return @[@0, @(num - 8)];
    } else if (num >= 15 && num <= 16) {//知行苑7，8舍
        return @[@2, @(num - 9)];
    } else if (num >= 17 && num <= 22) {//兴业苑1-6舍
        return @[@3, @(num - 17)];
    } else if (num >= 24 && num <= 31) {//明理苑1-8舍
        return @[@1, @(num - 24)];
    } else if (num >= 32 && num <= 35) {//宁静苑6-9舍
        return @[@0, @(num - 27)];
    } else if (num >= 36 && num <= 37) {//四海1-2舍
        return @[@4, @(num - 36)];
    } else if (num == 39) {//明理苑9舍
        return @[@1, @8];
    }

    return @[@0, @0];
}

@end
