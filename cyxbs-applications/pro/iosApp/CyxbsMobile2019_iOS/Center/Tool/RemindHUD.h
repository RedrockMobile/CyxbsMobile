//
//  RemindHUD.h
//  CyxbsMobile2019_iOS
//
//  Created by 潘申冰 on 2023/11/1.
//  Copyright © 2023 Redrock. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
NS_ASSUME_NONNULL_BEGIN

@interface RemindHUD : NSObject

+ (instancetype)shared;

- (void)addProgressHUDViewWithWidth:(CGFloat)width
                             height:(CGFloat)height
                               text:(NSString *)text
                               font:(UIFont *)font
                          textColor:(UIColor *)textColor
                              delay:(CGFloat)delay
                    backGroundColor:(UIColor *)backGroundColor
                       cornerRadius:(CGFloat)cornerRadius
                           yOffset:(float)yOffset
                         completion:(void (^ __nullable)(void))completion;

- (void)addProgressHUDViewWithText:(NSString *)text
                              font:(UIFont *)font
                         textColor:(UIColor *)textColor
                             delay:(CGFloat)delay
                   backGroundColor:(UIColor *)backGroundColor
                      cornerRadius:(CGFloat)cornerRadius
                          yOffset:(float)yOffset
                        completion:(void (^ __nullable)(void))completion;

- (void)showDefaultHUDWithText:(NSString *)text completion:(void (^ __nullable)(void))completion;

- (void)showDefaultHUDLongWithText:(NSString *)text completion:(void (^ __nullable)(void))completion;

@end

NS_ASSUME_NONNULL_END
