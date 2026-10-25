//
//  RemindHUD.m
//  CyxbsMobile2019_iOS
//
//  Created by 潘申冰 on 2023/11/1.
//  Copyright © 2023 Redrock. All rights reserved.
//

#import "RemindHUD.h"
#import <MBProgressHUD/MBProgressHUD.h>

@interface RemindHUD ()

@property (nonatomic, strong) MBProgressHUD *hud;

@end

@implementation RemindHUD

+ (instancetype)shared {
    static RemindHUD *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}

///HUD 宽高自定义
- (void)addProgressHUDViewWithWidth:(CGFloat)width
                             height:(CGFloat)height
                               text:(NSString *)text
                               font:(UIFont *)font
                          textColor:(UIColor *)textColor
                              delay:(CGFloat)delay
                    backGroundColor:(UIColor *)backGroundColor
                       cornerRadius:(CGFloat)cornerRadius
                           yOffset:(float)yOffset
                         completion:(void (^)(void))completion {
    UIView *customView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, width, height)];
    customView.layer.backgroundColor = backGroundColor.CGColor;
    customView.layer.cornerRadius = cornerRadius;
    
    UILabel *label = [[UILabel alloc] init];
    label.textAlignment = NSTextAlignmentCenter;
    label.font = font;
    label.text = text;
    label.textColor = textColor;
    [customView addSubview:label];
    [label setTranslatesAutoresizingMaskIntoConstraints:NO];
    [NSLayoutConstraint activateConstraints:@[
        [label.centerXAnchor constraintEqualToAnchor:customView.centerXAnchor],
        [label.centerYAnchor constraintEqualToAnchor:customView.centerYAnchor]
    ]];
    
    UIWindow *window = [[UIApplication sharedApplication].windows filteredArrayUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(UIWindow *window, NSDictionary *bindings) {
        return window.isKeyWindow;
    }]].firstObject;
    
    if (window) {
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:window animated:YES];
        hud.color = [UIColor clearColor];
        hud.mode = MBProgressHUDModeCustomView;
        hud.customView = customView;
        hud.yOffset = yOffset;
        hud.userInteractionEnabled = NO;
        [hud hide:YES afterDelay:delay ?: 2];
        
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay ?: 2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (completion) {
                completion();
            }
        });
    }
}

///HUD 宽高自适应
- (void)addProgressHUDViewWithText:(NSString *)text
                              font:(UIFont *)font
                         textColor:(UIColor *)textColor
                             delay:(CGFloat)delay
                   backGroundColor:(UIColor *)backGroundColor
                      cornerRadius:(CGFloat)cornerRadius
                          yOffset:(float)yOffset
                        completion:(void (^)(void))completion {
    UILabel *label = [[UILabel alloc] init];
    label.textAlignment = NSTextAlignmentCenter;
    label.font = font;
    label.text = text;
    label.textColor = textColor;
    label.numberOfLines = 0;
    label.lineBreakMode = NSLineBreakByWordWrapping;
    CGSize labelSize = [label sizeThatFits:CGSizeMake([UIScreen mainScreen].bounds.size.width - 40, CGFLOAT_MAX)];
    CGFloat width = labelSize.width + 40;
    CGFloat height = labelSize.height + 20;
    
    UIView *customView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, width, height)];
    customView.backgroundColor = backGroundColor;
    customView.layer.cornerRadius = cornerRadius;
    [customView addSubview:label];
    [label setTranslatesAutoresizingMaskIntoConstraints:NO];
    [NSLayoutConstraint activateConstraints:@[
        [label.leadingAnchor constraintEqualToAnchor:customView.leadingAnchor constant:20],
        [label.trailingAnchor constraintEqualToAnchor:customView.trailingAnchor constant:-20],
        [label.topAnchor constraintEqualToAnchor:customView.topAnchor constant:10],
        [label.bottomAnchor constraintEqualToAnchor:customView.bottomAnchor constant:-10]
    ]];
    
    UIWindow *keyWindow = [[[UIApplication sharedApplication] windows] firstObject];
    if (keyWindow) {
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:keyWindow animated:YES];
        hud.color = [UIColor clearColor];
        hud.mode = MBProgressHUDModeCustomView;
        hud.customView = customView;
        hud.yOffset = yOffset;
        hud.userInteractionEnabled = NO;
        [hud hide:YES afterDelay:delay ?: 2];
        
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay ?: 2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (completion) {
                completion();
            }
        });
    }
}

///默认的 HUD
- (void)showDefaultHUDWithText:(nonnull NSString *)text completion:(void (^)(void))completion {
    UIFont *defaultFont = [UIFont fontWithName:PingFangSCSemibold size:13];
    UIColor *defaultTextColor = [UIColor whiteColor];
    UIColor *defaultBackGroundColor = [UIColor colorWithHexString:@"#2A4E84"];
    CGFloat defaultDelay = 1.5;
    CGFloat defaultCornerRadius = 18;
    CGFloat defaultYOffset = (CGFloat)(-SCREEN_HEIGHT * 0.368 + STATUSBARHEIGHT);
    
    [self addProgressHUDViewWithText:text
                                font:defaultFont
                           textColor:defaultTextColor
                               delay:defaultDelay
                     backGroundColor:defaultBackGroundColor
                        cornerRadius:defaultCornerRadius
                             yOffset:defaultYOffset
                          completion:completion
    ];
}

///显示较长的 HUD
- (void)showDefaultHUDLongWithText:(nonnull NSString *)text completion:(void (^)(void))completion {
    UIFont *defaultFont = [UIFont fontWithName:PingFangSCSemibold size:13];
    UIColor *defaultTextColor = [UIColor whiteColor];
    UIColor *defaultBackGroundColor = [UIColor colorWithHexString:@"#2A4E84"];
    CGFloat defaultDelay = 3;
    CGFloat defaultCornerRadius = 18;
    CGFloat defaultYOffset = (CGFloat)(-SCREEN_HEIGHT * 0.368 + STATUSBARHEIGHT);
    
    [self addProgressHUDViewWithText:text
                                font:defaultFont
                           textColor:defaultTextColor
                               delay:defaultDelay
                     backGroundColor:defaultBackGroundColor
                        cornerRadius:defaultCornerRadius
                             yOffset:defaultYOffset
                          completion:completion
    ];
}

@end
