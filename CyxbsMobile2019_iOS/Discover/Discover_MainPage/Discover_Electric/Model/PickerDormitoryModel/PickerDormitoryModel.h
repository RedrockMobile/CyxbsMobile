//
//  PickerDormitoryModel.h
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2020/5/27.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface PickerDormitoryModel : NSObject

@property (nonatomic, copy) NSArray <NSString *> *placeArray;
@property (nonatomic, copy) NSArray <NSString *> *siHaiPlace;
@property (nonatomic, copy) NSArray <NSString *> *ningJingPlace;
@property (nonatomic, copy) NSArray <NSString *> *mingLiPlace;
@property (nonatomic, copy) NSArray <NSString *> *xingYePlace;
@property (nonatomic, copy) NSArray <NSString *> *zhiXingPlace;
@property (nonatomic, copy) NSArray <NSArray <NSString *> *> *allArray;

///根据苑名字和几舍获取栋数字：例如参数1：知行苑，参数2：8舍，返回：16栋
- (NSString *)getNumberOfDormitoryWith:(NSString *)building andPlace:(NSString *)place;
/// 根据几栋获得是什么宿舍，几舍。例如参数：24栋，返回：@[1,0]//1是明理苑的索引，0是1舍的索引
- (NSArray <NSNumber *> *)getBuildingNameIndexAndBuildingNumberIndexByNumberOfDormitory:(NSString *)dormitoryNumber;

@end

NS_ASSUME_NONNULL_END
