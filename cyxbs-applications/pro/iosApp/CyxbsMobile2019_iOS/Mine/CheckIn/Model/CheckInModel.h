//
//  CheckInModel.h
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/1/11.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CheckInModel : NSObject

+ (void)CheckInSucceeded:(void (^)(void)) succeded Failed:(void (^)(NSError *err)) failed;

+ (void)requestCheckInInfoSucceeded:(void (^ _Nullable)(void))succeeded Failed:(void (^ _Nullable)(NSError * _Nullable))failed;

- (NSInteger)CheckInStampsCalculate;

@end

NS_ASSUME_NONNULL_END
